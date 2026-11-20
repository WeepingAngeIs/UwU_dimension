package dev.uwu.dimension.traversal;

import dev.uwu.dimension.traversal.client.TraversalClientSession;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class TraversalIntegration {
    private static final class ServerState {
        final TraversalRuntime runtime = new TraversalRuntime();
        final TraversalInputSessions input = new TraversalInputSessions(runtime);
    }

    private static final Map<MinecraftServer, ServerState> SERVERS = new ConcurrentHashMap<>();

    private TraversalIntegration() {}

    public static void register(IEventBus modBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TraversalClientSession.register(modBus, server -> state(server).input);
        } else {
            modBus.addListener((RegisterPayloadHandlersEvent event) -> TraversalInputRegistration.register(
                    event, server -> state(server).input, effect -> {}, (listener, lease) -> {}));
        }
        NeoForge.EVENT_BUS.addListener(TraversalIntegration::tick);
        NeoForge.EVENT_BUS.addListener(TraversalIntegration::tracking);
        NeoForge.EVENT_BUS.addListener(TraversalIntegration::unload);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> SERVERS.remove(event.getServer()));
    }

    private static ServerState state(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, ignored -> new ServerState());
    }

    private static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ServerState state = state(level.getServer());
            state.input.tick(level);
            state.runtime.tick(level);
        }
    }

    private static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer subject) {
            ServerState state = SERVERS.get(observer.serverLevel().getServer());
            if (state != null) state.runtime.startTracking(observer, subject);
        }
    }

    public static void forget(ServerLevel level, ServerPlayer player) {
        ServerState state = SERVERS.get(level.getServer());
        if (state != null) {
            state.input.forget(level, player);
            state.runtime.forget(level, player);
        }
    }

    private static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ServerState state = SERVERS.get(level.getServer());
            if (state != null) {
                state.input.unload(level);
                state.runtime.unload(level);
            }
        }
    }
}
