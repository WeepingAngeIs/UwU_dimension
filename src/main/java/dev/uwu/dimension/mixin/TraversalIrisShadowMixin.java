package dev.uwu.dimension.mixin;

import dev.uwu.dimension.traversal.client.TraversalRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.irisshaders.iris.shadows.ShadowRenderer",remap=false)
abstract class TraversalIrisShadowMixin {
    @Inject(method="renderShadows",at=@At("HEAD"),require=0)
    private void uwu$enter(CallbackInfo ci) { TraversalRenderPass.shadow(true); }
    @Inject(method="renderShadows",at=@At("RETURN"),require=0)
    private void uwu$leave(CallbackInfo ci) { TraversalRenderPass.shadow(false); }
}
