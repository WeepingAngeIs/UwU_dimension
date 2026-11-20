package dev.uwu.dimension.mixin;

import dev.uwu.dimension.traversal.client.TraversalWrist;
import net.minecraft.client.model.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class TraversalSleeveMixin {
    @Inject(method="<init>",at=@At("RETURN"))
    private void uwu$registerArmMeshes(CallbackInfo ci) {
        var model=(PlayerModel<?>)(Object)this;
        TraversalWrist.register(model.rightSleeve,model.leftSleeve);
    }
}
