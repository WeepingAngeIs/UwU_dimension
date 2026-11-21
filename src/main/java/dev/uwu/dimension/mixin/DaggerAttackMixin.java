package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uwu.dimension.DaggerBackstab;
import dev.uwu.dimension.ReturnbloomTurnBack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Applies the bonus to the complete primary attack before ordinary armor and damage events. */
@Mixin(Player.class)
abstract class DaggerAttackMixin {
    @WrapMethod(method = "attack")
    private void uwudimension$returnbloomSwing(Entity target, Operation<Void> original) {
        ReturnbloomTurnBack.vanilla((Player)(Object)this, () -> original.call(target));
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            ordinal = 0))
    private boolean uwudimension$backstab(Entity victim, DamageSource source, float amount,
                                         Operation<Boolean> original) {
        Player attacker = (Player) (Object) this;
        if (victim instanceof LivingEntity target && DaggerBackstab.isDagger(attacker)
                && DaggerBackstab.isBehind(target, attacker.position())) {
            amount *= 2.0F;
        }
        float damage = amount;
        return ReturnbloomTurnBack.damage(attacker, victim, source, () -> original.call(victim, source, damage));
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean uwudimension$returnbloomSweep(LivingEntity victim, DamageSource source, float damage,
                                                        Operation<Boolean> original) {
        return ReturnbloomTurnBack.damage((Player)(Object)this, victim, source, () -> original.call(victim, source, damage));
    }
}
