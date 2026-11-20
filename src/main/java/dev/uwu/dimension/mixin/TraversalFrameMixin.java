package dev.uwu.dimension.mixin;

import dev.uwu.dimension.traversal.client.TraversalRenderPass;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class TraversalFrameMixin {
    @Inject(method="renderLevel", at=@At("HEAD"))
    private void uwu$beginTraversalView(DeltaTracker clock, CallbackInfo ci) { TraversalRenderPass.begin(); }

    @Inject(method="renderLevel", at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void uwu$saveTraversalDepth(DeltaTracker clock, CallbackInfo ci) { TraversalRenderPass.beforeHand(); }

    @Inject(method="renderItemInHand", at=@At(value="INVOKE",
        target="Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V",shift=At.Shift.AFTER))
    private void uwu$finishVisibleHand(CallbackInfo ci) { TraversalRenderPass.afterHand(); }

    @Inject(method="renderLevel", at=@At("RETURN"))
    private void uwu$endTraversalView(DeltaTracker clock, CallbackInfo ci) { TraversalRenderPass.end(); }
}
