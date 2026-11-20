package dev.uwu.dimension;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

final class LocketLanding {
    private static boolean hazardous(BlockState state) {
        return !state.getFluidState().isEmpty() || harmfulBlock(state);
    }

    static boolean harmfulBlock(BlockState state) {
        return state.is(BlockTags.FIRE)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
    }

    static boolean safe(ServerLevel level, Player player, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 2 >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(feet)) return false;
        BlockPos floor = feet.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        double half = Math.max(.3, player.getBbWidth() / 2.0);
        AABB body = new AABB(feet.getX()+.5-half, feet.getY(), feet.getZ()+.5-half,
                feet.getX()+.5+half, feet.getY()+Math.max(1.8, player.getBbHeight()), feet.getZ()+.5+half);
        if (!level.getWorldBorder().isWithinBounds(body) || !level.noCollision(body)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX,body.minY-1,body.minZ),
                BlockPos.containing(body.maxX,body.maxY,body.maxZ))) {
            if (hazardous(level.getBlockState(pos))) return false;
        }
        return true;
    }

    static Optional<BlockPos> nearby(ServerLevel level, Player player, BlockPos desired) {
        if (!level.getWorldBorder().isWithinBounds(desired)) return Optional.empty();
        for (int radius = 0; radius <= 5; radius++) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                BlockPos column = desired.offset(x, 0, z);
                if (!level.getWorldBorder().isWithinBounds(column)) continue;
                for (int dy : OFFSETS) {
                    BlockPos candidate = column.above(dy);
                    if (safe(level, player, candidate)) return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static final int[] OFFSETS = {0, 1, -1, 2, -2, 3, -3, 4, -4};

    static Optional<BlockPos> surface(ServerLevel level, Player player, BlockPos origin) {
        if (level.dimensionType().hasCeiling() || !level.getWorldBorder().isWithinBounds(origin)) return Optional.empty();
        for (int x : SURVEY) for (int z : SURVEY) {
            BlockPos column = origin.offset(x, 0, z);
            if (!level.getWorldBorder().isWithinBounds(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (safe(level, player, top)) return Optional.of(top);
        }
        return Optional.empty();
    }
    private static final int[] SURVEY = {0, 2, -2, 5, -5, 9, -9};
}
