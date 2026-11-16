package dev.uwu.test.mixin;

import dev.uwu.test.RelicPortraits;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe cues after the real client has passed the packet to its particle provider. */
@Mixin(ClientPacketListener.class)
public abstract class CupidCueMixin {
    @Inject(method="handleSystemChat",at=@At("TAIL"))
    private void wishMessage(net.minecraft.network.protocol.game.ClientboundSystemChatPacket packet,CallbackInfo ci){
        dev.uwu.test.NetworkWishClient.message(packet.content().getString());
    }
    @Inject(method="handleParticleEvent",at=@At("TAIL"))
    private void received(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        RelicPortraits.received(packet);
        dev.uwu.test.NetworkCupid.received(packet);
    }
    @Inject(method="handleSoundEvent",at=@At("TAIL"))
    private void sound(net.minecraft.network.protocol.game.ClientboundSoundPacket packet,CallbackInfo ci){
        dev.uwu.test.NetworkCupid.sound(packet);
    }
}
