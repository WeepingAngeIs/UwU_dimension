package dev.uwu.dimension.traversal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Client-only bridge between the traversal view and the integration owner's held-item poses. */
public final class TraversalHeldRendering {
    private TraversalHeldRendering() {}

    @FunctionalInterface
    public interface SnowkissPose {
        /** Evaluate the actual visible held model for this render pass, including its equip and hand transforms. */
        TraversalAttachmentFrame.Sample evaluate(Player actor, float partialTick, SnowkissDraw draw);
    }

    /** Scoped to a single actor in a single draw. A returned sample from another pass cannot attach a ribbon. */
    public static final class SnowkissDraw {
        private final TraversalAttachmentFrame frame;
        private final Player actor;

        private SnowkissDraw(TraversalAttachmentFrame frame, Player actor) {
            this.frame = frame;
            this.actor = actor;
        }

        /** Matrix includes the resolved model's display transform, vanilla centering and the hand-eye pose. */
        public TraversalAttachmentFrame.Sample firstPerson(InteractionHand hand, ItemDisplayContext context,
                ItemStack renderedStack, BakedModel model, Matrix4fc meshToHandEye, Matrix4fc handProjection) {
            Vec3 tip = TraversalLandmarks.snowkiss(actor, hand, context, renderedStack, model);
            return tip == null ? null : frame.firstPerson(actor, hand, context, renderedStack, true,
                tip, meshToHandEye, handProjection);
        }

        /** Matrix includes the animated hand and item transform, with the camera world origin subtracted once. */
        public TraversalAttachmentFrame.Sample thirdPerson(InteractionHand hand, ItemDisplayContext context,
                ItemStack renderedStack, BakedModel model, Matrix4fc meshToCameraRelativeWorld) {
            Vec3 tip = TraversalLandmarks.snowkiss(actor, hand, context, renderedStack, model);
            return tip == null ? null : frame.thirdPerson(actor, hand, context, renderedStack, true,
                tip, meshToCameraRelativeWorld);
        }
    }

    /** Call instead of the raw TraversalView.render entry point. Return null from evaluate for a hidden hand. */
    public static void render(TraversalView view, RenderLevelStageEvent event, SnowkissPose poses) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (poses == null) {
            view.render(event, null);
            return;
        }
        view.render(event, new TraversalView.LashSocket() {
            private TraversalAttachmentFrame frame;

            @Override public Vec3 position(Player actor, float partialTick) {
                var mc = Minecraft.getInstance();
                var camera = event.getCamera();
                if (mc.level == null || actor.level() != mc.level || camera.getEntity() == null) return null;
                if (frame == null) {
                    // TraversalView submits camera-relative vertices through the event's current pose stack.
                    Matrix4f toEye = new Matrix4f(event.getModelViewMatrix()).mul(event.getPoseStack().last().pose());
                    frame = new TraversalAttachmentFrame(mc.level, camera.getEntity(), !camera.isDetached(),
                        camera.getPosition(), toEye, event.getProjectionMatrix());
                }
                if (mc.options.hideGui && camera.getEntity() == actor && !camera.isDetached()) return null;
                return frame.resolve(actor, poses.evaluate(actor, partialTick, new SnowkissDraw(frame, actor)));
            }
        });
    }

    /** Supply the actual held draw and its existing wrapped-grip coordinate in raw baked mesh space. */
    public record VesperDraw(Player actor, InteractionHand hand, ItemDisplayContext context, ItemStack stack,
                             BakedModel selectedModel, Vec3 approvedGrip) {
        private TraversalBladePlant plant(Matrix4fc meshToPose, Vec3 contact) {
            if (contact == null) return null;
            var points = TraversalLandmarks.vesper(actor, hand, context, stack, selectedModel, approvedGrip);
            return points == null ? null : TraversalBladePlant.solve(points.wrappedGrip(), points.tip(), meshToPose, contact);
        }
    }

    /** Use the current hand view's matrices. The returned correction is in hand-eye space. */
    public static TraversalBladePlant firstPersonVesper(TraversalView view, VesperDraw draw, float partialTick,
            TraversalAttachmentFrame cameraFrame, Matrix4fc meshToHandEye, Matrix4fc handProjection) {
        if (draw == null || !currentActor(draw.actor) || cameraFrame == null || !cameraFrame.viewsHandOf(draw.actor)
                || draw.context == null || !draw.context.firstPerson() || Minecraft.getInstance().options.hideGui) return null;
        Vec3 target = view.bladeContact(draw.actor.getId(), partialTick);
        return target == null ? null : draw.plant(meshToHandEye, cameraFrame.worldToHandEye(target, handProjection));
    }

    /** Use the actor's final animated held-item matrix. The correction is in camera-relative world space. */
    public static TraversalBladePlant thirdPersonVesper(TraversalView view, VesperDraw draw, float partialTick,
            Vec3 cameraOrigin, Matrix4fc meshToCameraRelativeWorld) {
        if (draw == null || cameraOrigin == null || !currentActor(draw.actor)
                || draw.context == null || draw.context.firstPerson()) return null;
        Vec3 target = view.bladeContact(draw.actor.getId(), partialTick);
        if (target == null) return null;
        return draw.plant(meshToCameraRelativeWorld, target.subtract(cameraOrigin));
    }

    private static boolean currentActor(Player actor) {
        var level = Minecraft.getInstance().level;
        return actor != null && level != null && actor.level() == level && level.getEntity(actor.getId()) == actor;
    }
}
