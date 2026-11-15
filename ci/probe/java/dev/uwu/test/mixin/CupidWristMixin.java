package dev.uwu.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.test.RelicPortraits;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class CupidWristMixin {
    // The normal held photos keep the gameplay pose. Only the four explicitly named inspection
    // frames turn the wrist, after the real production model and display transform are selected.
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void turn(ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose,MultiBufferSource buffers,
            int light,int overlay,BakedModel model,CallbackInfo ci) {
        if(context==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) RelicPortraits.turnForInspection(stack,pose);
    }
}
