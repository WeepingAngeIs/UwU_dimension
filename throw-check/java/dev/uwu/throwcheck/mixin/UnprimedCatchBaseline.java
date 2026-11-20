package dev.uwu.throwcheck.mixin;

import dev.uwu.dimension.RelicCatchRecovery;
import dev.uwu.throwcheck.ThrowProbe;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Emulates the former return speed and vanilla equip recharge for marked baseline trials. */
@Mixin(value = RelicCatchRecovery.class, remap = false)
abstract class UnprimedCatchBaseline {
    @Inject(method = "supports", at = @At("HEAD"), cancellable = true)
    private static void throwCheck$unprimed(ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (ThrowProbe.disableCatchMechanics(stack)) result.setReturnValue(false);
    }
}
