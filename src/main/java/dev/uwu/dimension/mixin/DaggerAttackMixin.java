package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uwu.dimension.DaggerBackstab;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Applies the bonus to the complete primary attack before ordinary armor and damage events. */
@Mixin(Player.class)
abstract class DaggerAttackMixin {
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
        return original.call(victim, source, amount);
    }
}
