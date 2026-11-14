package dev.uwu.dimension.mixin;

import dev.uwu.dimension.GuardianTargeting;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class GuardianTargetMixin {
    @Shadow @Final protected GoalSelector targetSelector;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void uwu$giveGuardianTargetGoal(EntityType<? extends Mob> type, Level level, CallbackInfo ci) {
        if (level == null || level.isClientSide) return;
        GuardianTargeting.addGuardTargetGoal(this.targetSelector, (Mob) (Object) this);
    }
}
