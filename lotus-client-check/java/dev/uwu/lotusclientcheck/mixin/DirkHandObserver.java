package dev.uwu.lotusclientcheck.mixin;

import dev.uwu.lotusclientcheck.DirkCountdownProbe;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class DirkHandObserver {
    @Shadow private ItemStack mainHandItem;
    @Shadow private float mainHandHeight;
    @Shadow private float oMainHandHeight;

    @Inject(method = "tick", at = @At("TAIL"))
    private void observeNativeHeight(CallbackInfo ci) {
        DirkCountdownProbe.handTick(mainHandItem, mainHandHeight, oMainHandHeight);
    }
}
