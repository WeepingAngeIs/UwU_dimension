package dev.uwu.dimension.mixin;

import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.GuardianTargeting;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SwellGoal caches a target; clearing Mob.target alone cannot stop that cached fuse. */
@Mixin(SwellGoal.class)
public abstract class GuardianCreeperSwellMixin implements GuardianTargeting.GuardianSwellTarget {
    @Shadow @Final private Creeper creeper;
    @Shadow @Nullable private LivingEntity target;

    @Override public boolean uwu$hasGuardianTarget() {
        return target instanceof BlossomWarden;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void uwu$stopGuardianFuse(CallbackInfo ci) {
        if (creeper.level().isClientSide || !(target instanceof BlossomWarden)
                || GuardianTargeting.creeperAggroOn(creeper.level())) return;
        target = null;
        if (!creeper.isIgnited()) creeper.setSwellDir(-1);
        ci.cancel();
    }
}
