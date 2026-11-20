package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.traversal.client.TraversalRenderPass;
import dev.uwu.dimension.traversal.client.TraversalWrist;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
abstract class TraversalActorMixin {
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void uwu$collectVisibleWrist(EntityRenderer<?> renderer,Entity actor,float yaw,float partial,PoseStack pose,
            MultiBufferSource buffers,int light,Operation<Void> original) {
        if(actor instanceof Player p && TraversalRenderPass.planting(p))
            TraversalWrist.render(p,buffers,source->original.call(renderer,actor,yaw,partial,pose,source,light));
        else original.call(renderer,actor,yaw,partial,pose,buffers,light);
    }
}
