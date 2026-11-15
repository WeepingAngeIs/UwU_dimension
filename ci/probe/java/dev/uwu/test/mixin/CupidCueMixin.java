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
    @Inject(method="handleParticleEvent",at=@At("TAIL"))
    private void received(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        RelicPortraits.received(packet);
    }
}
