package dev.uwu.dimension;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

/** Confirmed village plazas, independent of the registry of individual guardians. */
final class GuardianVillageHomes extends SavedData {
    static final int PRECINCT_RADIUS = 48;
    private static final int WITNESS_RANGE = 160;
    private static final Factory<GuardianVillageHomes> FACTORY =
            new Factory<>(GuardianVillageHomes::new, GuardianVillageHomes::load, null);
    private final Set<BlockPos> courts = new HashSet<>();

    private static GuardianVillageHomes data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "uwudimension_guardian_villages");
    }

    static void remember(ServerLevel level, BlockPos court) {
        GuardianVillageHomes homes = data(level);
        if (homes.courts.add(court.immutable())) homes.setDirty();
    }

    static boolean confirmed(ServerLevel level, BlockPos court) {
        if (data(level).courts.contains(court)) return true;
        if (!hasPlazaMarker(level, court)) return false;
        remember(level, court);
        return true;
    }

    @Nullable static BlockPos nearest(ServerLevel level, BlockPos origin) {
        AABB nearby = new AABB(origin.getX() - WITNESS_RANGE, level.getMinBuildHeight(),
                origin.getZ() - WITNESS_RANGE, origin.getX() + WITNESS_RANGE + 1, level.getMaxBuildHeight(),
                origin.getZ() + WITNESS_RANGE + 1);
        // A resident's actual settlement is also evidence for older villages built before bells.
        for (Keeper keeper : level.getEntitiesOfClass(Keeper.class, nearby,
                resident -> resident.isAlive() && resident.ownerId() == null && resident.villageCourt() != null)) {
            remember(level, keeper.villageCourt());
        }
        return data(level).courts.stream().filter(court -> level.getWorldBorder().isWithinBounds(court))
                .min(Comparator.comparingDouble((BlockPos court) -> horizontalDistanceSquared(court, origin))
                        .thenComparingLong(BlockPos::asLong)).orElse(null);
    }

    static boolean inPrecinct(BlockPos court, BlockPos pos) {
        return Math.abs((long) court.getX() - pos.getX()) <= PRECINCT_RADIUS
                && Math.abs((long) court.getZ() - pos.getZ()) <= PRECINCT_RADIUS;
    }

    private static double horizontalDistanceSquared(BlockPos a, BlockPos b) {
        double dx = (double) a.getX() - b.getX();
        double dz = (double) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean hasPlazaMarker(ServerLevel level, BlockPos court) {
        BlockState floor = loadedState(level, court.below());
        if (floor == null || !floor.is(HeartwaterFeature.QUILT.get()) && !floor.is(HeartwaterFeature.LACE.get())) return false;
        // Every authored orientation has the meeting bell at one of the four corners.
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-2, 2}) {
                BlockState bell = loadedState(level, court.offset(dx, 0, dz));
                if (bell != null && bell.is(Blocks.BELL)) return true;
            }
        }
        return false;
    }

    @Nullable private static BlockState loadedState(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    private static GuardianVillageHomes load(CompoundTag tag, HolderLookup.Provider registries) {
        GuardianVillageHomes homes = new GuardianVillageHomes();
        for (long court : tag.getLongArray("Courts")) homes.courts.add(BlockPos.of(court));
        return homes;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("Courts", courts.stream().mapToLong(BlockPos::asLong).sorted().toArray());
        return tag;
    }
}
