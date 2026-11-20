package dev.uwu.dimension.traversal.client;

import dev.uwu.dimension.Arsenal;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4fc;
import org.joml.Vector4d;

/**
 * One world view in one rendered frame. Create a fresh instance for each pass, including
 * passes with identical camera, game tick and interpolation. Supply matrices from that view
 * and the same normalized viewport. All positions use camera-relative math until the final
 * double-precision addition. Projection matrices use Minecraft's OpenGL clip-depth range.
 */
public final class TraversalAttachmentFrame {
    public static final class Sample {
        private final TraversalAttachmentFrame frame;
        private final Player actor;
        private final HumanoidArm arm;
        private final Vec3 position;

        private Sample(TraversalAttachmentFrame frame, Player actor, Vec3 position) {
            this.frame = frame;
            this.actor = actor;
            this.arm = actor.getMainArm();
            this.position = position;
        }
    }

    private final ClientLevel level;
    private final Entity cameraEntity;
    private final boolean firstPerson;
    private final Vec3 cameraOrigin;
    private final Matrix4d worldToEye;
    private final Matrix4d eyeToWorld;
    private final Matrix4d worldProjection;
    private final Matrix4d inverseWorldProjection;

    public TraversalAttachmentFrame(ClientLevel level, Entity cameraEntity, boolean firstPerson,
            Vec3 cameraOrigin, Matrix4fc cameraRelativeWorldToEye, Matrix4fc worldProjection) {
        this.level = level;
        this.cameraEntity = cameraEntity;
        this.firstPerson = firstPerson;
        this.cameraOrigin = cameraOrigin;
        this.worldToEye = checkedMatrix(cameraRelativeWorldToEye);
        this.eyeToWorld = inverse(worldToEye);
        this.worldProjection = checkedMatrix(worldProjection);
        this.inverseWorldProjection = inverse(this.worldProjection);
    }

    /**
     * The pose owner supplies the visible hand draw's fully evaluated mesh-to-eye matrix and
     * its actual hand projection, including the selected model's display transform and the
     * renderer's centering and handedness exactly once. Supply the same-frame pose before
     * the world effect draws. The later vanilla hand pass cannot retroactively supply it.
     */
    public Sample firstPerson(Player actor, InteractionHand hand, ItemDisplayContext context,
            ItemStack renderedStack, boolean visible, Vec3 bakedTip, Matrix4fc meshToHandEye,
            Matrix4fc handProjection) {
        if (!acceptsDraw(actor, hand, context, renderedStack, visible, true)) return null;
        Vec3 handEye = point(checkedMatrix(meshToHandEye), bakedTip);
        Vec3 worldEye = atSameDepth(handEye, checkedMatrix(handProjection), inverseWorldProjection);
        return sample(actor, point(eyeToWorld, worldEye));
    }

    /** Map a world contact into the hand's eye space at its world forward depth and screen position. */
    public Vec3 worldToHandEye(Vec3 worldPoint, Matrix4fc handProjection) {
        if (!firstPerson || !validView() || !finite(worldPoint)) return null;
        Vec3 worldEye = point(worldToEye, worldPoint.subtract(cameraOrigin));
        return atSameDepth(worldEye, worldProjection, inverse(checkedMatrix(handProjection)));
    }

    boolean viewsHandOf(Player actor) {
        return firstPerson && cameraEntity == actor && actor != null && actor.level() == level && validView();
    }

    /** The fully posed third-person mesh is already in the world pass's coordinate system. */
    public Sample thirdPerson(Player actor, InteractionHand hand, ItemDisplayContext context,
            ItemStack renderedStack, boolean visible, Vec3 bakedTip, Matrix4fc meshToCameraRelativeWorld) {
        if (!acceptsDraw(actor, hand, context, renderedStack, visible, false)) return null;
        Vec3 relative = point(checkedMatrix(meshToCameraRelativeWorld), bakedTip);
        Vec3 eye = point(worldToEye, relative);
        if (eye == null || eye.z >= 0 || !visibleDepth(transform(worldProjection, eye))) return null;
        return sample(actor, relative);
    }

    /** Use in the current world's lash callback. A sample belongs to one actor and one view. */
    public Vec3 resolve(Player actor, Sample sample) {
        if (sample == null || sample.frame != this || sample.actor != actor || !validView() || !equipped(actor)
                || sample.arm != actor.getMainArm()) return null;
        return sample.position;
    }

    private boolean acceptsDraw(Player actor, InteractionHand hand, ItemDisplayContext context,
            ItemStack renderedStack, boolean visible, boolean handPass) {
        if (!visible || hand != InteractionHand.MAIN_HAND || !equipped(actor) || !snowkiss(renderedStack)
                || !validView()
                || handPass != (firstPerson && cameraEntity == actor)) return false;
        boolean left = actor.getMainArm() == HumanoidArm.LEFT;
        if (handPass) return context == (left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
        return context == (left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    }

    private boolean equipped(Player actor) {
        return actor != null && actor.level() == level && snowkiss(actor.getMainHandItem());
    }

    private boolean validView() {
        return level != null && cameraEntity != null && cameraEntity.level() == level
                && finite(cameraOrigin) && eyeToWorld != null && inverseWorldProjection != null;
    }

    private static boolean snowkiss(ItemStack stack) {
        return stack != null && stack.is(Arsenal.WEAPONS.get(Arsenal.Relic.FROST_WHIP).get());
    }

    private Sample sample(Player actor, Vec3 relative) {
        if (relative == null) return null;
        Vec3 world = cameraOrigin.add(relative);
        return finite(world) ? new Sample(this, actor, world) : null;
    }

    private static Matrix4d checkedMatrix(Matrix4fc source) {
        if (source == null || !source.isFinite()) return null;
        Matrix4d matrix = new Matrix4d(source);
        double determinant = matrix.determinant();
        return Double.isFinite(determinant) && determinant != 0 ? matrix : null;
    }

    private static Matrix4d inverse(Matrix4d matrix) {
        if (matrix == null) return null;
        Matrix4d result = matrix.invert(new Matrix4d());
        return result.isFinite() ? result : null;
    }

    private static Vec3 atSameDepth(Vec3 sourceEye, Matrix4d sourceProjection, Matrix4d inverseDestinationProjection) {
        if (sourceEye == null || sourceEye.z >= 0 || inverseDestinationProjection == null) return null;
        Vector4d clip = transform(sourceProjection, sourceEye);
        if (!visibleDepth(clip)) return null;
        // NDC x/y fixes a ray. Intersect that ray with the plane z = sourceEye.z.
        // Copying NDC z would change the physical distance for different near/far planes.
        Vector4d rayOrigin = inverseDestinationProjection.transform(new Vector4d(clip.x / clip.w, clip.y / clip.w, 0, 1));
        Vector4d rayStep = inverseDestinationProjection.transform(new Vector4d(0, 0, 1, 0));
        double denominator = rayStep.z - sourceEye.z * rayStep.w;
        if (denominator == 0 || !Double.isFinite(denominator)) return null;
        double ndcDepth = (sourceEye.z * rayOrigin.w - rayOrigin.z) / denominator;
        if (!Double.isFinite(ndcDepth) || ndcDepth < -1 || ndcDepth > 1) return null;
        return cartesian(rayOrigin.add(rayStep.mul(ndcDepth)));
    }

    private static Vector4d transform(Matrix4d matrix, Vec3 point) {
        if (matrix == null || !finite(point)) return null;
        return matrix.transform(new Vector4d(point.x, point.y, point.z, 1));
    }

    private static Vec3 point(Matrix4d matrix, Vec3 point) {
        return cartesian(transform(matrix, point));
    }

    private static Vec3 cartesian(Vector4d value) {
        if (value == null || !value.isFinite() || value.w <= 0) return null;
        Vec3 point = new Vec3(value.x / value.w, value.y / value.w, value.z / value.w);
        return finite(point) ? point : null;
    }

    private static boolean visibleDepth(Vector4d clip) {
        return clip != null && clip.isFinite() && clip.w > 0 && clip.z >= -clip.w && clip.z <= clip.w;
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
