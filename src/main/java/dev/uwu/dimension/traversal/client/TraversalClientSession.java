package dev.uwu.dimension.traversal.client;

import dev.uwu.dimension.traversal.TraversalInputRegistration;
import dev.uwu.dimension.traversal.TraversalInputSessions;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class TraversalClientSession {
    private static ClientPacketListener connection;
    private static TraversalView view;

    private TraversalClientSession() {}

    public static void register(IEventBus modBus, Function<MinecraftServer, TraversalInputSessions> sessions) {
        modBus.addListener(TraversalControls::register);
        modBus.addListener((RegisterPayloadHandlersEvent event) -> TraversalInputRegistration.register(
                event, sessions, effect -> view().accept(effect), TraversalInputClient::accept));
        NeoForge.EVENT_BUS.addListener(TraversalClientSession::tick);
    }

    public static TraversalView view() {
        ClientPacketListener current = Minecraft.getInstance().getConnection();
        if (view == null || connection != current) {
            connection = current;
            view = new TraversalView();
        }
        return view;
    }

    private static void tick(ClientTickEvent.Post event) {
        TraversalInputClient.poll();
        if (Minecraft.getInstance().getConnection() == null) {
            connection = null;
            view = null;
        } else {
            view().tick();
        }
    }
}
