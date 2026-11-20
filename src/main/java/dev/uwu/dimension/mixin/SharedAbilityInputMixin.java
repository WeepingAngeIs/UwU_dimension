package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uwu.dimension.SharedAbilityHotbar;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 1100)
abstract class SharedAbilityInputMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void uwu$abilitySelection(CallbackInfo callback) {
        SharedAbilityHotbar.handleHotbar((Minecraft)(Object)this);
    }

    // The first call consumes mapped presses. The second is held-key auto-repeat.
    @WrapOperation(method = "handleKeybinds",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V", ordinal = 0))
    private void uwu$explicitUse(Minecraft client, Operation<Void> vanilla) {
        SharedAbilityHotbar.withQueuedUse(() -> vanilla.call(client));
    }
}
