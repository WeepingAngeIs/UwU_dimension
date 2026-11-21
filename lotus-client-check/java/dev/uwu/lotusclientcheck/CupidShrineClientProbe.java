package dev.uwu.lotusclientcheck;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.uwu.dimension.CupidShrine;
import dev.uwu.dimension.ReefLife;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

/** Records real client vertex submissions on a disposable Sakura Reach platform. */
public final class CupidShrineClientProbe {
    private static final BlockPos CENTER = new BlockPos(8, 112, 8);
    private static final Vec3 FEET = Vec3.atBottomCenterOf(CENTER).add(0, 0, 2.5);
    private static final Map<Direction, BlockPos> MARKERS = Map.of(
            Direction.SOUTH, CENTER, Direction.NORTH, CENTER.north(3),
            Direction.EAST, CENTER.east(3), Direction.WEST, CENTER.west(3));
    private static final String SCULPTURE = "block/seated_cupid_twin_tails";
    private static final Map<String, String> APPROVED_RESOURCES = Map.of(
            "models/" + SCULPTURE + ".json", "2e45aaf08ee2b69d5719c762c1cdf7dd317088147a1c432a38e7543a90370b6c",
            "models/" + SCULPTURE + ".mtl", "54a04854dfd47aa0c9e12cc216128cc44f5d7ab5ea6b21009600664a293146c5",
            "models/" + SCULPTURE + ".obj", "422a1e530ad304377137a5adc1cfcd3ef7c8e797e3b87cc20dcd9d665ffd8d13",
            "textures/" + SCULPTURE + "/pedestal.png", "35dd30650ebfde045e7bb61709b2d6179c94d380a578e40f7445bbcb103f4246",
            "textures/" + SCULPTURE + "/cupid.png", "27ea1198f91d4c99a9c2f7db2ec0ba437e2d5d5a7ea5a24ae80b43c15f7184ee",
            "textures/" + SCULPTURE + "/eyes.png", "e074baee8f91f4125477234484d1c69181f79f2ceb1ad080db456bf814bb7540",
            "textures/" + SCULPTURE + "/head_wings.png", "b51df32173808426fa70e6c6a63423b0236c454b261c2faee6d4cd5ce5e8646a",
            "textures/effect/revival_blossom.png", "c904813210fe5f5e8e4d612d219770eafddd080561dfb4873ea33f58c49989b3");
    private static final Map<String, Integer> MATERIALS =
            Map.of("pedestal", 744, "cupid", 3274, "eyes", 16, "head_wings", 72);
    private static final int QUADS = MATERIALS.values().stream().mapToInt(Integer::intValue).sum();
    private static final Vec3 HAND = new Vec3(.060, 1.133, .372);
    private static final RenderType STONE = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
    private static final ResourceLocation LOTUS_SHEET = id("textures/effect/revival_blossom.png");
    private static final RenderType FLOWER = RenderType.entityTranslucent(LOTUS_SHEET);
    private static final float EPSILON = .001F;

    // The render observer and this probe run on the same client thread.
    private static CupidShrineClientProbe observingCircle;
    private final List<Vec3> circle = new ArrayList<>(160);
    private final long deadline = System.nanoTime() + 75_000_000_000L;
    private volatile Throwable preparationFailure;
    private boolean verifiedGeometry;
    private boolean verifiedCircle;

    CupidShrineClientProbe(Minecraft client) {
        var server = client.getSingleplayerServer();
        var playerId = client.player.getUUID();
        server.execute(() -> {
            try {
                var owner = server.getPlayerList().getPlayer(playerId);
                var level = server.getLevel(ReefLife.REACH);
                require(owner != null && level != null, "Missing fixture player or Sakura Reach");
                for (BlockPos floor : BlockPos.betweenClosed(CENTER.offset(-5, -1, -5), CENTER.offset(5, -1, 5))) {
                    level.setBlockAndUpdate(floor, Blocks.POLISHED_DIORITE.defaultBlockState());
                    for (int y = 1; y <= 4; y++) {
                        BlockPos clear = floor.above(y);
                        if (!level.getBlockState(clear).isAir()) {
                            level.setBlockAndUpdate(clear, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                for (var marker : MARKERS.entrySet()) {
                    require(level.setBlockAndUpdate(marker.getValue(), CupidShrine.state(marker.getKey())),
                            "Could not place " + marker.getKey() + " fixture marker");
                }
                require(owner.teleportTo(level, FEET.x, FEET.y, FEET.z, Set.of(), 180, 8),
                        "Could not travel to the fixture platform");
            } catch (Throwable failure) {
                preparationFailure = failure;
            }
        });
    }

    boolean tick(Minecraft client) throws ReflectiveOperationException, IOException, NoSuchAlgorithmException {
        if (preparationFailure != null) throw new AssertionError("Shrine platform preparation failed", preparationFailure);
        require(System.nanoTime() < deadline, "Timed out waiting for shrine renders and ring submissions");
        if (client.level == null || client.player == null || client.screen != null
                || !client.level.dimension().equals(ReefLife.REACH)
                || client.player.distanceToSqr(FEET) > .04) return false;
        if (!verifiedGeometry) {
            for (BlockPos pos : MARKERS.values()) {
                if (client.level.getBlockEntity(pos) == null) return false;
            }
            verifyResourceIdentity(client);
            verifyBakedModel(client);
            verifyRenderer(client);
            var camera = client.gameRenderer.getMainCamera();
            verifyFlowerCycle(camera);
            verifyGraceIcon(client);
            verifiedGeometry = true;
            observingCircle = this;
        }
        if (!verifiedCircle) return false;
        observingCircle = null;
        System.out.println("CUPID SHRINE CLIENT PASS baked exact sculpture and both feet, grounded pedestal,"
                + " four marker facings, mounted full-bright 64-frame Lotus, 40 world ring marks and Grace atlas icon");
        return true;
    }

    private static void verifyResourceIdentity(Minecraft client) throws IOException, NoSuchAlgorithmException {
        var sha256 = MessageDigest.getInstance("SHA-256");
        for (var resource : APPROVED_RESOURCES.entrySet()) {
            var path = id(resource.getKey());
            try (var bytes = client.getResourceManager().open(path)) {
                String digest = HexFormat.of().formatHex(sha256.digest(bytes.readAllBytes()));
                require(digest.equals(resource.getValue()), "Client loaded different bytes for approved resource " + path);
            }
        }
    }

    private static void verifyBakedModel(Minecraft client) {
        var manager = client.getModelManager();
        var model = manager.getModel(ModelResourceLocation.standalone(id(SCULPTURE)));
        require(model != manager.getMissingModel(), "The seated Cupid OBJ was not registered or did not bake");
        var counts = new HashMap<ResourceLocation, Integer>();
        int stride = DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;
        int lowFoot = 0;
        int raisedFoot = 0;
        for (var quad : model.getQuads(null, null, RandomSource.create(42), ModelData.EMPTY, null)) {
            ResourceLocation material = quad.getSprite().contents().name();
            counts.merge(material, 1, Integer::sum);
            if (!material.equals(id(SCULPTURE + "/cupid"))) continue;
            int[] data = quad.getVertices();
            for (int i = 0; i < data.length; i += stride) {
                float x = Float.intBitsToFloat(data[i]);
                float y = Float.intBitsToFloat(data[i + 1]);
                float z = Float.intBitsToFloat(data[i + 2]);
                if (x > .1 && y > .34 && y < .425 && z > .4) lowFoot++;
                if (x < -.1 && y > .75 && y < .89 && z > .25) raisedFoot++;
            }
        }
        require(counts.size() == MATERIALS.size(), "Unexpected baked material sprites: " + counts);
        for (var entry : MATERIALS.entrySet()) {
            require(entry.getValue().equals(counts.get(id(SCULPTURE + "/" + entry.getKey()))),
                    "Missing or substituted baked sculpture geometry for " + entry.getKey() + ": " + counts);
        }
        require(lowFoot > 60 && raisedFoot > 60, "Both approved barefoot shapes must survive OBJ baking");
    }

    private static void verifyRenderer(Minecraft client) {
        var camera = client.gameRenderer.getMainCamera();
        int frame = Math.floorMod(client.level.getGameTime() / 2, 64);
        var draws = new EnumMap<Direction, RecordedDraw>(Direction.class);
        for (var marker : MARKERS.entrySet()) {
            Direction facing = marker.getKey();
            var entity = client.level.getBlockEntity(marker.getValue());
            require(entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING) == facing,
                    "Native marker lost " + facing + " facing");
            var renderer = client.getBlockEntityRenderDispatcher().getRenderer(entity);
            require(renderer != null && renderer.getClass().getName().equals("dev.uwu.dimension.CupidShrineRenderer"),
                    "Block entity dispatcher did not register the shrine renderer");
            var draw = new RecordedDraw();
            renderer.render(entity, 0, new PoseStack(), draw, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            require(draw.stone.positions.size() == QUADS * 4, "Baked statue was not fully drawn for " + facing);
            verifyFlower(draw.flower, rotate(HAND, facing).add(.5, 0, .5), frame, camera);
            draws.put(facing, draw);
        }
        var south = draws.get(Direction.SOUTH).stone.positions;
        double minY = south.stream().mapToDouble(Vec3::y).min().orElseThrow();
        double maxY = south.stream().mapToDouble(Vec3::y).max().orElseThrow();
        require(Math.abs(minY) < EPSILON && Math.abs(maxY - 1.8) < EPSILON,
                "Pedestal no longer sits on the floor or statue scale changed: " + minY + ".." + maxY);
        for (var entry : draws.entrySet()) {
            for (int i = 0; i < south.size(); i++) {
                Vec3 expected = rotate(south.get(i).add(-.5, 0, -.5), entry.getKey()).add(.5, 0, .5);
                if (!near(expected, entry.getValue().stone.positions.get(i))) {
                    throw new AssertionError("Statue and pedestal did not rotate together for " + entry.getKey()
                            + " at vertex " + i);
                }
            }
        }
    }

    private static void verifyFlowerCycle(Camera camera) throws ReflectiveOperationException {
        var flower = Class.forName("dev.uwu.dimension.SeatedCupidFollowerFlower").getMethod("draw",
                PoseStack.class, MultiBufferSource.class, float.class, Camera.class, long.class);
        for (long tick = 0; tick <= 128; tick++) {
            var draw = new RecordedDraw();
            flower.invoke(null, new PoseStack(), draw, 0F, camera, tick);
            verifyFlower(draw.flower, HAND, (int) (tick / 2 % 64), camera);
        }
    }

    private static void verifyFlower(VertexRecorder draws, Vec3 mount, int frame, Camera camera) {
        var positions = draws.positions;
        var uvs = draws.uvs;
        require(positions.size() == 4 && uvs.size() == 4 && draws.fullBright == 4,
                "Lotus did not submit one full-bright billboard");
        Vec3 center = positions.stream().reduce(Vec3.ZERO, Vec3::add).scale(.25);
        require(near(center, mount), "Lotus did not follow the rotated hand mount: " + center + " vs " + mount);
        Vec3 width = positions.get(1).subtract(positions.get(0));
        Vec3 height = positions.get(3).subtract(positions.get(0));
        require(Math.abs(width.length() - .26) < EPSILON && Math.abs(height.length() - .26) < EPSILON,
                "Mounted Lotus sprite changed size");
        var cameraNormal = new Vector3f(0, 0, 1).rotate(camera.rotation());
        double alignment = width.cross(height).normalize().dot(
                new Vec3(cameraNormal.x(), cameraNormal.y(), cameraNormal.z()));
        require(alignment > .999, "Mounted Lotus billboard no longer faces the camera");

        double left = uvs.stream().mapToDouble(Vec3::x).min().orElseThrow();
        double top = uvs.stream().mapToDouble(Vec3::y).min().orElseThrow();
        double right = uvs.stream().mapToDouble(Vec3::x).max().orElseThrow();
        double bottom = uvs.stream().mapToDouble(Vec3::y).max().orElseThrow();
        require(left == (frame % 8) / 8.0 && top == (frame / 8) / 8.0
                        && right - left == .125 && bottom - top == .125,
                "Unexpected UV cell for Lotus frame " + frame + ": " + uvs);
    }

    private static void verifyGraceIcon(Minecraft client) {
        var key = ResourceKey.<MobEffect>create(Registries.MOB_EFFECT, id("cupid_grace"));
        var effect = BuiltInRegistries.MOB_EFFECT.getHolderOrThrow(key);
        require(effect.value().getDisplayName().getString().equals("Cupid Grace"), "Cupid Grace name is missing");
        var icon = client.getMobEffectTextures().get(effect).contents();
        require(icon.name().equals(key.location()), "Cupid Grace icon missed the effect atlas: " + icon.name());
    }

    public static void beginCircle() {
        if (observingCircle != null) observingCircle.circle.clear();
    }

    public static void circleVertex(float x, float y, float z) {
        if (observingCircle != null) observingCircle.circle.add(new Vec3(x, y, z));
    }

    public static void finishCircle(Camera camera) {
        var probe = observingCircle;
        if (probe == null || probe.circle.isEmpty()) return;
        require(probe.circle.size() == 160, "The ring did not draw 40 world-space markers");
        Vec3 center = Vec3.atBottomCenterOf(CENTER).subtract(camera.getPosition()).add(0, .04, 0);
        for (int i = 0; i < probe.circle.size(); i += 4) {
            Vec3 mark = probe.circle.get(i).add(probe.circle.get(i + 1))
                    .add(probe.circle.get(i + 2)).add(probe.circle.get(i + 3)).scale(.25);
            double angle = (i / 4) * Math.TAU / 40;
            Vec3 expected = center.add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4);
            require(near(mark, expected),
                    "Wrong ring radius or ground height at mark " + (i / 4));
        }
        probe.verifiedCircle = true;
        observingCircle = null;
    }

    private static Vec3 rotate(Vec3 offset, Direction facing) {
        return switch (facing) {
            case SOUTH -> offset;
            case NORTH -> new Vec3(-offset.x, offset.y, -offset.z);
            case EAST -> new Vec3(offset.z, offset.y, -offset.x);
            case WEST -> new Vec3(-offset.z, offset.y, offset.x);
            default -> throw new IllegalArgumentException("Expected horizontal facing");
        };
    }

    private static boolean near(Vec3 a, Vec3 b) {
        return a.distanceToSqr(b) < EPSILON * EPSILON;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension", path);
    }

    private static void require(boolean condition, String problem) {
        if (!condition) throw new AssertionError(problem);
    }

    private static final class RecordedDraw implements MultiBufferSource {
        final VertexRecorder stone = new VertexRecorder(false);
        final VertexRecorder flower = new VertexRecorder(true);

        @Override public VertexConsumer getBuffer(RenderType type) {
            if (type == STONE) return stone;
            if (type == FLOWER) return flower;
            throw new AssertionError("Unexpected render layer for shrine: " + type);
        }
    }

    private static final class VertexRecorder implements VertexConsumer {
        final List<Vec3> positions = new ArrayList<>();
        final List<Vec3> uvs = new ArrayList<>(4);
        final boolean collectUV;
        int fullBright;

        VertexRecorder(boolean collectUV) {
            this.collectUV = collectUV;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            require(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z), "Non-finite shrine vertex");
            positions.add(new Vec3(x, y, z));
            return this;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) {
            if (collectUV) uvs.add(new Vec3(u, v, 0));
            return this;
        }

        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) {
            if (collectUV && (u | v << 16) == LightTexture.FULL_BRIGHT) fullBright++;
            return this;
        }

        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
