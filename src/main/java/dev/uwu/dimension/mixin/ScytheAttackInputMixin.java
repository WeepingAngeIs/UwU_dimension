package dev.uwu.dimension.mixin;

import dev.uwu.dimension.ScytheClientInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the scythe's wave on the real attack key when combat replaces startAttack. */
@Mixin(Minecraft.class)
abstract class ScytheAttackInputMixin {
    @Inject(method="handleKeybinds", at=@At(value="INVOKE", target="Lnet/minecraft/client/Minecraft;startAttack()Z"))
    private void uwu$scytheAttackKey(CallbackInfo callback) {
        ScytheClientInput.requestWave();
    }
}
