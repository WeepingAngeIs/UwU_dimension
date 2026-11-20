package dev.uwu.dimension;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Short-lived protection for an already committed Revival Blossom revive. */
final class RevivalBloomProtection {
    private static final Map<ServerPlayer, Long> DEADLINES = new IdentityHashMap<>();

    private RevivalBloomProtection() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RevivalBloomProtection::blockDamage);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            if (event.getEntity() instanceof ServerPlayer player) isProtected(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                DEADLINES.remove(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone event) ->
                DEADLINES.remove(event.getOriginal()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) ->
                DEADLINES.remove(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) ->
                DEADLINES.keySet().removeIf(player -> player.getServer() == event.getServer()));
    }

    static void protectUntil(ServerPlayer player, long deadline) {
        if (deadline > gameTime(player)) DEADLINES.put(player, deadline);
    }

    static long gameTime(ServerPlayer player) {
        return player.serverLevel().getServer().overworld().getGameTime();
    }

    private static boolean isProtected(ServerPlayer player) {
        Long deadline = DEADLINES.get(player);
        if (deadline == null) return false;
        if (!player.isRemoved() && gameTime(player) < deadline) return true;
        DEADLINES.remove(player);
        return false;
    }

    private static void blockDamage(EntityInvulnerabilityCheckEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isProtected(player)) {
            event.setInvulnerable(true);
        }
    }
}
