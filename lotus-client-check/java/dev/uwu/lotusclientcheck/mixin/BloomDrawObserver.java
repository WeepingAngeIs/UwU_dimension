package dev.uwu.lotusclientcheck.mixin;

import dev.uwu.dimension.client.RevivalLotusBurstClient;
import dev.uwu.lotusclientcheck.LotusClientProbe;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RevivalLotusBurstClient.class, remap = false)
abstract class BloomDrawObserver {
    @Inject(method = "receive", at = @At("TAIL"))
    private static void observeAccepted(ResourceLocation dimension, UUID owner, long start, Vec3 origin,
            float scale, float yaw, CallbackInfo ci) {
        LotusClientProbe.accepted(owner, start, origin);
    }

    @ModifyArg(method = "part", index = 3, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(IIII)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static int observeSubmittedOpacity(int opacity) {
        LotusClientProbe.submitted(opacity);
        return opacity;
    }
}
