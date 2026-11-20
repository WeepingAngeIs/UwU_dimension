package dev.uwu.hotbarcheck.mixin;

import dev.uwu.dimension.DawnpetalAbilities;
import dev.uwu.hotbarcheck.HotbarProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DawnpetalAbilities.class, remap = false)
abstract class AbilityNetworkObserver {
    @Inject(method = "send", at = @At("HEAD"))
    private static void hotbarCheck$sent(CallbackInfo callback) {
        HotbarProbe.sentUse();
    }

    @Inject(method = "receive", at = @At("HEAD"))
    private static void hotbarCheck$received(CallbackInfo callback) {
        HotbarProbe.receivedUse();
    }
}
