package dev.uwu.dimension.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.uwu.dimension.DawnpetalSpinCue;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Loaded only with PlayerAnimator on the physical client. */
final class DawnpetalSpinAnimator {
    private static final ResourceLocation RIGHT = ResourceLocation.fromNamespaceAndPath("uwudimension", "dawnpetal_spin");
    private static final ResourceLocation LEFT = ResourceLocation.fromNamespaceAndPath("uwudimension", "dawnpetal_spin_left");

    private DawnpetalSpinAnimator() {}

    static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(RIGHT, 2100, SpinLayer::new);
    }

    static boolean firstPersonPass() {
        return FirstPersonMode.isFirstPersonPass();
    }

    private static KeyframeAnimation clip(HumanoidArm arm) {
        return PlayerAnimationRegistry.getAnimation(arm == HumanoidArm.LEFT ? LEFT : RIGHT)
                instanceof KeyframeAnimation animation ? animation : null;
    }

    static boolean hasLayerAndClip(Player player, HumanoidArm arm) {
        return player instanceof AbstractClientPlayer clientPlayer && clip(arm) != null
                && PlayerAnimationAccess.getPlayerAssociatedData(clientPlayer).get(RIGHT) instanceof SpinLayer;
    }

    private static final class SpinLayer implements IAnimation {
        private final AbstractClientPlayer player;
        private KeyframeAnimationPlayer animation;
        private UUID castId;
        private float headingCorrection;
        private float previousBodyYaw;
        private float bodyMinusCast;
        private boolean recovering;

        private SpinLayer(AbstractClientPlayer player) {
            this.player = player;
        }

        private DawnpetalSpinCue.Frame frame(float partialTick) {
            return DawnpetalSpinClient.renderFrame(player, partialTick);
        }

        @Override
        public boolean isActive() {
            float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            DawnpetalSpinCue.Frame frame = frame(partialTick);
            return frame != null && clip(frame.arm()) != null;
        }

        @Override
        public FirstPersonMode getFirstPersonMode(float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.getCameraEntity() == player && minecraft.options.getCameraType().isFirstPerson()
                    ? FirstPersonMode.DISABLED : FirstPersonMode.NONE;
        }

        @Override
        public void setupAnim(float partialTick) {
            DawnpetalSpinCue.Frame frame = frame(partialTick);
            KeyframeAnimation clip = frame == null ? null : clip(frame.arm());
            if (clip == null) {
                animation = null;
                castId = null;
                return;
            }

            double age = frame.age();
            int tick = (int) age;
            boolean newCast = !frame.castId().equals(castId);
            if (newCast || animation.getCurrentTick() != tick || animation.getData() != clip) {
                animation = new KeyframeAnimationPlayer(clip, tick);
            }
            animation.setupAnim((float) (age - tick));

            float bodyYaw = rendererBodyYaw(player, partialTick);
            boolean recovery = age >= 18;
            if (newCast || recovery != recovering) {
                bodyMinusCast = Mth.wrapDegrees(bodyYaw - frame.castYaw());
            } else {
                bodyMinusCast += Mth.wrapDegrees(bodyYaw - previousBodyYaw);
            }
            previousBodyYaw = bodyYaw;
            recovering = recovery;
            castId = frame.castId();
            headingCorrection = (float) Math.toRadians(DawnpetalSpinVisual.headingCorrectionDegrees(age, bodyMinusCast));
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type, float partialTick, Vec3f value) {
            if (animation == null || DawnpetalSpinClient.animatorFirstPersonPass(player)) return value;
            Vec3f pose = animation.get3DTransform(part, type, partialTick, value);
            // PlayerAnimator applies the body rotation to the entire rendered actor in radians.
            if (part.equals("body") && type == TransformType.ROTATION) {
                return new Vec3f(pose.getX(), pose.getY() + headingCorrection, pose.getZ());
            }
            return pose;
        }

        private static float rendererBodyYaw(AbstractClientPlayer player, float partialTick) {
            // Match the body yaw passed to the renderer, including its mounted head constraint.
            if (player.getVehicle() instanceof LivingEntity mount && mount.shouldRiderSit()) {
                float mountYaw = Mth.rotLerp(partialTick, mount.yBodyRotO, mount.yBodyRot);
                float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
                float difference = Mth.clamp(Mth.wrapDegrees(headYaw - mountYaw), -85, 85);
                return headYaw - difference + (difference * difference > 2500 ? difference * .2F : 0);
            }
            return Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        }
    }
}
