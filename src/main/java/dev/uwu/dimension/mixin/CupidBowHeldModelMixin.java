package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.uwu.dimension.CupidBowHeldVisual;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Select the usual draw/release model first, then adjust only its held cosmetic presentation. */
@Mixin(ItemRenderer.class)
abstract class CupidBowHeldModelMixin {
    @ModifyExpressionValue(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;getModel(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)Lnet/minecraft/client/resources/model/BakedModel;"))
    private BakedModel uwu$cupidHeldScale(BakedModel model, @Local(argsOnly = true) LivingEntity owner,
                                        @Local(argsOnly = true) ItemStack stack,
                                        @Local(argsOnly = true) ItemDisplayContext context) {
        return CupidBowHeldVisual.wrap(model, owner, stack, context);
    }
}
