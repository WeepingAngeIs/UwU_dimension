package dev.uwu.gripcheck.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemInHandRenderer.class)
public interface HandDrawInvoker {
    @Invoker("renderArmWithItem")
    void gripcheck$draw(AbstractClientPlayer owner, float partial, float pitch, InteractionHand hand, float swing,
                        ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffer, int light);
}
