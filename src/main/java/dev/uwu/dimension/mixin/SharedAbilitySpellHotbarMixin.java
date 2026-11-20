package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uwu.dimension.SharedAbilityHotbar;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Spell Engine polls mapped keys independently of Minecraft's queued clicks. */
@Pseudo
@Mixin(targets = "net.spell_engine.client.input.SpellHotbar", remap = false)
abstract class SharedAbilitySpellHotbarMixin {
    @WrapOperation(method = "handleSlotsInternal", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean uwu$sharedWeaponKeys(KeyMapping key, Operation<Boolean> original) {
        return !SharedAbilityHotbar.ownsMappedKey(key) && original.call(key);
    }
}
