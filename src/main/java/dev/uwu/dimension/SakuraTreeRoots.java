package dev.uwu.dimension;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Tests the footprint the trunk will actually occupy, allowing dry terrain to slope beside it. */
public final class SakuraTreeRoots {
    private SakuraTreeRoots() {}

    public static boolean dry(BlockGetter level, BlockPos base, int width) {
        return dry(level, base, width, level::getBlockState);
    }

    static boolean dry(BlockGetter level, BlockPos base, int width, Function<BlockPos, BlockState> lookup) {
        for (int x = -1; x <= width; x++) for (int z = -1; z <= width; z++) {
            var column = base.offset(x, 0, z);
            for (int h = -1; h <= 1; h++) {
                var state = lookup.apply(column.above(h));
                if (state == null || !state.getFluidState().isEmpty()) return false;
            }
            var soil = column.below();
            if (x >= 0 && z >= 0 && x < width && z < width) {
                var state = lookup.apply(soil);
                if (!state.is(BlockTags.DIRT) || !state.isFaceSturdy(level, soil, Direction.UP)) return false;
            } else {
                boolean supported = false;
                for (int h = -2; h <= 0; h++) {
                    var at = column.above(h);
                    var state = lookup.apply(at);
                    if (state != null && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                            && state.getFluidState().isEmpty() && state.isFaceSturdy(level, at, Direction.UP)) {
                        supported = true;
                        break;
                    }
                }
                if (!supported) return false;
            }
        }
        return true;
    }
}
