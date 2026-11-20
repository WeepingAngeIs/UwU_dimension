package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.traversal.client.TraversalRenderPass;
import dev.uwu.dimension.traversal.client.TraversalWrist;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandRenderer.class)
abstract class TraversalHandMixin {
    @WrapOperation(method="renderHandsWithItems", at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void uwu$firstWrist(ItemInHandRenderer renderer,AbstractClientPlayer actor,float partial,float pitch,
            InteractionHand hand,float swing,ItemStack stack,float equip,PoseStack pose,MultiBufferSource buffers,
            int light,Operation<Void> original) {
        if(hand==InteractionHand.MAIN_HAND && TraversalRenderPass.planting(actor))
            TraversalWrist.render(actor,buffers,b->original.call(renderer,actor,partial,pitch,hand,swing,stack,equip,pose,b,light));
        else original.call(renderer,actor,partial,pitch,hand,swing,stack,equip,pose,buffers,light);
    }
}
