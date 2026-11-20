package dev.uwu.dimension.traversal.client;

import dev.uwu.dimension.Arsenal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.zip.CRC32;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.CompositeModel;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Landmarks in raw baked vertices' [0,1] mesh space. The supplied rendering matrix must
 * already contain ItemRenderer's normal translate(-.5, -.5, -.5), exactly once. */
public final class TraversalLandmarks {
    public record Vesper(Vec3 tip, Vec3 wrappedGrip, ResourceLocation modelId, int element, boolean flowerCredit) {}

    record Mesh(ResourceLocation modelId, int element, boolean flowerCredit, Vec3 tip, long silhouette) {}

    // CRC32 over 32 x 32 occupancy bytes (clear = 0, visible = 1), row-major, at the grip owner's pin.
    private static final long[] VESPER_SHAPES = {0x0cb68cb4L, 0xdc00b05aL, 0x2448acb6L, 0x3dc071a6L};
    // Alternate release art shares the stock model and layer IDs.
    private static final long[] SWEEP_SHAPES = {0xa55ff0a1L, 0x2e3c59ebL, 0x9a120087L, 0x9c3e5d37L};
    private static final Vec3[] SWEEP_TIPS = {texel(0.5, 6.5), texel(0.5, 6.5), texel(0.5, 6.5), texel(0.5, 6.5)};
    private static final Mesh SNOWKISS = new Mesh(item("frost_whip"), -1, false, texel(26.5, 20.5), 0xf0b70af9L);
    private static final Map<List<ResourceLocation>, Mesh> KNOWN = knownLayers();
    // Accessed only during client render. Weak keys let obsolete baked models go on resource reload.
    private static final Map<BakedModel, Optional<Mesh>> CACHE = new WeakHashMap<>();

    private TraversalLandmarks() {}

    /** The grip comes from the integration owner's approved mesh landmark, independently of the trial tip. */
    public static Vesper vesper(Player actor, InteractionHand hand, ItemDisplayContext context,
                                ItemStack renderedStack, BakedModel selectedModel, Vec3 approvedWrappedGrip) {
        if (approvedWrappedGrip == null || !Double.isFinite(approvedWrappedGrip.x)
                || !Double.isFinite(approvedWrappedGrip.y) || !Double.isFinite(approvedWrappedGrip.z)) return null;
        Mesh mesh = held(actor, hand, context, renderedStack, selectedModel, Arsenal.Relic.VESPER_SCYTHE);
        if (mesh == null || mesh == SNOWKISS) return null;
        return new Vesper(mesh.tip, approvedWrappedGrip, mesh.modelId, mesh.element, mesh.flowerCredit);
    }

    public static Vec3 snowkiss(Player actor, InteractionHand hand, ItemDisplayContext context,
                                ItemStack renderedStack, BakedModel selectedModel) {
        return held(actor, hand, context, renderedStack, selectedModel, Arsenal.Relic.FROST_WHIP) == SNOWKISS
                ? SNOWKISS.tip : null;
    }

    private static Mesh held(Player actor, InteractionHand hand, ItemDisplayContext context, ItemStack stack,
                             BakedModel selected, Arsenal.Relic relic) {
        if (actor == null || !(actor.level() instanceof ClientLevel level) || hand != InteractionHand.MAIN_HAND
                || context == null || stack == null || selected == null || selected.getClass() != CompositeModel.Baked.class) return null;
        boolean correctArm = switch (context) {
            case FIRST_PERSON_LEFT_HAND, THIRD_PERSON_LEFT_HAND -> actor.getMainArm() == HumanoidArm.LEFT;
            case FIRST_PERSON_RIGHT_HAND, THIRD_PERSON_RIGHT_HAND -> actor.getMainArm() == HumanoidArm.RIGHT;
            default -> false;
        };
        if (!correctArm) return null;
        var item = Arsenal.WEAPONS.get(relic).get();
        if (!stack.is(item) || !actor.getMainHandItem().is(item)) return null;
        // Match ItemInHandRenderer.renderItem, including the rendered actor, level and seed.
        // Override dependencies need not have top-level ModelManager entries of their own.
        BakedModel actual = Minecraft.getInstance().getItemRenderer().getModel(stack, level, actor, actor.getId() + context.ordinal());
        if (actual != selected) return null;
        return CACHE.computeIfAbsent(selected, model -> Optional.ofNullable(readMesh(
                model.getQuads(null, null, RandomSource.create(42), ModelData.EMPTY, null)))).orElse(null);
    }

    private static Map<List<ResourceLocation>, Mesh> knownLayers() {
        Map<List<ResourceLocation>, Mesh> models = new HashMap<>();
        models.put(List.of(SNOWKISS.modelId, item("animated/frost_whip_glint")), SNOWKISS);
        for (int element = 0; element < 4; element++) {
            String variant = "vesper_scythe_" + element;
            String ordinary = element == 0 ? "vesper_scythe" : variant;
            ResourceLocation blade = item(variant);
            Vec3 tip = texel(element == 3 ? 1.5 : 2.5, 14.5);
            models.put(List.of(blade, item("animated/" + ordinary + "_glint")),
                    new Mesh(item(ordinary), element, false, tip, VESPER_SHAPES[element]));
            models.put(List.of(blade, item("animated/" + variant + "_glint"), item("flower_credit_bud")),
                    new Mesh(item("vesper_scythe_credit_" + element), element, true, tip, VESPER_SHAPES[element]));
        }
        return Map.copyOf(models);
    }

    // Read the selected model's actual layers, including the bud. The credit deadline and element
    // predicates have already been evaluated by the stock renderer for the correct subject.
    static Mesh readMesh(List<BakedQuad> quads) {
        TextureAtlasSprite[] layers = new TextureAtlasSprite[3];
        int front = 0;
        int back = 0;
        for (BakedQuad quad : quads) {
            int layer = quad.getTintIndex();
            TextureAtlasSprite sprite = quad.getSprite();
            if (layer < 0 || layer >= layers.length || sprite == null
                    || (layers[layer] != null && layers[layer] != sprite)) return null;
            layers[layer] = sprite;
            if (layer == 0 && quad.getDirection().getAxis() == Direction.Axis.Z) {
                if (!standardFace(quad, sprite)) return null;
                if (quad.getDirection() == Direction.SOUTH) front++;
                else back++;
            }
        }
        if (front != 1 || back != 1 || layers[1] == null) return null;
        List<ResourceLocation> names = new ArrayList<>(3);
        for (TextureAtlasSprite layer : layers) {
            if (layer != null) names.add(layer.contents().name());
        }
        Mesh mesh = KNOWN.get(names);
        if (mesh == null) return null;
        var blade = layers[0].contents();
        if (blade.width() != 32 || blade.height() != 32
                || blade.getOriginalImage().getWidth() != 32 || blade.getOriginalImage().getHeight() != 32) return null;
        // Resource packs can replace the outline while keeping all the same resource IDs.
        CRC32 outline = new CRC32();
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) outline.update(blade.isTransparent(0, x, y) ? 0 : 1);
        }
        long shape = outline.getValue();
        if (shape == mesh.silhouette) return mesh;
        if (mesh.element >= 0 && shape == SWEEP_SHAPES[mesh.element]) {
            return new Mesh(mesh.modelId, mesh.element, mesh.flowerCredit, SWEEP_TIPS[mesh.element], shape);
        }
        return null;
    }

    // Require both generated base faces to span the unit square in the pinned texture orientation.
    // A rotated, translated, mirrored or redrawn-through-the-tip mesh cannot borrow these coordinates.
    private static boolean standardFace(BakedQuad quad, TextureAtlasSprite sprite) {
        int[] vertices = quad.getVertices();
        if (vertices.length != 4 * IQuadTransformer.STRIDE) return false;
        float depth = quad.getDirection() == Direction.SOUTH ? 8.5F / 16 : 7.5F / 16;
        float shrink = sprite.uvShrinkRatio();
        int corners = 0;
        for (int start = 0; start < vertices.length; start += IQuadTransformer.STRIDE) {
            int pos = start + IQuadTransformer.POSITION;
            int uv = start + IQuadTransformer.UV0;
            float x = Float.intBitsToFloat(vertices[pos]);
            float y = Float.intBitsToFloat(vertices[pos + 1]);
            if ((x != 0 && x != 1) || (y != 0 && y != 1) || Float.intBitsToFloat(vertices[pos + 2]) != depth) return false;
            float u = x + shrink * (.5F - x);
            float v = 1 - y + shrink * (y - .5F);
            if (!(Math.abs(Float.intBitsToFloat(vertices[uv]) - sprite.getU(u)) < 1e-6F
                    && Math.abs(Float.intBitsToFloat(vertices[uv + 1]) - sprite.getV(v)) < 1e-6F)) return false;
            corners |= 1 << (2 * (int) x + (int) y);
        }
        return corners == 15;
    }

    private static Vec3 texel(double u, double v) {
        return new Vec3(u / 32, 1 - v / 32, .5);
    }

    private static ResourceLocation item(String name) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension", "item/" + name);
    }
}
