package dev.uwu.dimension.lush.client;

import com.google.common.collect.MapMaker;
import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.lush.GeodeMarks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/** Client-thread publication of chunk marks for read-only use by section mesh workers. */
@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class GeodeClientIndex {
    // Weak keys use identity. Snapshots must not keep their owning level alive through a chunk.
    private static final ConcurrentMap<ClientLevel, WorldView> WORLDS = new MapMaker().weakKeys().makeMap();
    private static final int[] EMPTY = new int[0];

    private record Snapshot(WeakReference<LevelChunk> owner, GeodeMarks marks, int[] encoded) {}

    private static final class WorldView {
        // The client thread copies this sparse map before a change. Published maps never mutate.
        volatile Long2ObjectOpenHashMap<Snapshot> chunks = new Long2ObjectOpenHashMap<>();
        int ticksSinceSweep;
    }

    private GeodeClientIndex() {}

    public static @Nullable String theme(ClientLevel owner, BlockPos pos, BlockState state) {
        WorldView world = WORLDS.get(owner);
        if (world == null) return null;
        Snapshot snapshot = world.chunks.get(ChunkPos.asLong(pos));
        return snapshot == null || snapshot.owner().get() == null ? null : snapshot.marks().theme(pos, state);
    }

    /** Called on the client thread with the complete, freshly decoded chunk attachment. */
    public static void accept(LevelChunk chunk, GeodeMarks incoming) {
        if (!(chunk.getLevel() instanceof ClientLevel level) || !level.dimension().equals(ReefLife.REACH)) return;
        ChunkPos pos = chunk.getPos();
        if (Minecraft.getInstance().level != level || loaded(level, pos.x, pos.z) != chunk) return;
        WorldView world = WORLDS.get(level);
        if (world == null) {
            if (incoming.isEmpty()) return;
            world = new WorldView();
            WORLDS.put(level, world);
        }
        long key = pos.toLong();
        Snapshot old = world.chunks.get(key);
        int[] entries = incoming.encoded();
        if (old != null && old.owner().get() == chunk && Arrays.equals(old.encoded(), entries)) return;
        Snapshot next = entries.length == 0 ? null : new Snapshot(new WeakReference<>(chunk), incoming, entries);
        replace(level, world, key, next);
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        ChunkPos pos = chunk.getPos();
        WorldView world = WORLDS.get(level);
        if (world == null || loaded(level, pos.x, pos.z) != chunk) return;
        // A full chunk packet may reuse a LevelChunk. Its Load event precedes the separate
        // initial attachment packet. A newly unmarked chunk receives no such packet.
        replace(level, world, pos.toLong(), null);
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ClientLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        WorldView world = WORLDS.get(level);
        if (world == null) return;
        long key = chunk.getPos().toLong();
        Snapshot snapshot = world.chunks.get(key);
        if (snapshot != null && snapshot.owner().get() == chunk) replace(level, world, key, null);
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) WORLDS.remove(level);
    }

    @SubscribeEvent
    public static void afterLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        WorldView world = WORLDS.get(level);
        if (world == null || ++world.ticksSinceSweep < 20) return;
        world.ticksSinceSweep = 0;
        // Resizing the client chunk cache or reusing a ring slot can evict without Unload.
        // Check only chunks that have geode marks, without requesting any new chunk.
        var current = world.chunks;
        Long2ObjectOpenHashMap<Snapshot> kept = null;
        for (var it = current.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            long key = entry.getLongKey();
            LevelChunk owner = entry.getValue().owner().get();
            if (owner != null && loaded(level, ChunkPos.getX(key), ChunkPos.getZ(key)) == owner) continue;
            if (kept == null) kept = current.clone();
            kept.remove(key);
        }
        if (kept == null) return;
        world.chunks = kept;
        Minecraft client = Minecraft.getInstance();
        if (client.level != level) return;
        for (var it = current.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            long key = entry.getLongKey();
            if (!kept.containsKey(key) && loaded(level, ChunkPos.getX(key), ChunkPos.getZ(key)) != null) {
                dirtyChanges(client, level, key, entry.getValue().encoded(), EMPTY);
            }
        }
    }

    private static @Nullable LevelChunk loaded(ClientLevel level, int x, int z) {
        return level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
    }

    private static void replace(ClientLevel level, WorldView world, long key, @Nullable Snapshot next) {
        var current = world.chunks;
        Snapshot old = current.get(key);
        if (old == null && next == null) return;
        var copy = current.clone();
        if (next == null) copy.remove(key);
        else copy.put(key, next);
        world.chunks = copy;
        // The index must be visible before the renderer starts a replacement mesh.
        Minecraft client = Minecraft.getInstance();
        if (client.level == level) {
            dirtyChanges(client, level, key, old == null ? EMPTY : old.encoded(), next == null ? EMPTY : next.encoded());
        }
    }

    private static void dirtyChanges(Minecraft client, ClientLevel level, long chunk, int[] before, int[] after) {
        int i = 0, j = 0, previousSection = Integer.MIN_VALUE;
        while (i < before.length || j < after.length) {
            int changed;
            if (j == after.length || (i < before.length && before[i] < after[j])) {
                changed = before[i++];
            } else if (i == before.length || after[j] < before[i]) {
                changed = after[j++];
            } else {
                i++;
                j++;
                continue;
            }
            // Signed y above five datum bits and eight local x/z bits; four more give section y.
            int sectionY = changed >> 17;
            if (sectionY != previousSection && sectionY >= level.getMinSection() && sectionY < level.getMaxSection()) {
                client.levelRenderer.setSectionDirty(ChunkPos.getX(chunk), sectionY, ChunkPos.getZ(chunk));
            }
            previousSection = sectionY;
        }
    }
}
