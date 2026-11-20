package dev.uwu.hotbarcheck.mixin;

import dev.uwu.dimension.ReturnbloomPrime;
import dev.uwu.hotbarcheck.HotbarProbe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ReturnbloomPrime.class, remap = false)
abstract class DirkTurnObserver {
    @Inject(method = "arm", at = @At("RETURN"))
    private static void hotbarCheck$observe(ServerPlayer player, ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue()) HotbarProbe.armedTurn();
    }
}
