package dev.uwu.dimension;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Loaded shrine positions on each side and the server's private visit interactions. */
@EventBusSubscriber(modid = "uwudimension")
public final class CupidShrineVisits {
    static final int RADIUS = 4;
    private static final String[] LINES = {
            "The pink circle warms the stone. You can rest here a little while.",
            "A little warmth for the road. Stay inside the ring while you catch your breath.",
            "The petals stir at your feet. Leave whenever you're ready."
    };
    // Integrated client and server have distinct Level keys and can attach markers on different threads.
    private static final Map<Level, Map<Long, Set<BlockPos>>> MARKERS = new WeakHashMap<>();
    private static final Map<UUID, Visit> VISITS = new HashMap<>();

    private CupidShrineVisits() {}

    static synchronized void loaded(Level level, BlockPos pos) {
        if (!level.dimension().equals(ReefLife.REACH)) return;
        MARKERS.computeIfAbsent(level, key -> new HashMap<>())
                .computeIfAbsent(ChunkPos.asLong(pos), key -> new HashSet<>()).add(pos.immutable());
    }

    static synchronized void unloaded(Level level, BlockPos pos) {
        var chunks = MARKERS.get(level);
        if (chunks == null) return;
        long key = ChunkPos.asLong(pos);
        var positions = chunks.get(key);
        if (positions != null && positions.remove(pos) && positions.isEmpty()) chunks.remove(key);
        if (chunks.isEmpty()) MARKERS.remove(level);
    }

    static synchronized BlockPos closest(Level level, Vec3 point, int radius, double minY, double maxY) {
        var chunks = MARKERS.get(level);
        if (chunks == null) return null;
        int minChunkX = Math.floorDiv((int) Math.floor(point.x - radius), 16);
        int maxChunkX = Math.floorDiv((int) Math.floor(point.x + radius), 16);
        int minChunkZ = Math.floorDiv((int) Math.floor(point.z - radius), 16);
        int maxChunkZ = Math.floorDiv((int) Math.floor(point.z + radius), 16);
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                var positions = chunks.get(ChunkPos.asLong(x, z));
                if (positions == null) continue;
                for (BlockPos marker : positions) {
                    double dy = point.y - marker.getY();
                    if (dy < minY || dy > maxY) continue;
                    double dx = point.x - (marker.getX() + .5);
                    double dz = point.z - (marker.getZ() + .5);
                    double horizontal = dx * dx + dz * dz;
                    if (horizontal > radius * radius) continue;
                    double distance = horizontal + dy * dy;
                    if (distance < bestDistance
                            || (distance == bestDistance && best != null && marker.compareTo(best) < 0)) {
                        best = marker;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void pulse(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ReefLife.isSettlementRealm(level)
                || level.getGameTime() % 100 != 0) return;
        var grace = CupidGrace.holder();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.hasEffect(MobEffects.REGENERATION)
                    || player.hasEffect(grace) || closest(level, player.position(), RADIUS, -.5, 1.25) == null) continue;
            player.addEffect(new MobEffectInstance(grace, 51, 0, false, false, true));
        }
    }

    static void read(ServerPlayer reader) {
        var now = reader.serverLevel().getGameTime();
        UUID id = reader.getUUID();
        var previous = VISITS.get(id);
        if (previous != null && now - previous.tick() < 40) return;
        int line = previous == null ? 0 : previous.next();
        reader.sendSystemMessage(Component.literal(LINES[line]));
        VISITS.put(id, new Visit(now, (line + 1) % LINES.length));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer reader) VISITS.remove(reader.getUUID());
    }

    @SubscribeEvent
    public static void changedWorld(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer reader) VISITS.remove(reader.getUUID());
    }

    @SubscribeEvent
    public static synchronized void levelClosed(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            MARKERS.remove(level);
            if (level instanceof ServerLevel server && ReefLife.isSettlementRealm(server)) VISITS.clear();
        }
    }

    private record Visit(long tick, int next) {}
}
