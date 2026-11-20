package dev.uwu.dimension.mixin;

import dev.uwu.dimension.GeneratedLootrRewards;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "noobanidus.mods.lootr.common.api.data.ILootrInfo", remap = false)
public interface GeneratedLootrLifecycleMixin {
    @Inject(method = {"canRefresh()Z", "canDecay()Z"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void uwu$keepGeneratedPersonalRewards(CallbackInfoReturnable<Boolean> callback) {
        if (GeneratedLootrRewards.suppressAutomaticReset(this)) callback.setReturnValue(false);
    }
}
