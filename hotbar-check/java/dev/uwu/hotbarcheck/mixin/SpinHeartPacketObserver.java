package dev.uwu.hotbarcheck.mixin;

import dev.uwu.hotbarcheck.HotbarProbe;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class SpinHeartPacketObserver {
    @Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/particles/ParticleOptions;ZDDDIDDDD)Z",
            at = @At("RETURN"))
    private void hotbarCheck$particleSent(ServerPlayer viewer, ParticleOptions particle, boolean overrideLimiter,
                                         double x, double y, double z, int count,
                                         double dx, double dy, double dz, double speed,
                                         CallbackInfoReturnable<Boolean> callback) {
        HotbarProbe.spinParticle(viewer, particle, x, y, z, count, dx, dy, dz, speed, callback.getReturnValueZ());
    }
}
