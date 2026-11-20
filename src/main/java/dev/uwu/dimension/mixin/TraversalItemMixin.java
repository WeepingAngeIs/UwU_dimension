package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.traversal.client.TraversalRenderPass;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
abstract class TraversalItemMixin {
    @WrapOperation(method="renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/ItemRenderer;render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V"))
    private void uwu$heldSubject(ItemRenderer renderer, ItemStack stack, ItemDisplayContext context, boolean left,
        PoseStack pose, MultiBufferSource buffers, int light, int overlay, BakedModel model, Operation<Void> original,
        LivingEntity entity, ItemStack input, ItemDisplayContext inputContext, boolean inputLeft, PoseStack inputPose,
        MultiBufferSource inputBuffers, Level level, int inputLight, int inputOverlay, int seed) {
        var previous = TraversalRenderPass.subject(entity);
        try { original.call(renderer,stack,context,left,pose,buffers,light,overlay,model); }
        finally { TraversalRenderPass.subject(previous); }
    }

    @Inject(method="render", at=@At(value="INVOKE",target="Lnet/minecraft/client/resources/model/BakedModel;isCustomRenderer()Z"))
    private void uwu$centeredMesh(ItemStack stack, ItemDisplayContext context, boolean left, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        TraversalRenderPass.mesh(stack,context,model,pose);
    }
}
