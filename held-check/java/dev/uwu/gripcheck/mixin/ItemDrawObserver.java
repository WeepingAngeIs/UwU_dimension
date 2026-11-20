package dev.uwu.gripcheck.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.gripcheck.GripProbe;
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
abstract class ItemDrawObserver {
    @Inject(method="render", at=@At("HEAD"))
    private void gripcheck$capture(ItemStack item, ItemDisplayContext context, boolean left, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        GripProbe.observe(item, context, left, pose, model);
    }
}
