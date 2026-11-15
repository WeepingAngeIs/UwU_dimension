package dev.uwu.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.test.RelicPortraits;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArrowRenderer.class)
public abstract class CupidArrowRenderMixin {
    @Inject(method="render(Lnet/minecraft/world/entity/projectile/AbstractArrow;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("TAIL"))
    private void photographed(AbstractArrow arrow,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo ci) {
        RelicPortraits.rendered(arrow);
    }
}
