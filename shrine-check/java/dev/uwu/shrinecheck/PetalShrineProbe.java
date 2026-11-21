package dev.uwu.shrinecheck;

import static dev.uwu.shrinecheck.ShrineGate.require;

import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.CupidShrine;
import dev.uwu.dimension.CupidShrineVisits;
import dev.uwu.dimension.PetalShrinePlacement;
import dev.uwu.dimension.ReefLife;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Drives native Sakura generation, first load, and unload/reload of a real Petal gate. */
public final class PetalShrineProbe {
    private static final int SEARCH = 18;
    private static final long WAIT_TICKS = 1000;
    private static final int ENTITY_TICKET = 2;

    private enum Order { SHOULDER, VILLAGE, LEGACY }
    private enum Phase { PLAN, FIRST_CHUNK, GATE_CHUNKS, RUNTIME, UNLOAD, RELOAD, DONE }
    private record Load(int sequence, boolean fresh) {}

    private final Order order = Order.valueOf(System.getProperty("uwu.check.shrineOrder", "shoulder")
            .toUpperCase(Locale.ROOT));
    private final AtomicInteger nextLoad = new AtomicInteger();
    private final Map<ChunkPos, Load> firstLoads = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Load> latestLoads = new ConcurrentHashMap<>();
    private final Set<ChunkPos> unloaded = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> loadedTickets = new LinkedHashSet<>();
    private Phase phase = Phase.PLAN;
    private ServerLevel level;
    private BlossomVillage.Plan petal, dusk;
    private PetalShrinePlacement.Site site;
    private ChunkPos plazaChunk, firstChunk;
    private long phaseStarted;
    private int originalLoad;
    private Method nearest;
    private CupidShrineRuntimeProbe runtime;

    public PetalShrineProbe() {
        NeoForge.EVENT_BUS.addListener(this::recordLoad);
        NeoForge.EVENT_BUS.addListener(this::recordUnload);
    }

    public boolean tick(MinecraftServer server) throws ReflectiveOperationException {
        switch (phase) {
            case PLAN -> prepare(server);
            case FIRST_CHUNK -> {
                if (!loaded(firstChunk)) {
                    await("first requested Petal chunk " + firstChunk);
                    return false;
                }
                if (order != Order.LEGACY) {
                    var first = firstLoads.get(firstChunk);
                    require(first != null && first.fresh(), "The fresh fixture needs a fresh disposable world");
                    ChunkPos other = order == Order.SHOULDER ? plazaChunk : site.owner();
                    require(!firstLoads.containsKey(other),
                            "Both planned chunks loaded before the second explicit request: " + firstChunk + " / " + other);
                    request(other);
                } else {
                    var old = firstLoads.get(firstChunk);
                    require(old != null && !old.fresh(), "The old setup road did not load from the previous save");
                    ShrineGate.verifyRoad(level, petal, ShrineGate.road(petal, petal.road().length - 1, 0));
                }
                for (var chunk : columnsAround(ShrineGate.center(petal))) request(chunk);
                if (dusk != null) for (var chunk : columnsAround(ShrineGate.center(dusk))) request(chunk);
                next(Phase.GATE_CHUNKS);
            }
            case GATE_CHUNKS -> {
                if (!loadedTickets.stream().allMatch(this::loaded)) {
                    await("native gate area chunks");
                    return false;
                }
                ShrineGate.verify(level, petal);
                var petals = markersNear(ShrineGate.center(petal));
                if (order == Order.LEGACY) {
                    var apron = petal.site().pos(ShrineGate.localX(petal) - 5, 0, ShrineGate.localZ(petal));
                    var load = firstLoads.get(new ChunkPos(apron));
                    require(load != null && load.fresh(), "The legacy run did not exercise a genuinely new west shoulder");
                    require(petals.isEmpty() && PetalShrinePlacement.select(level, petal).isEmpty(),
                            "Opening fresh terrain by an old Petal settlement added a shrine: " + petals);
                    releaseAll();
                    System.out.println("PETAL SHRINE LEGACY WORLD PASS old eastern road, newly generated west apron, no retrofit");
                    next(Phase.DONE);
                    return true;
                }
                ShrineGate.verify(level, dusk);
                require(PetalShrinePlacement.select(level, dusk).isEmpty() && markersNear(ShrineGate.center(dusk)).isEmpty(),
                        "Dusk generated a shrine at its native entrance");
                require(petals.equals(List.of(site.anchor())),
                        "Expected one shrine near the Petal gate, found " + petals);
                ChunkPos other = order == Order.SHOULDER ? plazaChunk : site.owner();
                var first = firstLoads.get(firstChunk);
                var second = firstLoads.get(other);
                require(second != null && second.fresh() && first.sequence() < second.sequence(),
                        "Native full chunk load order was not witnessed");
                verifyMarker();
                originalLoad = firstLoads.get(site.owner()).sequence();
                nearest = CupidShrineVisits.class.getDeclaredMethod("closest", Level.class, Vec3.class,
                        int.class, double.class, double.class);
                nearest.setAccessible(true);
                level.getChunkSource().addRegionTicket(TicketType.FORCED, site.owner(), ENTITY_TICKET, site.owner(), true);
                next(Phase.RUNTIME);
            }
            case RUNTIME -> {
                if (!level.isPositionEntityTicking(site.anchor())) {
                    await("entity ticking at the real Shrine owner");
                    return false;
                }
                if (runtime == null) runtime = new CupidShrineRuntimeProbe(level, site.anchor());
                if (!runtime.tick()) return false;
                require(site.anchor().equals(nearestMarker()), "Runtime check did not leave the natural shrine registered");
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, site.owner(), ENTITY_TICKET, site.owner(), true);
                releaseAll();
                next(Phase.UNLOAD);
            }
            case UNLOAD -> {
                if (!unloaded.contains(site.owner()) || loaded(site.owner()) || nearestMarker() != null) {
                    await("actual shrine chunk and its nearest-marker index to unload");
                    return false;
                }
                request(site.owner());
                next(Phase.RELOAD);
            }
            case RELOAD -> {
                Load reload = latestLoads.get(site.owner());
                if (!loaded(site.owner()) || reload.sequence() <= originalLoad || nearestMarker() == null) {
                    await("native shrine chunk reload and block entity registration");
                    return false;
                }
                require(!reload.fresh(), "Reload regenerated the shrine chunk");
                verifyMarker();
                require(site.anchor().equals(nearestMarker()), "Reload misregistered the shrine's stored center");
                require(PetalShrinePlacement.select(level, petal).orElseThrow().equals(site),
                        "The frozen new-generation plan moved after the owner chunk was saved");
                releaseAll();
                next(Phase.DONE);
                System.out.println("PETAL SHRINE SERVER PASS order=" + order.name().toLowerCase(Locale.ROOT)
                        + " anchor=" + site.anchor() + " facing=" + site.inward() + " gate=" + ShrineGate.center(petal)
                        + ", one native Petal marker, clear road, no Dusk marker, runtime and native reload");
            }
            case DONE -> { return true; }
        }
        return phase == Phase.DONE;
    }

    private void prepare(MinecraftServer server) {
        level = server.getLevel(ReefLife.REACH);
        require(level != null, "Sakura Reach did not load");
        petal = BlossomVillage.first(level, false);
        var planned = PetalShrinePlacement.select(level, petal);
        if (order == Order.LEGACY) {
            require(planned.isEmpty(), "Frozen upgrade snapshot did not reject a previously saved Petal settlement");
            firstChunk = ShrineGate.farEnd(petal);
        } else {
            site = planned.orElseThrow(() -> new AssertionError("Fresh Petal village has no shrine site on seed "
                    + level.getSeed() + " at " + ShrineGate.center(petal)));
            dusk = BlossomVillage.first(level, true);
            plazaChunk = new ChunkPos(petal.plaza());
            verifyPlanGeometry();
            require(!site.owner().equals(plazaChunk), "The fixture needs distinct plaza and shrine owner chunks");
            firstChunk = order == Order.SHOULDER ? site.owner() : plazaChunk;
        }
        require(!loaded(firstChunk), "A previous fixture loaded the first controlled-order chunk " + firstChunk);
        request(firstChunk);
        next(Phase.FIRST_CHUNK);
    }

    private void verifyPlanGeometry() {
        int westOfTip = petal.x() - 32 - site.localX();
        int offLane = Math.abs(ShrineGate.localZ(petal) - site.localZ());
        require(westOfTip >= 5 && westOfTip <= 14 && offLane >= 7 && offLane <= 13,
                "Shrine plan is not on an outside entrance shoulder: " + site);
        require(site.anchor().equals(petal.site().pos(site.localX(), site.anchor().getY(), site.localZ()))
                        && new ChunkPos(site.anchor()).equals(site.owner())
                        && site.inward() == ShrineGate.rotate(petal, Direction.EAST),
                "Local shrine anchor, rotated world center, owner chunk, or gate-facing direction disagrees: " + site);
        int radius = PetalShrinePlacement.RADIUS;
        require(new ChunkPos(site.anchor().offset(-radius, 0, -radius)).equals(site.owner())
                        && new ChunkPos(site.anchor().offset(radius, 0, radius)).equals(site.owner()),
                "The shrine pad is not contained by its one native owner chunk");
        require(PetalShrinePlacement.RADIUS == 4, "Shrine visit radius should be four blocks");
    }

    private void verifyMarker() {
        BlockPos anchor = site.anchor();
        var state = level.getBlockState(anchor);
        var marker = level.getBlockEntity(anchor);
        require(state == CupidShrine.state(site.inward()) && state.canSurvive(level, anchor),
                "Native shrine at " + anchor + " has wrong facing or no support: " + state);
        require(marker != null && marker.getBlockPos().equals(anchor)
                        && ShrineGate.uwuId("cupid_shrine").equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(marker.getType())),
                "Native generated shrine did not restore its center block entity at " + anchor);
    }

    private Set<ChunkPos> columnsAround(BlockPos center) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (int x = (center.getX() - SEARCH) >> 4; x <= (center.getX() + SEARCH) >> 4; ++x) {
            for (int z = (center.getZ() - SEARCH) >> 4; z <= (center.getZ() + SEARCH) >> 4; ++z) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    private List<BlockPos> markersNear(BlockPos center) {
        var found = new ArrayList<BlockPos>();
        for (ChunkPos chunkPos : columnsAround(center)) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            require(chunk != null, "A marker scan cannot witness missing chunk " + chunkPos);
            var sections = chunk.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; ++sectionIndex) {
                var section = sections[sectionIndex];
                if (!section.maybeHas(state -> state.is(CupidShrine.BLOCK.get()))) continue;
                int y0 = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
                for (int dx = 0; dx < 16; ++dx) {
                    int x = chunkPos.getMinBlockX() + dx;
                    if (Math.abs(x - center.getX()) > SEARCH) continue;
                    for (int dz = 0; dz < 16; ++dz) {
                        int z = chunkPos.getMinBlockZ() + dz;
                        if (Math.abs(z - center.getZ()) > SEARCH) continue;
                        for (int dy = 0; dy < 16; ++dy) {
                            if (section.getBlockState(dx, dy, dz).is(CupidShrine.BLOCK.get())) {
                                found.add(new BlockPos(x, y0 + dy, z));
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    private BlockPos nearestMarker() throws ReflectiveOperationException {
        return (BlockPos) nearest.invoke(null, level, Vec3.atBottomCenterOf(site.anchor()),
                PetalShrinePlacement.RADIUS, -.5, 1.25);
    }

    private boolean loaded(ChunkPos chunk) {
        return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null;
    }

    private void request(ChunkPos chunk) {
        if (loadedTickets.add(chunk)) {
            level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 0, chunk, true);
        }
    }

    private void releaseAll() {
        for (var chunk : loadedTickets) {
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 0, chunk, true);
        }
        loadedTickets.clear();
    }

    private void recordLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel world) || !world.dimension().equals(ReefLife.REACH)) return;
        ChunkPos chunk = event.getChunk().getPos();
        var load = new Load(nextLoad.incrementAndGet(), event.isNewChunk());
        firstLoads.putIfAbsent(chunk, load);
        latestLoads.put(chunk, load);
    }

    private void recordUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel world && world.dimension().equals(ReefLife.REACH)) {
            unloaded.add(event.getChunk().getPos());
        }
    }

    private void next(Phase next) {
        phase = next;
        phaseStarted = level.getGameTime();
    }

    private void await(String task) {
        require(level.getGameTime() - phaseStarted < WAIT_TICKS, "Timed out waiting for " + task);
    }
}
