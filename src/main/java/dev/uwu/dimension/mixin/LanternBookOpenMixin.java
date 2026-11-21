package dev.uwu.dimension.mixin;

import dev.uwu.dimension.LanternStorybookClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only the marked held book's server-authorized reader opening. */
@Mixin(ClientPacketListener.class)
abstract class LanternBookOpenMixin {
    @Inject(method = "handleOpenBook", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"),
            cancellable = true)
    private void uwu$liftMarkedBook(ClientboundOpenBookPacket packet, CallbackInfo callback) {
        // Vanilla has already rescheduled onto the client thread and checked the held book.
        // Do not intercept packet HEAD: the network thread must keep its native dispatch path.
        var player = Minecraft.getInstance().player;
        if (player != null && LanternStorybookClient.openHeld(player.getItemInHand(packet.getHand()), packet.getHand())) {
            callback.cancel();
        }
    }
}
