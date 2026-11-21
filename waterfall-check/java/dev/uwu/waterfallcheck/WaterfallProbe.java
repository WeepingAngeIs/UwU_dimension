package dev.uwu.waterfallcheck;

import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.ReefLife;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("uwu_waterfall_probe")
public final class WaterfallProbe {
    private static final int TICKET_RADIUS = 2;
    private static final int FLOW_TICKS = 60;
    private static WaterfallProbe active;

    private final List<Lane> lanes = new ArrayList<>();
    private final Map<ChunkPos, Set<BlockPos>> samples = new HashMap<>();
    private final Map<BlockPos, BlockState> generated = new HashMap<>();
    private final Set<BlockPos> heads = new HashSet<>();
    private final Set<BlockPos> liquidTicks = new HashSet<>();
    private final Map<BlockPos, String> passage = new HashMap<>();
    private final Set<BlockPos> galleryDeck = new HashSet<>();
    private ServerLevel level;
    private int requestedAt;
    private int readyAt = -1;
    private int rotation;
    private boolean finished;

    public WaterfallProbe() {
        active = this;
        NeoForge.EVENT_BUS.addListener(this::chunkLoaded);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    public static void observeWaterTick(ServerLevel level, BlockPos pos) {
        WaterfallProbe probe = active;
        if (probe != null && level == probe.level && probe.heads.contains(pos)) {
            probe.liquidTicks.add(pos.immutable());
        }
    }

    private void chunkLoaded(ChunkEvent.Load event) {
        if (!event.isNewChunk() || event.getLevel() != level) return;
        var chunk = event.getChunk();
        for (BlockPos pos : samples.getOrDefault(chunk.getPos(), Set.of())) {
            generated.putIfAbsent(pos, chunk.getBlockState(pos));
        }
    }

    private void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        int now = server.getTickCount();
        if (finished || now < 15) return;
        try {
            if (level == null) {
                level = server.getLevel(ReefLife.REACH);
                require(level != null, "Missing Sakura Reach");
                setup(now);
                return;
            }
            if (readyAt < 0) {
                Set<ChunkPos> missingChunks = new HashSet<>();
                for (ChunkPos chunk : samples.keySet()) {
                    if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) missingChunks.add(chunk);
                }
                if (!missingChunks.isEmpty()) {
                    require(now - requestedAt < 2400, "Chunks did not reach FULL: " + missingChunks);
                    return;
                }
                for (Set<BlockPos> chunkSamples : samples.values()) {
                    for (BlockPos pos : chunkSamples) {
                        require(generated.containsKey(pos), "Missing newly generated sample at " + pos);
                    }
                }
                verify(true);
                readyAt = now;
            }
            if (now - readyAt < FLOW_TICKS) return;
            Set<BlockPos> missed = new HashSet<>(heads);
            missed.removeAll(liquidTicks);
            require(missed.isEmpty(), "Native water tick did not run at " + missed);
            verify(false);
            int splits = 0;
            for (Lane lane : lanes) {
                if (!new ChunkPos(lane.source()).equals(new ChunkPos(lane.mouth()))) splits++;
            }
            System.out.println("HEARTWATER WATERFALL PASS paths=entrance,quiet_spring,hanging_shrine lanes="
                    + lanes.size() + " freshlyGeneratedChunks=" + samples.size() + " rotation=" + rotation
                    + " crossChunkLanes=" + splits + " nativeHeadTicks=" + liquidTicks.size()
                    + " elapsedTicks=" + (now - readyAt));
            finished = true;
            server.halt(false);
        } catch (Throwable failure) {
            finished = true;
            System.err.println("HEARTWATER WATERFALL CHECK FAILED");
            failure.printStackTrace();
            server.halt(false);
        }
    }

    private void setup(int now) {
        GroveSites.Site site = GroveSites.first(level);
        require(site.usable() && GroveSites.shrine(level, site), "Fixture needs the actual first grove and shrine");
        rotation = site.turns();
        int floor = site.entryY();
        int lip = site.lip();
        for (int lane = -1; lane <= 1; lane++) {
            addLane("entrance", site.pos(lane, floor + 8, lip), site.pos(lane, floor + 8, lip - 1),
                    site.pos(lane, floor, lip - 1), true);
            addLane("quiet spring", site.pos(-40, -37, 129 + lane), site.pos(-39, -37, 129 + lane),
                    site.pos(-39, -48, 129 + lane), false);
            addLane("hanging shrine", site.pos(56, 13, 108 + lane), site.pos(55, 13, 108 + lane),
                    site.pos(55, 0, 108 + lane), false);
        }
        for (int x : new int[] {-2, 2}) {
            for (int height = 1; height <= 2; height++) {
                for (int z = lip - 2; z <= lip - 1; z++) {
                    addPassage(site.pos(x, floor + height, z), "Entrance side aisle");
                }
            }
        }
        for (int z = 112; z <= 114; z++) {
            BlockPos deck = site.pos(48, 8, z);
            galleryDeck.add(deck);
            sample(deck);
            addPassage(deck.above(), "Shrine gallery");
        }
        requestedAt = now;
        for (ChunkPos chunk : samples.keySet()) {
            level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, TICKET_RADIUS, chunk, true);
        }
        System.out.println("Requesting new Heartwater waterfall chunks at " + site.x() + "," + site.z()
                + ": " + samples.keySet());
    }

    private void addLane(String name, BlockPos source, BlockPos mouth, BlockPos landing, boolean runnel) {
        lanes.add(new Lane(name, source, mouth, landing, runnel));
        sample(source);
        sample(source.below());
        sample(source.above());
        sample(source.offset(source.subtract(mouth)));
        heads.add(source);
        heads.add(mouth);
        for (int y = landing.getY(); y <= mouth.getY(); y++) {
            sample(mouth.atY(y));
        }
    }

    private void addPassage(BlockPos pos, String description) {
        passage.put(pos, description);
        sample(pos);
    }

    private void sample(BlockPos pos) {
        samples.computeIfAbsent(new ChunkPos(pos), ignored -> new HashSet<>()).add(pos);
    }

    private void verify(boolean initial) {
        for (Lane lane : lanes) {
            BlockPos source = lane.source();
            BlockPos mouth = lane.mouth();
            checkWater(lane, source, 0, initial);
            checkWater(lane, mouth, 1, initial);
            for (int y = mouth.getY() - 1; y > lane.landing().getY(); y--) {
                checkWater(lane, mouth.atY(y), 8, initial);
            }
            checkBank(lane, source.below(), initial);
            checkBank(lane, source.above(), initial);
            checkBank(lane, source.offset(source.subtract(mouth)), initial);
            if (lane.runnel()) {
                BlockState slab = state(lane.landing(), initial);
                require(slab.is(Blocks.SMOOTH_QUARTZ_SLAB) && slab.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                        && slab.getValue(SlabBlock.WATERLOGGED) && slab.getFluidState().isSource(),
                        problem(lane.name(), lane.landing(), "bottom waterlogged runnel slab", slab, initial));
            } else {
                checkWater(lane, lane.landing(), 0, initial);
            }
        }
        for (Map.Entry<BlockPos, String> entry : passage.entrySet()) {
            BlockState block = state(entry.getKey(), initial);
            require(!block.blocksMotion() && block.getFluidState().isEmpty(),
                    problem(entry.getValue(), entry.getKey(), "dry clearance", block, initial));
        }
        for (BlockPos pos : galleryDeck) {
            BlockState block = state(pos, initial);
            require(block.blocksMotion() && block.getFluidState().isEmpty(),
                    problem("Shrine gallery", pos, "dry intact deck", block, initial));
        }
    }

    private void checkWater(Lane lane, BlockPos pos, int depth, boolean initial) {
        BlockState block = state(pos, initial);
        require(block.is(Blocks.WATER) && block.getValue(LiquidBlock.LEVEL) == depth
                && block.getFluidState().is(FluidTags.WATER),
                problem(lane.name(), pos, "water level " + depth, block, initial));
    }

    private void checkBank(Lane lane, BlockPos pos, boolean initial) {
        BlockState block = state(pos, initial);
        require(block.blocksMotion() && block.getFluidState().isEmpty(),
                problem(lane.name(), pos, "dry solid source enclosure", block, initial));
    }

    private BlockState state(BlockPos pos, boolean initial) {
        if (initial) return generated.get(pos);
        ChunkPos chunk = new ChunkPos(pos);
        var ready = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        require(ready != null, "Sample chunk unloaded while ticketed: " + chunk);
        return ready.getBlockState(pos);
    }

    private static String problem(String path, BlockPos pos, String expected, BlockState actual, boolean initial) {
        return path + " at " + pos + " expected " + expected + ", saw " + actual
                + (initial ? " at chunk load" : " after native fluid ticks");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Lane(String name, BlockPos source, BlockPos mouth, BlockPos landing, boolean runnel) {}
}
