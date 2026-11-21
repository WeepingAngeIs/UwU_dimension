package dev.uwu.dimension.mixin;

import dev.uwu.dimension.RevivalBlossomClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class RevivalBlossomPositionMixin {
    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void uwu$resetCompanionAfterLocalMove(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player != null) RevivalBlossomClient.resetCompanionMotion(player, 0);
    }

    @Inject(method = "handleTeleportEntity", at = @At("TAIL"))
    private void uwu$resetCompanionAfterRemoteRelocation(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.level.getEntity(packet.getId()) instanceof Player player
                && player != client.player) {
            RevivalBlossomClient.resetCompanionAfterRemoteRelocation(player, packet.getX(), packet.getY(), packet.getZ());
        }
    }
}
