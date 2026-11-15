package dev.uwu.test.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.uwu.test.NetworkCupid;
import net.minecraft.client.particle.SingleQuadParticle;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class CupidQuadWitnessMixin {
    @Inject(method="renderRotatedQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Quaternionf;FFFF)V",at=@At("HEAD"))
    private void heartwater$witness(VertexConsumer vertices,Quaternionf facing,float x,float y,float z,float partial,CallbackInfo ci) {
        if(NetworkCupid.ACTIVE) NetworkCupid.quad((SingleQuadParticle)(Object)this,x,y,z,partial);
    }
}
