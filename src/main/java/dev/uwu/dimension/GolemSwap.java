package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.IronGolem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Replaces newly spawned vanilla golems with the UwU Guardian in UwU realms. */
public final class GolemSwap {
    private static final Map<ServerLevel, Set<BlockPos>> PENDING = new IdentityHashMap<>();

    private GolemSwap() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GolemSwap::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(GolemSwap::onServerTick);
        NeoForge.EVENT_BUS.addListener(GolemSwap::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(GolemSwap::onServerStopping);
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk()) return;
        if (!(event.getEntity() instanceof IronGolem golem) || golem.isPlayerCreated()) return;
        if (!(event.getLevel() instanceof ServerLevel level) || !RealmResidents.applies(level)) return;

        // Joining can happen before its chunk is fully promoted. Defer all world access
        // until the next server tick to avoid chunk-loading deadlocks.
        PENDING.computeIfAbsent(level, ignored -> new LinkedHashSet<>())
                .add(golem.blockPosition().immutable());
        event.setCanceled(true);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        for (var entry : List.copyOf(PENDING.entrySet())) {
            ServerLevel level = entry.getKey();
            if (level.getServer() != event.getServer()) {
                PENDING.remove(level);
                continue;
            }

            Set<BlockPos> positions = entry.getValue();
            for (BlockPos position : List.copyOf(positions)) {
                if (!level.hasChunkAt(position)) continue;
                if (BlossomWarden.ensureVillageGuard(level, position) != null) {
                    positions.remove(position);
                }
            }
            if (positions.isEmpty()) PENDING.remove(level);
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level);
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
    }
}
