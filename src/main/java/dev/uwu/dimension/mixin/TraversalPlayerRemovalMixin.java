package dev.uwu.dimension.mixin;

import dev.uwu.dimension.traversal.TraversalIntegration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class TraversalPlayerRemovalMixin {
    // Logout, dimension transfer and player replacement pass here before tracking is removed.
    @Inject(method = "removePlayerImmediately", at = @At("HEAD"))
    private void uwu$forgetTraversal(ServerPlayer player, Entity.RemovalReason reason, CallbackInfo callback) {
        TraversalIntegration.forget((ServerLevel)(Object)this, player);
    }
}
