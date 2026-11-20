package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.RevivalBlossoms;
import dev.uwu.dimension.client.RevivalHandPose;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandRenderer.class)
abstract class RevivalRestingHandMixin {
    // The second renderItem call is the ordinary item draw inside vanilla's hand pose push/pop.
    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void uwu$restingGrip(ItemInHandRenderer renderer, LivingEntity owner, ItemStack stack,
            ItemDisplayContext view, boolean left, PoseStack pose, MultiBufferSource buffers, int light,
            Operation<Void> draw, @Local(argsOnly = true) InteractionHand hand,
            @Local(argsOnly = true, ordinal = 0) float partial, @Local(argsOnly = true, ordinal = 2) float swing,
            @Local(argsOnly = true, ordinal = 3) float equip) {
        boolean axe = stack.is(RevivalBlossoms.AXE.get());
        if ((axe || stack.is(RevivalBlossoms.DIRK.get()))
                && RevivalHandPose.usesRestingGrip(owner, hand, stack, partial, swing)) {
            pose.translate(0, -RevivalHandPose.recoveryDrop(owner, partial, equip), 0);
            if (axe) {
                RevivalHandPose.drawAxe(stack, () -> draw.call(renderer, owner, stack, view, left, pose, buffers, light));
                return;
            }
            pose.translate(0, -.18, 0);
        }
        draw.call(renderer, owner, stack, view, left, pose, buffers, light);
    }
}
