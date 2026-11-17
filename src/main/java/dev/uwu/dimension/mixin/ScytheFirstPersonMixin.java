package dev.uwu.dimension.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.uwu.dimension.WeaponPresentation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The same existing attack clock drives the unprofiled scythe's first-person reap. */
@Mixin(ItemInHandRenderer.class)
abstract class ScytheFirstPersonMixin {
    @Unique private boolean uwu$scythePose;

    @Inject(method="renderArmWithItem", at=@At("HEAD"))
    private void uwu$beginReap(AbstractClientPlayer player, float partial, float pitch,
            InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poses,
            MultiBufferSource buffers, int light, CallbackInfo callback) {
        uwu$scythePose = swing > 0 && !player.isUsingItem() && WeaponPresentation.unprofiledScythe(stack);
        if (!uwu$scythePose) return;
        poses.pushPose();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float sweep = Mth.sin(Mth.sqrt(swing) * Mth.PI);
        poses.translate(-side * .11F * sweep, .06F * sweep, 0);
        poses.mulPose(Axis.YP.rotationDegrees(-side * 18F * sweep));
        poses.mulPose(Axis.ZP.rotationDegrees(side * 12F * sweep));
    }

    @Inject(method="renderArmWithItem", at=@At("TAIL"))
    private void uwu$finishReap(AbstractClientPlayer player, float partial, float pitch,
            InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poses,
            MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (uwu$scythePose) {
            poses.popPose();
            uwu$scythePose = false;
        }
    }
}
