package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uwu.dimension.DawnpetalAbilityClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(Minecraft.class)
abstract class DawnpetalAbilityKeyMixin {
    @WrapOperation(method = "handleKeybinds",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyHotbarSlots:[Lnet/minecraft/client/KeyMapping;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z", ordinal = 0))
    private boolean uwu$abilitySelection(KeyMapping mapping, Operation<Boolean> original) {
        boolean pressed = original.call(mapping);
        return pressed && !DawnpetalAbilityClient.handleHotbar((Minecraft)(Object)this, mapping);
    }

    // The first call is a queued mapped press; the second call is the held-key auto-repeat.
    @WrapOperation(method = "handleKeybinds",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V", ordinal = 0))
    private void uwu$explicitUse(Minecraft instance, Operation<Void> vanilla) {
        DawnpetalAbilityClient.withQueuedUse(() -> vanilla.call(instance));
    }
}
