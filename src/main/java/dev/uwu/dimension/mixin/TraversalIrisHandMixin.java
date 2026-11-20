package dev.uwu.dimension.mixin;

import dev.uwu.dimension.traversal.client.TraversalRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.irisshaders.iris.pathways.HandRenderer",remap=false)
abstract class TraversalIrisHandMixin {
    @Inject(method="renderSolid",at=@At("HEAD"),require=0)
    private void uwu$enter(CallbackInfo ci) { TraversalRenderPass.shaderHand(true); }
    @Inject(method="renderSolid",at=@At("RETURN"),require=0)
    private void uwu$leave(CallbackInfo ci) { TraversalRenderPass.shaderHand(false); }
}
