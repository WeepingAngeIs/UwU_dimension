package dev.uwu.lotusclientcheck.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.uwu.lotusclientcheck.CupidShrineClientProbe;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.uwu.dimension.CupidShrineRenderer$GroundCircle", remap = false)
abstract class ShrineCircleObserver {
    @Inject(method = "render", at = @At("HEAD"))
    private static void begin(RenderLevelStageEvent event, CallbackInfo ci) {
        CupidShrineClientProbe.beginCircle();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            + "addVertex(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static VertexConsumer submit(VertexConsumer sink, PoseStack.Pose pose, float x, float y, float z) {
        CupidShrineClientProbe.circleVertex(x, y, z);
        return sink.addVertex(pose, x, y, z);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void finish(RenderLevelStageEvent event, CallbackInfo ci) {
        CupidShrineClientProbe.finishCircle(event.getCamera());
    }
}
