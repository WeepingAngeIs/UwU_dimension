package dev.uwu.dimension;

import com.mojang.serialization.MapCodec;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST;

final class TeaTableBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    private static final MapCodec<TeaTableBlock> CODEC = simpleCodec(TeaTableBlock::new);
    private static final int NORTH_JOIN = 1, EAST_JOIN = 2, SOUTH_JOIN = 4, WEST_JOIN = 8;
    private static final VoxelShape[] SHAPES = makeShapes();

    TeaTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false)
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    private static VoxelShape[] makeShapes() {
        VoxelShape top = Block.box(0, 13, 0, 16, 16, 16);
        VoxelShape northWest = Block.box(1, 0, 1, 3, 13, 3);
        VoxelShape northEast = Block.box(13, 0, 1, 15, 13, 3);
        VoxelShape southEast = Block.box(13, 0, 13, 15, 13, 15);
        VoxelShape southWest = Block.box(1, 0, 13, 3, 13, 15);
        VoxelShape[] shapes = new VoxelShape[16];
        for (int joins = 0; joins < shapes.length; joins++) {
            VoxelShape shape = top;
            if ((joins & (NORTH_JOIN | WEST_JOIN)) == 0) shape = Shapes.or(shape, northWest);
            if ((joins & (NORTH_JOIN | EAST_JOIN)) == 0) shape = Shapes.or(shape, northEast);
            if ((joins & (SOUTH_JOIN | EAST_JOIN)) == 0) shape = Shapes.or(shape, southEast);
            if ((joins & (SOUTH_JOIN | WEST_JOIN)) == 0) shape = Shapes.or(shape, southWest);
            shapes[joins] = shape.optimize();
        }
        return shapes;
    }

    @Override protected MapCodec<TeaTableBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED, NORTH, EAST, SOUTH, WEST);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), connectsTo(level.getBlockState(pos.relative(side))));
        }
        return state;
    }

    @Override protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
                                              LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (side.getAxis().isHorizontal()) {
            return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), connectsTo(neighbor));
        }
        return super.updateShape(state, side, neighbor, level, pos, neighborPos);
    }

    private static boolean connectsTo(BlockState neighbor) {
        return neighbor.getBlock() instanceof TeaTableBlock;
    }

    @Override protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int joins = (state.getValue(NORTH) ? NORTH_JOIN : 0) | (state.getValue(EAST) ? EAST_JOIN : 0)
                | (state.getValue(SOUTH) ? SOUTH_JOIN : 0) | (state.getValue(WEST) ? WEST_JOIN : 0);
        return SHAPES[joins];
    }

    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) { return false; }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return transform(state, rotation::rotate);
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return transform(state, mirror::mirror);
    }

    private static BlockState transform(BlockState state, UnaryOperator<Direction> transform) {
        BlockState result = state.setValue(FACING, transform.apply(state.getValue(FACING)));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            var source = PipeBlock.PROPERTY_BY_DIRECTION.get(side);
            var target = PipeBlock.PROPERTY_BY_DIRECTION.get(transform.apply(side));
            result = result.setValue(target, state.getValue(source));
        }
        return result;
    }
}
