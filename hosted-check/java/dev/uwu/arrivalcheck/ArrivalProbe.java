package dev.uwu.arrivalcheck;

import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.ReefLife;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("uwu_arrival_probe")
public final class ArrivalProbe {
    private FakePlayer visitor;
    private ChunkPos entrance;
    private int readyTick = -1;
    private long observedHeap;
    private boolean finished;

    public ArrivalProbe() {
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (finished || server.getTickCount() < 15) return;
        try {
            var level = server.getLevel(ReefLife.REACH);
            if (level == null) throw new AssertionError("Missing Sakura Reach");
            var runtime = Runtime.getRuntime();
            observedHeap = Math.max(observedHeap, runtime.totalMemory() - runtime.freeMemory());
            if (visitor == null) {
                compareHeights(level);
                visitor = FakePlayerFactory.getMinecraft(level);
                visitor.setPos(48320.5, 100, -64447.5);
            }
            if (entrance == null) {
                // Let the normal game clock reach the precise tick that runs the fish spawn search.
                if ((level.getGameTime() + visitor.getId()) % 160 != 0) return;
                checkUnloadedReefTick(level);
                entrance = new ChunkPos(GroveSites.arrival(level));
                level.getChunkSource().addRegionTicket(TicketType.FORCED, entrance, 2, entrance);
                System.out.println("Requesting Sakura entrance terrain at " + entrance);
                return;
            }
            // Reading only completed chunks keeps the main thread free to advance world generation.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (level.getChunkSource().getChunkNow(entrance.x + dx, entrance.z + dz) == null) {
                        if (readyTick >= 0) throw new AssertionError("Entrance chunk vanished while ticketed");
                        return;
                    }
                }
            }
            if (readyTick < 0) {
                verifyTerrain(level);
                readyTick = server.getTickCount();
            }
            int ticks = server.getTickCount() - readyTick;
            if (ticks < 60) return;
            verifyTerrain(level);
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, entrance, 2, entrance);
            System.out.println("ARRIVAL TERRAIN AND TICKS PASS fullChunks=9 ticks=" + ticks
                    + " maximumSampledHeapMiB=" + (observedHeap >> 20)
                    + " heapLimitMiB=" + (runtime.maxMemory() >> 20));
            finished = true;
            server.halt(false);
        } catch (Throwable failure) {
            finished = true;
            System.err.println("ARRIVAL CHECK FAILED");
            failure.printStackTrace();
            server.halt(false);
        }
    }

    private static void compareHeights(ServerLevel level) throws ReflectiveOperationException {
        Method height = Class.forName("dev.uwu.dimension.ForestHeights")
                .getDeclaredMethod("height", ServerLevel.class, int.class, int.class);
        height.setAccessible(true);
        var chunks = level.getChunkSource();
        int before = chunks.getLoadedChunksCount();
        Set<ChunkPos> unloaded = new HashSet<>();
        int columns = 0;
        int[][] origins = {{0, 0}, {-16, 16}, {16, -16}, {-4, -4},
                {-560, -720}, {4096, -8192}, {-7312, 9468}};
        for (int[] origin : origins) {
            for (int dx = -1; dx <= 4; dx++) {
                for (int dz = -1; dz <= 4; dz++) {
                    int x = origin[0] + dx, z = origin[1] + dz;
                    var chunk = new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
                    if (!chunks.hasChunk(chunk.x, chunk.z)) unloaded.add(chunk);
                    int expected = chunks.getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                            level, chunks.randomState()) - 1;
                    int actual = (int) height.invoke(null, level, x, z);
                    if (actual != expected) {
                        throw new AssertionError("Height " + x + "," + z + ": " + actual + " vs vanilla " + expected);
                    }
                    columns++;
                }
            }
        }
        if (unloaded.isEmpty() || chunks.getLoadedChunksCount() != before) {
            throw new AssertionError("Height comparison lacked unloaded terrain or changed the chunk count");
        }
        for (var pos : unloaded) {
            if (chunks.hasChunk(pos.x, pos.z)) throw new AssertionError("Survey loaded " + pos);
        }
        System.out.println("ARRIVAL HEIGHT PARITY PASS columns=" + columns);
    }

    private void checkUnloadedReefTick(ServerLevel level) throws ReflectiveOperationException {
        if (visitor.isSpectator() || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            throw new AssertionError("Ambient spawns disabled in test");
        }
        var center = visitor.chunkPosition();
        requireUnloaded(level, center);
        var chunks = level.getChunkSource();
        int before = chunks.getLoadedChunksCount();
        // An empty search consumes eight angular and radial samples. Compare the visitor's actual RNG state.
        var random = visitor.getRandom();
        random.setSeed(8192L);
        for (int i = 0; i < 8; i++) {
            random.nextDouble();
            random.nextInt(23);
        }
        int expected = random.nextInt();
        random.setSeed(8192L);
        var hook = ReefLife.class.getDeclaredMethod("waterLife", PlayerTickEvent.Post.class);
        hook.setAccessible(true);
        long start = System.nanoTime();
        hook.invoke(null, new PlayerTickEvent.Post(visitor));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        if (random.nextInt() != expected || elapsedMs >= 2000 || chunks.getLoadedChunksCount() != before) {
            throw new AssertionError("Unloaded reef tick skipped probes, blocked, or added chunks: " + elapsedMs + " ms");
        }
        requireUnloaded(level, center);
        System.out.println("ARRIVAL UNLOADED REEF TICK PASS probes=8 elapsedMs=" + elapsedMs);
    }

    private static void requireUnloaded(ServerLevel level, ChunkPos center) {
        for (int x = center.x - 3; x <= center.x + 3; x++) {
            for (int z = center.z - 3; z <= center.z + 3; z++) {
                if (level.getChunkSource().hasChunk(x, z)) throw new AssertionError("Loaded reef probe chunk " + x + "," + z);
            }
        }
    }

    private void verifyTerrain(ServerLevel level) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                var chunk = level.getChunkSource().getChunkNow(entrance.x + dx, entrance.z + dz);
                if (chunk == null) throw new AssertionError("Missing completed entrance chunk");
                int solid = 0;
                for (int x = 2; x < 16; x += 4) {
                    for (int z = 2; z < 16; z += 4) {
                        int y = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                        var pos = new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                        if (y > level.getMinBuildHeight() + 8 && chunk.getBlockState(pos).blocksMotion()) solid++;
                    }
                }
                if (solid < 8) throw new AssertionError("Insufficient solid terrain in " + chunk.getPos() + ": " + solid);
            }
        }
    }
}
