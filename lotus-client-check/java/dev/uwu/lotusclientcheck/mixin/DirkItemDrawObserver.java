package dev.uwu.lotusclientcheck.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.lotusclientcheck.DirkCountdownProbe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
abstract class DirkItemDrawObserver {
    @Inject(method = "render", at = @At("HEAD"))
    private void observeModel(ItemStack stack, ItemDisplayContext context, boolean left, PoseStack poses,
                              MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        DirkCountdownProbe.model(stack, context, model);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void observeAppliedPose(ItemStack stack, ItemDisplayContext context, boolean left, PoseStack poses,
                                    MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        DirkCountdownProbe.draw(stack, context, poses);
    }
}
