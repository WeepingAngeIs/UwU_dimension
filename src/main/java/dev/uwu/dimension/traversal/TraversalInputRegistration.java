package dev.uwu.dimension.traversal;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The owner calls this once for traversal. Client callbacks are side-safe dispatchers supplied by that owner. */
public final class TraversalInputRegistration {
    private TraversalInputRegistration() {}

    public static void register(RegisterPayloadHandlersEvent event,
            Function<MinecraftServer, TraversalInputSessions> serverSessions,
            Consumer<TraversalPackets.Effect> clientEffect,
            BiConsumer<ICommonPacketListener, TraversalInputPacket> clientLease) {
        // The registrar's default handler thread is MAIN in 21.1.249.
        var registrar = event.registrar(TraversalPackets.WIRE_VERSION);
        registrar.playToServer(TraversalPackets.Request.TYPE, TraversalPackets.Request.CODEC, (request, context) -> {
            if (context.player() instanceof ServerPlayer player)
                serverSessions.apply(player.serverLevel().getServer()).receiveLegacy(player, request);
        });
        registrar.playToClient(TraversalPackets.Effect.TYPE, TraversalPackets.Effect.CODEC,
            (effect, context) -> clientEffect.accept(effect));
        registrar.optional().playBidirectional(TraversalInputPacket.TYPE, TraversalInputPacket.CODEC, (packet, context) -> {
            if (context.flow() == PacketFlow.CLIENTBOUND) {
                if (packet.command() == TraversalInputPacket.Command.LEASE)
                    clientLease.accept(context.listener(), packet);
            } else if (packet.command() != TraversalInputPacket.Command.LEASE
                    && context.player() instanceof ServerPlayer player) {
                serverSessions.apply(player.serverLevel().getServer()).receive(player, packet);
            }
        });
    }
}
