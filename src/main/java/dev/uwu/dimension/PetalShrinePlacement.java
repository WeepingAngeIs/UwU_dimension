package dev.uwu.dimension;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Reserves the entrance shoulder using seed terrain and the first-load upgrade boundary. */
public final class PetalShrinePlacement {
    public static final int RADIUS = 4;
    private static final int GATE_INDEX = 2;
    private static final int CLEARANCE = 2;
    private static final int MIN_WEST = RADIUS + 1;
    private static final int MAX_WEST = 14;
    private static final int MIN_SIDE = RADIUS + 3;
    private static final int MAX_SIDE = 13;
    private static final Map<ServerLevel, Map<Long, Optional<Site>>> SITES = new WeakHashMap<>();

    public record Site(int localX, int localZ, BlockPos anchor, Direction inward, ChunkPos owner) {}

    private PetalShrinePlacement() {}

    public static Optional<Site> select(ServerLevel level, BlossomVillage.Plan village) {
        if (!ReefLife.isSettlementRealm(level) || village.dusk()) return Optional.empty();
        var sites = cache(level);
        if (sites.size() > 4096) sites.clear();
        return sites.computeIfAbsent(ChunkPos.asLong(village.site().gx(), village.site().gz()),
                key -> survey(level, village));
    }

    private static synchronized Map<Long, Optional<Site>> cache(ServerLevel level) {
        return SITES.computeIfAbsent(level, key -> new ConcurrentHashMap<>());
    }

    private static Optional<Site> survey(ServerLevel level, BlossomVillage.Plan village) {
        var grove = village.site();
        int halfRoad = village.road().length / 2;
        int tip = village.x() - halfRoad;
        int gateZ = village.z() + village.bends()[GATE_INDEX];
        int gateY = village.road()[GATE_INDEX];
        // A pre-existing road, gate or house chunk opts the entire village out, even if this shoulder is new.
        if (!SettlementLayout.untouchedForCupidShrine(level, grove, tip - 3, village.z() - 30,
                village.x() + halfRoad + 3, village.z() + 30)) return Optional.empty();

        var moonveil = MoonveilTreehouse.plan(level, village);
        Direction facing = Direction.EAST;
        for (int turn = 0; turn < grove.turns(); turn++) facing = facing.getClockWise();
        int firstSide = (HeartwaterFeature.Valley.mix(village.x(), village.z(), level.getSeed()) & 1) == 0 ? -1 : 1;
        for (int west = MIN_WEST; west <= MAX_WEST; west++) {
            int x = tip - west;
            for (int side = MIN_SIDE; side <= MAX_SIDE; side++) {
                for (int sign : new int[]{firstSide, -firstSide}) {
                    int z = gateZ + sign * side;
                    var minimum = grove.pos(x - RADIUS, 0, z - RADIUS);
                    var maximum = grove.pos(x + RADIUS, 0, z + RADIUS);
                    var owner = new ChunkPos(minimum);
                    if (!owner.equals(new ChunkPos(maximum))
                            || !SettlementLayout.untouchedForCupidShrine(level, grove, x - RADIUS, z - RADIUS,
                                    x + RADIUS, z + RADIUS)) continue;

                    var world = grove.pos(x, 0, z);
                    int floor = GroveSites.height(level, world.getX(), world.getZ());
                    if (floor <= level.getSeaLevel() || floor + CLEARANCE + 2 >= level.getMaxBuildHeight()
                            || Math.abs(floor - gateY) > 3
                            || !level.getWorldBorder().isWithinBounds(minimum.atY(floor))
                            || !level.getWorldBorder().isWithinBounds(maximum.atY(floor))) continue;
                    if (moonveil.isPresent() && overlapsTreehouse(moonveil.orElseThrow(), x, floor, z)) continue;
                    if (VillageSakura.overlapsShrine(level, village, x, floor, z, RADIUS)) continue;
                    if (gradeable(level, grove, x, floor, z)) {
                        return Optional.of(new Site(x, z, world.atY(floor + 1), facing, owner));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean overlapsTreehouse(MoonveilTreehouse.Plot tree, int x, int y, int z) {
        int radius = y + CLEARANCE + 1 >= tree.y() + 14 ? MoonveilTreehouse.FOOTPRINT : 12;
        return Math.abs(x - tree.x()) <= RADIUS + radius && Math.abs(z - tree.z()) <= RADIUS + radius;
    }

    private static boolean gradeable(ServerLevel level, GroveSites.Site grove, int x, int floor, int z) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            int wx = grove.wx(x + dx, z + dz), wz = grove.wz(x + dx, z + dz);
            int height = GroveSites.height(level, wx, wz);
            if (height <= level.getSeaLevel() || Math.abs(height - floor) > 1
                    || !GroveSites.cherry(level, wx, height + 1, wz)) return false;
        }
        return true;
    }

    static void place(HeartwaterFeature.Valley valley, BlossomVillage.Plan village) {
        var level = valley.level.getLevel();
        // Reject distant chunks before seed sampling. The actual write always has just one owner.
        int tip = village.x() - village.road().length / 2;
        int gateZ = village.z() + village.bends()[GATE_INDEX];
        int limit = MAX_SIDE + RADIUS;
        if (!valley.overlaps(tip - MAX_WEST - RADIUS, gateZ - limit, tip - 1, gateZ + limit)) return;
        var selected = select(level, village);
        if (selected.isEmpty()) return;
        var site = selected.orElseThrow();
        int x = site.localX(), z = site.localZ(), floor = site.anchor().getY() - 1;
        if (valley.planning()) {
            valley.survey.box(x - RADIUS, floor - 1, z - RADIUS,
                    x + RADIUS, floor + CLEARANCE + 1, z + RADIUS);
            return;
        }
        if (!valley.chunk.getPos().equals(site.owner()) || !valley.inside(x, z)
                || valley.chunk.getBlockState(site.anchor()).is(CupidShrine.BLOCK.get())) return;
        var ground = new int[2 * RADIUS + 1][2 * RADIUS + 1];
        // Validate the complete plateau first. Never clear water, trees, stonework or a foreign block entity.
        for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            var at = village.site().pos(x + dx, floor, z + dz);
            int height = GroveSites.height(level, at.getX(), at.getZ());
            if (Math.abs(height - floor) > 1) return;
            var surface = valley.chunk.getBlockState(at.atY(height));
            if (!naturalSoil(surface)
                    || !surface.isFaceSturdy(valley.level, at.atY(height), Direction.UP)) return;
            if (height > floor && !naturalSoil(valley.chunk.getBlockState(at))) return;
            for (int y = height + 1; y <= floor + CLEARANCE + 1; y++) {
                if (!clearable(valley.chunk.getBlockState(at.atY(y)))) return;
            }
            ground[dx + RADIUS][dz + RADIUS] = height;
        }

        // At the anchor the seed height equals the pad floor, so its original support is intact.
        valley.put(x, floor + 1, z, Blocks.AIR.defaultBlockState());
        if (!CupidShrine.place(valley.level, site.anchor(), valley.face(Direction.EAST))) return;

        for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            int px = x + dx, pz = z + dz;
            int old = ground[dx + RADIUS][dz + RADIUS];
            boolean pedestal = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
            if (old != floor) valley.block(px, floor, pz, Blocks.GRASS_BLOCK);
            if (old != floor || pedestal) {
                for (int y = floor + 1; y <= floor + (old > floor ? CLEARANCE + 1 : CLEARANCE); y++) {
                    if (dx == 0 && dz == 0 && y == floor + 1) continue;
                    valley.put(px, y, pz, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static boolean naturalSoil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.ROOTED_DIRT);
    }

    private static boolean clearable(BlockState state) {
        return state.getFluidState().isEmpty() && !state.hasBlockEntity()
                && (state.isAir() || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                    || state.is(Blocks.PINK_PETALS) || state.is(BlockTags.SMALL_FLOWERS));
    }
}
