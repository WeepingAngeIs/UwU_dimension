package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.grower.TreeGrower;

/** Orchard saplings can be planted, bonemealed, and grown only in the UwU realms. */
final class DimensionSaplingBlock extends SaplingBlock {
    DimensionSaplingBlock(TreeGrower grower, BlockBehaviour.Properties properties) {
        super(grower, properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return ReefLife.isProduceRealm(context.getLevel()) ? super.getStateForPlacement(context) : null;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (ReefLife.isProduceRealm(level)) super.randomTick(state, level, pos, random);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return level instanceof Level actual && ReefLife.isProduceRealm(actual)
                && super.isValidBonemealTarget(level, pos, state);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return ReefLife.isProduceRealm(level) && super.isBonemealSuccess(level, random, pos, state);
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (ReefLife.isProduceRealm(level)) super.performBonemeal(level, random, pos, state);
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (ReefLife.isProduceRealm(level)) super.advanceTree(level, pos, state, random);
    }
}
