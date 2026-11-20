package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.uwu.dimension.traversal.client.TraversalWrist;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ModelPart.class)
abstract class TraversalArmPartMixin {
    @WrapMethod(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V")
    private void uwu$traceArm(PoseStack pose,VertexConsumer out,int light,int overlay,int color,Operation<Void> original) {
        boolean arm=TraversalWrist.enter((ModelPart)(Object)this);
        try { original.call(pose,out,light,overlay,color); } finally { if(arm) TraversalWrist.leave(); }
    }
}
