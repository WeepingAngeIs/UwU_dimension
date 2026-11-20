package dev.uwu.dimension.mixin;

import dev.uwu.dimension.NativeRewardChestMenu;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class NativeRewardMenuPacketMixin {
    @Shadow public ServerPlayer player;

    // Vanilla normally executes an old-state click and reconciles the window afterward.
    @Inject(method = "handleContainerClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;suppressRemoteUpdates()V"),
            cancellable = true)
    private void uwudimension$rejectStaleRewardClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (player.containerMenu instanceof NativeRewardChestMenu menu && packet.getStateId() != menu.getStateId()) {
            menu.broadcastFullState();
            callback.cancel();
        }
    }
}
