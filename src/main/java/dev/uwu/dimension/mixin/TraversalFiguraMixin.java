package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.uwu.dimension.traversal.client.TraversalWrist;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Figura 0.1.6 batches faces as closures. Mark the closure while its actual source part is known. */
@Pseudo
@Mixin(targets="org.figuramc.figura.model.rendering.ImmediateAvatarRenderer", remap=false)
abstract class TraversalFiguraMixin {
    @WrapOperation(method="renderPart",at=@At(value="INVOKE",target="Lorg/figuramc/figura/model/FiguraModelPart;pushVerticesImmediate(Lorg/figuramc/figura/model/rendering/ImmediateAvatarRenderer;[I)Z"),require=0)
    private boolean uwu$tagLimb(@Coerce Object part,@Coerce Object renderer,int[] limit,Operation<Boolean> original) {
        boolean arm=TraversalWrist.enterFigura(part,renderer);
        try { return original.call(part,renderer,limit); } finally { if(arm) TraversalWrist.leave(); }
    }

    @ModifyArg(method="pushToBuffer",at=@At(value="INVOKE",target="Lorg/figuramc/figura/model/rendering/ImmediateAvatarRenderer$VertexBuffer;getBufferFor(Lnet/minecraft/client/renderer/RenderType;ZLjava/util/function/Consumer;)V"),index=2,require=0)
    private Consumer<VertexConsumer> uwu$carryLimbTag(Consumer<VertexConsumer> submit) { return TraversalWrist.figuraSubmission(submit); }
}
