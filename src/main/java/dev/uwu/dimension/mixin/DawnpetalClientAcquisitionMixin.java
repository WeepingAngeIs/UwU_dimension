package dev.uwu.dimension.mixin;

import dev.uwu.dimension.DawnpetalTargetSelection;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// An INVOKE injector into a merged method must have higher priority than its author (BC uses 1000).
@Mixin(value = Minecraft.class, priority = 1100)
abstract class DawnpetalClientAcquisitionMixin {
    // Only performAttack performs the actual native strike. BC's HUD uses the same target finder separately.
    @Dynamic("Better Combat 2.4.0 merges performAttack into Minecraft.")
    @ModifyArg(method = "performAttack()V", at = @At(value = "INVOKE",
        target = "Lnet/bettercombat/client/collision/TargetFinder;findAttackTargets(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/bettercombat/api/WeaponAttributes$Attack;D)Ljava/util/List;"),
        index = 3, remap = false, require = 0)
    private double uwu$dawnpetalAcquisition(double range) {
        return DawnpetalTargetSelection.range((Minecraft)(Object)this, range);
    }
}
