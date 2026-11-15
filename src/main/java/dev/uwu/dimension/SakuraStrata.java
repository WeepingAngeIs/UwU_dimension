package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Bounded, chunk-local lenses. Exposed faces preferentially sparkle along cave walls. */
final class SakuraStrata extends Feature<NoneFeatureConfiguration> {
    SakuraStrata() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!ReefLife.isReach(level.getLevel())) return false;
        RandomSource random = context.random();
        BlockPos corner = context.origin();
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
        return placed;
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
