package dev.uwu.dimension.mixin;

import dev.uwu.dimension.WeaponPresentation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
abstract class WeaponCarryPoseMixin {
    @Inject(method="poseRightArm", at=@At("TAIL"))
    private void uwu$rightScythe(LivingEntity entity, CallbackInfo callback) {
        WeaponPresentation.idleScythe((HumanoidModel<?>)(Object)this, entity, HumanoidArm.RIGHT);
    }

    @Inject(method="poseLeftArm", at=@At("TAIL"))
    private void uwu$leftScythe(LivingEntity entity, CallbackInfo callback) {
        WeaponPresentation.idleScythe((HumanoidModel<?>)(Object)this, entity, HumanoidArm.LEFT);
    }

    @Inject(method="setupAttackAnimation", at=@At("TAIL"))
    private void uwu$scytheReap(LivingEntity entity, float age, CallbackInfo callback) {
        WeaponPresentation.swingScythe((HumanoidModel<?>)(Object)this, entity);
    }
}
