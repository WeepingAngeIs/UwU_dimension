package dev.uwu.dimension;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** A physical chakram can reap mature crop blocks along its outbound and return path. */
final class FarmChakram {
    private static final String VISITED = "uwu.farm_chakram_visited";
    private static final int MAX_VISITED = 64;
    private static final int MAX_PER_TICK = 8;

    private FarmChakram() {}

    static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof RelicFlight flight)
                || !(flight.level() instanceof ServerLevel level)
                || !(flight.getOwner() instanceof ServerPlayer player)
                || player.level() != level || !player.isAlive()
                || !(flight.getItem().getItem() instanceof CounterArsenal.CounterItem item)
                || !item.entry.shape().equals("chakram")
                || !RelicReturns.active(player, flight)
                || !player.getMainHandItem().isEmpty()) return;

        long[] visited = flight.getPersistentData().getLongArray(VISITED);
        if (visited.length >= MAX_VISITED) return;
        Vec3 start = new Vec3(flight.xo, flight.yo, flight.zo);
        Vec3 movement = flight.position().subtract(start);
        // No unbounded line scans or force-loaded chunks, even after a teleport.
        if (movement.lengthSqr() > 36) return;
        int steps = Math.min(12, Math.max(1, (int) Math.ceil(movement.length() * 2)));
        int attempted = 0;
        for (int step = 0; step <= steps && visited.length < MAX_VISITED && attempted < MAX_PER_TICK; step++) {
            BlockPos center = BlockPos.containing(start.add(movement.scale((double) step / steps)));
            for (int dy = -1; dy <= 0 && visited.length < MAX_VISITED && attempted < MAX_PER_TICK; dy++) {
                BlockPos position = center.offset(0, dy, 0);
                if (level.isOutsideBuildHeight(position) || !level.hasChunkAt(position)
                        || !SakuraAccess.mayInteract(player, position)) continue;
                var state = level.getBlockState(position);
                if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) continue;
                long key = position.asLong();
                boolean seen = false;
                for (long old : visited) if (old == key) { seen = true; break; }
                if (seen) continue;
                visited = Arrays.copyOf(visited, visited.length + 1);
                visited[visited.length - 1] = key;
                flight.getPersistentData().putLongArray(VISITED, visited);
                attempted++;
                // The ordinary player break path raises NeoForge protection and drop
                // events. Require an empty hand so another held tool is never damaged.
                if (player.gameMode.destroyBlock(position)
                        && !level.getBlockState(position).equals(state)) {
                    RelicReturns.damageHarvest(player, flight);
                    if (!RelicReturns.active(player, flight)) return;
                }
            }
        }
    }
}
