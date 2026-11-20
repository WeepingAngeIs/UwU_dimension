package dev.uwu.dimension;

import dev.uwu.dimension.client.RevivalLotusBurstClient;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** One visual cue and one shared deadline for a successful blossom revive. */
public final class RevivalBloom {
    public static final int DURATION_TICKS = 34;

    public record Cue(ResourceLocation dimension, UUID owner, long startTick, Vec3 origin,
                      float scale, float yaw) implements CustomPacketPayload {
        public static final Type<Cue> TYPE = new Type<>(UwUDimension.id("revival_bloom"));
        static final StreamCodec<RegistryFriendlyByteBuf, Cue> CODEC = StreamCodec.of(
                (buf, cue) -> {
                    buf.writeResourceLocation(cue.dimension);
                    buf.writeUUID(cue.owner);
                    buf.writeVarLong(cue.startTick);
                    buf.writeDouble(cue.origin.x);
                    buf.writeDouble(cue.origin.y);
                    buf.writeDouble(cue.origin.z);
                    buf.writeFloat(cue.scale);
                    buf.writeFloat(cue.yaw);
                },
                buf -> new Cue(buf.readResourceLocation(), buf.readUUID(), buf.readVarLong(),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        buf.readFloat(), buf.readFloat()));

        @Override public Type<Cue> type() { return TYPE; }
    }

    static void register(IEventBus bus) {
        RevivalBloomProtection.register();
        bus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").playToClient(
                Cue.TYPE, Cue.CODEC, (cue, context) -> RevivalLotusBurstClient.receive(
                        cue.dimension(), cue.owner(), cue.startTick(), cue.origin(), cue.scale(), cue.yaw())));
    }

    static Cue begin(ServerPlayer player, long now) {
        RevivalBloomProtection.protectUntil(player, now + DURATION_TICKS);
        return new Cue(player.level().dimension().location(), player.getUUID(), now,
                player.position(), player.getScale(), player.getYRot());
    }

    static void broadcast(ServerPlayer player, Cue cue) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, cue);
    }

    private RevivalBloom() {}
}
