package dev.uwu.dimension.mixin;

import dev.uwu.dimension.RelicCatchRecovery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class RelicCatchRecoveryMixin extends LivingEntity {
    protected RelicCatchRecoveryMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void uwu$readyCaughtRelic(CallbackInfo callback) {
        Player player = (Player)(Object)this;
        if (!RelicCatchRecovery.consume(player)) return;
        float delay = player.getCurrentItemAttackStrengthDelay();
        if (Float.isFinite(delay) && delay > 0)
            attackStrengthTicker = Math.max(attackStrengthTicker, (int)Math.ceil(delay));
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void uwu$usePendingCatch(Entity target, CallbackInfo callback) {
        RelicCatchRecovery.clear((Player)(Object)this);
    }
}
