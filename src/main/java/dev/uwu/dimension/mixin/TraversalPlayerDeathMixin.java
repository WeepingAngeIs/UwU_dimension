package dev.uwu.dimension.mixin;

import dev.uwu.dimension.traversal.TraversalIntegration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class TraversalPlayerDeathMixin {
    // TAIL selects the completed death. NeoForge's canceled death takes an earlier return.
    @Inject(method = "die", at = @At("TAIL"))
    private void uwu$forgetTraversalAfterDeath(DamageSource cause, CallbackInfo callback) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        TraversalIntegration.forget(player.serverLevel(), player);
    }
}
