package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Chunk-local mineral lenses in newly decorated Sakura Reach terrain. */
final class SakuraStrata {
    private SakuraStrata() {}
    static boolean decorate(WorldGenLevel level, BlockPos corner, RandomSource random) {
        int x = corner.getX() & ~15;
        int z = corner.getZ() & ~15;
        boolean placed = false;
        for (int i = 0; i < 3; i++) {
            BlockPos center = new BlockPos(x + 4 + random.nextInt(8), 12 + random.nextInt(41), z + 4 + random.nextInt(8));
            placed |= lens(level, random, center, SakuraCraft.TUFF.get(), SakuraCraft.ROSE_ORE.get());
        }
        if (random.nextInt(3) != 0) {
            BlockPos center = new BlockPos(x + 4 + random.nextInt(8), -46 + random.nextInt(37), z + 4 + random.nextInt(8));
            placed |= lens(level, random, center, SakuraCraft.SHALE.get(), SakuraCraft.OPAL_ORE.get());
        }
        if (random.nextInt(5) == 0) placed |= cavern(level, random, x, z);
        return placed;
    }

    private static boolean cavern(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ) {
        int x = chunkX + 4 + random.nextInt(8), z = chunkZ + 4 + random.nextInt(8);
        for (int y = 48; y >= -46; y--) {
            BlockPos foot = new BlockPos(x, y, z);
            if (!level.getBlockState(foot.below()).is(BlockTags.BASE_STONE_OVERWORLD)
                    || !level.isEmptyBlock(foot) || !level.isEmptyBlock(foot.above(2))) continue;
            if (level.canSeeSky(foot)) continue;
            // Ruined lintels point miners to an exposed vein by their foundations.
            if (y < -8 && y > -45 && clearForLintel(level, foot)) {
                for (int dx = -2; dx <= 2; dx++) {
                    level.setBlock(foot.offset(dx, -1, 0),
                            (dx == 1 ? SakuraCraft.OPAL_ORE : SakuraCraft.SHALE).get().defaultBlockState(), 2);
                    if (Math.abs(dx) == 2) {
                        level.setBlock(foot.offset(dx, 0, 0), SakuraCraft.SHALE.get().defaultBlockState(), 2);
                        level.setBlock(foot.offset(dx, 1, 0), SakuraCraft.ABYSS.get().defaultBlockState(), 2);
                    }
                    if (dx != 0) level.setBlock(foot.offset(dx, 2, 0), SakuraCraft.SHALE.get().defaultBlockState(), 2);
                }
                return true;
            }
            if (y < 10 || y > 48) continue;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos wall = foot.relative(dir);
                if (!level.getBlockState(wall).is(BlockTags.BASE_STONE_OVERWORLD)) continue;
                level.setBlock(wall, SakuraCraft.ROSE_ORE.get().defaultBlockState(), 2);
                // Let the existing aquifer feed a thin, luminous curtain when there is a water pocket.
                for (int h = 2; h <= 6; h++) {
                    var lip = wall.above(h);
                    if (level.isEmptyBlock(foot.above(h)) && level.getBlockState(lip).is(BlockTags.BASE_STONE_OVERWORLD)
                            && level.getBlockState(lip.relative(dir)).is(Blocks.WATER)) {
                        level.setBlock(lip, Blocks.WATER.defaultBlockState(), 2);
                        level.scheduleTick(lip, Fluids.WATER, 1);
                        break;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static boolean clearForLintel(WorldGenLevel level, BlockPos foot) {
        for (int x = -2; x <= 2; x++) {
            if (!level.getBlockState(foot.offset(x,-1,0)).is(BlockTags.BASE_STONE_OVERWORLD)) return false;
            for (int y = 0; y < 3; y++) if (!level.isEmptyBlock(foot.offset(x,y,0))) return false;
        }
        return true;
    }

    private static boolean lens(WorldGenLevel level, RandomSource random, BlockPos center, Block host, Block ore) {
        boolean placed = false;
        int width = 2 + random.nextInt(3);
        for (int x = -width; x <= width; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = -2; y <= 2; y++) {
                    double distance = x*x/(double)(width*width) + z*z/9.0 + y*y/4.0;
                    if (distance > .78 + random.nextFloat() * .32) continue;
                    BlockPos pos = center.offset(x, y, z);
                    if (!level.getBlockState(pos).is(BlockTags.BASE_STONE_OVERWORLD)) continue;
                    boolean open = level.isEmptyBlock(pos.above()) || level.isEmptyBlock(pos.below())
                            || level.getBlockState(pos.above()).is(Blocks.WATER);
                    Block mineral = random.nextInt(open ? 8 : 28) == 0 ? ore : host;
                    placed |= level.setBlock(pos, mineral.defaultBlockState(), 2);
                }
            }
        }
        return placed;
    }
}
