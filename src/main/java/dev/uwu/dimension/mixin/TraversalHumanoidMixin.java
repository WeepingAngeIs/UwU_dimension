package dev.uwu.dimension.mixin;

import dev.uwu.dimension.traversal.client.TraversalWrist;
import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
abstract class TraversalHumanoidMixin {
    @Inject(method="<init>(Lnet/minecraft/client/model/geom/ModelPart;Ljava/util/function/Function;)V",at=@At("RETURN"))
    private void uwu$registerArmMeshes(CallbackInfo ci) {
        var model=(HumanoidModel<?>)(Object)this;
        TraversalWrist.register(model.rightArm,model.leftArm);
    }
}
