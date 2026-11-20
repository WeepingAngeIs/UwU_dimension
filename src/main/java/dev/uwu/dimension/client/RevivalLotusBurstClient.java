package dev.uwu.dimension.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** One-shot world effect for the server's accepted revival. All coordinates are in blocks. */
@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class RevivalLotusBurstClient {
    public static final int DURATION = dev.uwu.dimension.RevivalBloom.DURATION_TICKS;
    private static final ResourceLocation MOTION = id("effects/revival_lotus_burst/motion.json");
    private static final ResourceLocation GLOW = id("textures/effect/revival_lotus_burst_glow.png");
    private static final ResourceLocation MIXED = id("dynamic/revival_lotus_burst_mixed");
    private static final RenderType LAYER = RenderType.entityTranslucentEmissive(MIXED, false);
    private static final int GRID = 8;
    private static final float OVERLAY_WEIGHT = .48F;
    private static final Map<Key, Burst> ACTIVE = new HashMap<>();
    private static ClientLevel level;
    private static Model model;

    private record Key(UUID owner, long start) {}
    private record Burst(Vec3 feet, float scale, float cos, float sin) {}
    private record Mesh(float[][] xyz, float[][] uv, int[][] triangles) {}
    private record Model(Mesh[] parts, float[][][] frames, float step, int maxVertices) {}

    private RevivalLotusBurstClient() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension", path);
    }

    /** Call on the client main thread via the clientbound payload; yaw is shared by all viewers. */
    public static void receive(ResourceLocation dimension, UUID owner, long startGameTick,
            Vec3 feetAtRevive, float playerScale, float yawDegrees) {
        if (!selectLevel() || !level.dimension().location().equals(dimension)
                || !Float.isFinite(playerScale) || playerScale <= 0 || !Float.isFinite(yawDegrees)
                || !Double.isFinite(feetAtRevive.x) || !Double.isFinite(feetAtRevive.y)
                || !Double.isFinite(feetAtRevive.z)) return;
        long age = level.getGameTime() - startGameTick;
        if (age >= DURATION || age < -DURATION) return;
        double yaw = Math.toRadians(yawDegrees);
        ACTIVE.putIfAbsent(new Key(owner, startGameTick),
                new Burst(feetAtRevive, playerScale, (float)Math.cos(yaw), (float)Math.sin(yaw)));
    }

    private static boolean selectLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != level) {
            ACTIVE.clear();
            level = current;
        }
        return level != null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!selectLevel() || ACTIVE.isEmpty()) return;
        long now = level.getGameTime();
        ACTIVE.keySet().removeIf(key -> now - key.start >= DURATION);
    }

    @EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Resources {
        @SubscribeEvent
        public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) RevivalLotusBurstClient::reload);
        }
    }

    private static JsonObject json(ResourceManager resources, ResourceLocation location) throws IOException {
        try (var reader = resources.openAsReader(location)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static float[][] rows(JsonArray array, int width) {
        float[][] values = new float[array.size()][width];
        for (int i = 0; i < values.length; i++) {
            JsonArray row = array.get(i).getAsJsonArray();
            if (row.size() != width) throw new IllegalArgumentException("Invalid lotus row width");
            for (int j = 0; j < width; j++) {
                values[i][j] = row.get(j).getAsFloat();
                if (!Float.isFinite(values[i][j])) throw new IllegalArgumentException("Nonfinite lotus data");
            }
        }
        return values;
    }

    private static Mesh mesh(JsonObject object) {
        float[][] xyz = rows(object.getAsJsonArray("vertices"), 3);
        float[][] uv = rows(object.getAsJsonArray("uv"), 2);
        if (xyz.length == 0 || xyz.length != uv.length) throw new IllegalArgumentException("Invalid lotus mesh");
        JsonArray faces = object.getAsJsonArray("triangles");
        int[][] indices = new int[faces.size()][3];
        for (int i = 0; i < indices.length; i++) {
            JsonArray row = faces.get(i).getAsJsonArray();
            if (row.size() != 3) throw new IllegalArgumentException("Invalid lotus triangle");
            for (int j = 0; j < 3; j++) {
                int v = row.get(j).getAsInt();
                if (v < 0 || v >= xyz.length) throw new IllegalArgumentException("Invalid lotus vertex index");
                indices[i][j] = v;
            }
        }
        return new Mesh(xyz, uv, indices);
    }

    private static void reload(ResourceManager resources) {
        var textures = Minecraft.getInstance().getTextureManager();
        try {
            JsonObject motion = json(resources, MOTION);
            if (!"uwudimension:lotus_motion_v1".equals(motion.get("format").getAsString())
                    || motion.get("duration_ticks").getAsInt() != DURATION)
                throw new IllegalArgumentException("Unsupported lotus motion");
            JsonObject geometry = json(resources, ResourceLocation.parse(motion.get("mesh").getAsString()));
            if (geometry.get("format").getAsInt() != 1 || !geometry.get("double_sided").getAsBoolean()
                    || !"top_left".equals(geometry.get("uv_origin").getAsString()))
                throw new IllegalArgumentException("Unsupported lotus mesh");
            Map<String,Mesh> meshes = new HashMap<>();
            geometry.getAsJsonObject("meshes").entrySet().forEach(e ->
                    meshes.put(e.getKey(), mesh(e.getValue().getAsJsonObject())));
            JsonArray parts = motion.getAsJsonArray("parts");
            Mesh[] ordered = new Mesh[parts.size()];
            int max = 0;
            for (int i = 0; i < ordered.length; i++) {
                ordered[i] = meshes.get(parts.get(i).getAsJsonObject().get("mesh").getAsString());
                if (ordered[i] == null) throw new IllegalArgumentException("Unknown lotus part");
                max = Math.max(max, ordered[i].xyz.length);
            }
            float step = motion.get("sample_step_ticks").getAsFloat();
            JsonArray frames = motion.getAsJsonArray("frames");
            if (step <= 0 || frames.size() < 2 || (frames.size()-1) * step != DURATION)
                throw new IllegalArgumentException("Invalid lotus clock");
            float[][][] keyed = new float[frames.size()][][];
            for (int i = 0; i < keyed.length; i++) {
                JsonObject frame = frames.get(i).getAsJsonObject();
                if (frame.get("tick").getAsFloat() != i * step) throw new IllegalArgumentException("Invalid lotus frame");
                keyed[i] = rows(frame.getAsJsonArray("poses"), 14);
                if (keyed[i].length != ordered.length) throw new IllegalArgumentException("Invalid lotus part count");
            }
            var base = ResourceLocation.parse(geometry.get("texture").getAsString());
            textures.register(MIXED, palette(resources, base));
            model = new Model(ordered, keyed, step, max);
        } catch (IOException | RuntimeException error) {
            model = null;
            textures.release(MIXED);
            LogUtils.getLogger().warn("Unable to load Lotus of Life burst assets", error);
        }
    }

    // Preblend 64 nearest-filtered shades at reload. Each fragment is submitted once,
    // allowing vanilla to depth-test and order petals without a coplanar glow layer.
    private static DynamicTexture palette(ResourceManager resources, ResourceLocation base) throws IOException {
        try (var baseStream = resources.open(base); var glowStream = resources.open(GLOW);
                var a = NativeImage.read(baseStream); var b = NativeImage.read(glowStream)) {
            int w = a.getWidth(), h = a.getHeight();
            if (w != b.getWidth() || h != b.getHeight()) throw new IOException("Lotus atlas sizes differ");
            NativeImage image = new NativeImage(w * GRID, h * GRID, false);
            try {
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int c = a.getPixelRGBA(x,y), g = b.getPixelRGBA(x,y);
                    int alpha = c >>> 24;
                    if ((g >>> 24) != alpha || (alpha != 0 && alpha != 255))
                        throw new IOException("Expected matching binary lotus texture masks");
                    for (int k = 0; k < GRID * GRID; k++) {
                        float weight = OVERLAY_WEIGHT * k / (GRID * GRID - 1);
                        int color = alpha << 24;
                        for (int shift = 0; shift < 24; shift += 8) {
                            int from = (c >>> shift) & 255, to = (g >>> shift) & 255;
                            color |= Math.round(from + (to - from) * weight) << shift;
                        }
                        image.setPixelRGBA((k % GRID) * w + x, (k / GRID) * h + y, color);
                    }
                }
                return new DynamicTexture(image);
            } catch (IOException | RuntimeException failure) {
                image.close();
                throw failure;
            }
        }
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !selectLevel() || ACTIVE.isEmpty() || model == null) return;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = level.getGameTime();
        VertexConsumer vertices = null;
        float[] mix = new float[14], xyz = new float[3 * model.maxVertices];
        for (var entry : ACTIVE.entrySet()) {
            float age = (now - entry.getKey().start) + partial;
            if (age < 0 || age >= DURATION) continue;
            Burst burst = entry.getValue();
            // The layer sorts all transparent quads from all accepted revives together on upload.
            if (vertices == null) vertices = buffers.getBuffer(LAYER);
            float sample = age / model.step;
            int i = Math.min((int) sample, model.frames.length - 2);
            for (int p = 0; p < model.parts.length; p++) {
                float[] a = model.frames[i][p], b = model.frames[i + 1][p];
                for (int n = 0; n < mix.length; n++) mix[n] = a[n] + (sample - i) * (b[n] - a[n]);
                if (mix[12] > 0) part(vertices, event.getPoseStack().last(), burst, camera, model.parts[p], mix, xyz);
            }
        }
        if (vertices != null) buffers.endBatch(LAYER);
    }

    private static void part(VertexConsumer out, PoseStack.Pose pose, Burst burst, Vec3 camera,
            Mesh mesh, float[] m, float[] xyz) {
        double dx = burst.feet.x - camera.x, dy = burst.feet.y - camera.y, dz = burst.feet.z - camera.z;
        for (int i = 0; i < mesh.xyz.length; i++) {
            float[] v = mesh.xyz[i];
            float x = m[0]*v[0] + m[1]*v[1] + m[2]*v[2] + m[3];
            float y = m[4]*v[0] + m[5]*v[1] + m[6]*v[2] + m[7];
            float z = m[8]*v[0] + m[9]*v[1] + m[10]*v[2] + m[11];
            xyz[3*i] = (float)(dx + burst.scale * (burst.cos * x + burst.sin * z));
            xyz[3*i+1] = (float)(dy + burst.scale * y);
            xyz[3*i+2] = (float)(dz + burst.scale * (burst.cos * z - burst.sin * x));
        }
        // Source-over with base coverage a and overlay coverage a*w gives opacity
        // a*[1+w*(1-a)] and glow's share of the source color w/[1+w*(1-a)].
        float a = Math.clamp(m[12], 0F, 1F), e = Math.clamp(m[13], 0F, 1F);
        float factor = 1 + OVERLAY_WEIGHT * e * (1 - a);
        int alpha = Math.round(255 * a * factor);
        if (alpha == 0) return;
        int tile = Math.round((GRID * GRID - 1) * e / factor);
        for (int[] tri : mesh.triangles) {
            int ia = tri[0]*3, ib = tri[1]*3, ic = tri[2]*3;
            float ax=xyz[ib]-xyz[ia], ay=xyz[ib+1]-xyz[ia+1], az=xyz[ib+2]-xyz[ia+2];
            float bx=xyz[ic]-xyz[ia], by=xyz[ic+1]-xyz[ia+1], bz=xyz[ic+2]-xyz[ia+2];
            float nx=ay*bz-az*by, ny=az*bx-ax*bz, nz=ax*by-ay*bx;
            float length = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
            if (length == 0) continue;
            // Repeating the third vertex encodes one triangle in vanilla's sorted quad format.
            for (int n = 0; n < 4; n++) {
                int v = tri[Math.min(n, 2)], off = 3 * v;
                out.addVertex(pose, xyz[off], xyz[off+1], xyz[off+2]).setColor(255,255,255,alpha)
                        .setUv(((tile % GRID) + mesh.uv[v][0]) / GRID, ((tile / GRID) + mesh.uv[v][1]) / GRID)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(pose, nx/length, ny/length, nz/length);
            }
        }
    }
}
