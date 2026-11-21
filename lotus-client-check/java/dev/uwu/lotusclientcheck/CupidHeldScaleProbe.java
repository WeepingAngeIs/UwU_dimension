package dev.uwu.lotusclientcheck;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.uwu.dimension.CupidBowVisualConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Vector3f;

/** Drives the actual ItemRenderer mixin, collecting the vertices it would submit to the GPU. */
final class CupidHeldScaleProbe {
    private static final float EPSILON = 2E-4F;
    private static final String MARKER = "uwudimension:smooth_cupid";
    private static final List<ItemDisplayContext> HANDS = List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND);

    private CupidHeldScaleProbe() {}

    static void verify(Minecraft client) {
        ItemStack classic = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                "uwudimension", "cupid_bow")).getDefaultInstance();
        ItemStack smooth = classic.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, smooth, tag -> tag.putBoolean(MARKER, true));
        ItemStack otherBow = new ItemStack(Items.BOW);
        CustomData.update(DataComponents.CUSTOM_DATA, otherBow, tag -> tag.putBoolean(MARKER, true));
        double original = CupidBowVisualConfig.HELD_SCALE.get();
        try {
            for (double scale : new double[]{.75, 1, 1.5}) {
                CupidBowVisualConfig.HELD_SCALE.set(scale);
                for (ItemDisplayContext view : HANDS) {
                    // Reference is the exact shipped model, bypassing only renderStatic's new wrapper.
                    RecordedDraw baseline = draw(client, smooth, view, false);
                    RecordedDraw actual = draw(client, smooth, view, true);
                    require(actual.vertices() > 400, "No detailed held mesh emitted for " + view);
                    var mount = parent();
                    var model = client.getItemRenderer().getModel(smooth, client.level, client.player, 42);
                    ClientHooks.handleCameraTransforms(mount, model, view, left(view));
                    float d = (float) (1 / Math.sqrt(2));
                    Vector3f grip = mount.last().pose().transformPosition(-.1875F * d, .1875F * d, 0,
                            new Vector3f());
                    Vector3f forward = mount.last().pose().transformDirection(-d, d, 0, new Vector3f());
                    float clearance = view.firstPerson() ? 0 : (float) (1.0 / 32 + Math.max(0, scale - 1) / 8);
                    verifyAffine(baseline, actual, grip, forward.mul(clearance), (float) scale,
                            "detailed " + view + " scale=" + scale);
                }
                for (ItemStack untouched : List.of(classic, otherBow)) {
                    for (ItemDisplayContext view : HANDS) {
                        verifySame(draw(client, untouched, view, false), draw(client, untouched, view, true),
                                "unaffected " + untouched.getItem() + " " + view + " scale=" + scale);
                    }
                }
                for (ItemDisplayContext view : List.of(ItemDisplayContext.GUI, ItemDisplayContext.GROUND,
                        ItemDisplayContext.FIXED, ItemDisplayContext.HEAD, ItemDisplayContext.NONE)) {
                    verifySame(draw(client, smooth, view, false), draw(client, smooth, view, true),
                            "unaffected detailed " + view + " scale=" + scale);
                }
            }
        } finally {
            CupidBowVisualConfig.HELD_SCALE.set(original);
        }
        System.out.println("CUPID HELD SCALE PASS real ItemRenderer path; 0.75/1.0/1.5, both hands,"
                + " fixed first-person grip with zero forward shift, bounded mirrored third-person clearance;"
                + " classic Cupid, other bows and non-held views unchanged");
    }

    private static RecordedDraw draw(Minecraft client, ItemStack stack, ItemDisplayContext view, boolean wrapped) {
        var output = new RecordedDraw();
        var renderer = client.getItemRenderer();
        if (wrapped) {
            renderer.renderStatic(client.player, stack, view, left(view), parent(), output, client.level,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 42);
        } else {
            var model = renderer.getModel(stack, client.level, client.player, 42);
            renderer.render(stack, view, left(view), parent(), output, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, model);
        }
        return output;
    }

    private static PoseStack parent() {
        var pose = new PoseStack();
        pose.translate(.17, -.23, -.37);
        pose.mulPose(Axis.YP.rotationDegrees(27));
        pose.mulPose(Axis.ZP.rotationDegrees(12));
        return pose;
    }

    private static void verifySame(RecordedDraw expected, RecordedDraw actual, String label) {
        verifyAffine(expected, actual, new Vector3f(), new Vector3f(), 1, label);
    }

    private static void verifyAffine(RecordedDraw baseline, RecordedDraw actual, Vector3f grip,
                                     Vector3f forward, float scale, String label) {
        require(baseline.parts.keySet().equals(actual.parts.keySet()), "Changed render passes: " + label);
        for (var entry : baseline.parts.entrySet()) {
            var original = entry.getValue().positions;
            var changed = actual.parts.get(entry.getKey()).positions;
            require(original.size() == changed.size(), "Changed vertex count: " + label);
            for (int i = 0; i < original.size(); i++) {
                Vector3f expected = new Vector3f(original.get(i)).sub(grip).mul(scale).add(grip).add(forward);
                require(expected.distance(changed.get(i)) < EPSILON,
                        "Wrong mounted grip/scale/forward vector for " + label + " at vertex " + i
                                + ": expected=" + expected + " actual=" + changed.get(i));
            }
        }
    }

    private static boolean left(ItemDisplayContext view) {
        return view == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || view == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordedDraw implements MultiBufferSource {
        private final Map<RenderType, RecordedVertices> parts = new LinkedHashMap<>();

        @Override public VertexConsumer getBuffer(RenderType type) {
            return parts.computeIfAbsent(type, ignored -> new RecordedVertices());
        }

        int vertices() {
            return parts.values().stream().mapToInt(part -> part.positions.size()).sum();
        }
    }

    private static final class RecordedVertices implements VertexConsumer {
        private final List<Vector3f> positions = new ArrayList<>();

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            require(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z), "Non-finite held mesh vertex");
            positions.add(new Vector3f(x, y, z));
            return this;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
