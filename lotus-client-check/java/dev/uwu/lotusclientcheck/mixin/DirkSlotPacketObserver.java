package dev.uwu.lotusclientcheck.mixin;

import dev.uwu.lotusclientcheck.DirkCountdownProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class DirkSlotPacketObserver {
    @Inject(method = "handleContainerSetSlot", at = @At("HEAD"), cancellable = true)
    private void deferOneRealPacket(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (DirkCountdownProbe.deferPacket(packet)) ci.cancel();
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void observeInventorySync(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        DirkCountdownProbe.slotPacket(packet.getItem());
    }
}
