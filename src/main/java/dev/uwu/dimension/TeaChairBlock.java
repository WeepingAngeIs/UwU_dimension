package dev.uwu.dimension;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TeaChairBlock extends FurnitureSeatBlock implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty TUCKED = BooleanProperty.create("tucked");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final TagKey<Block> TABLES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("uwudimension", "tea_tables"));
    private static final Map<Direction, VoxelShape[]> SHAPES = shapes();

    public TeaChairBlock(BlockBehaviour.Properties properties) {
        super(properties, .35F);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(TUCKED, false).setValue(WATERLOGGED, false));
    }

    private static Map<Direction, VoxelShape[]> shapes() {
        var out = new EnumMap<Direction, VoxelShape[]>(Direction.class);
        VoxelShape base = box(2, 5, 2, 14, 7, 14);
        for (int x : new int[] {3, 11}) {
            for (int z : new int[] {3, 11}) base = Shapes.or(base, box(x, 0, z, x + 2, 5, z + 2));
        }
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape back = switch (facing) {
                case NORTH -> box(2, 7, 11, 14, 16, 13);
                case SOUTH -> box(2, 7, 3, 14, 16, 5);
                case WEST -> box(11, 7, 2, 13, 16, 14);
                case EAST -> box(3, 7, 2, 5, 16, 14);
                default -> throw new IllegalArgumentException(facing.toString());
            };
            VoxelShape open = Shapes.or(base, back);
            out.put(facing, new VoxelShape[] {open, open.move(facing.getStepX() * .5, 0, facing.getStepZ() * .5)});
        }
        return out;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TUCKED, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING))[state.getValue(TUCKED) ? 1 : 0];
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos otherPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (state.getValue(TUCKED) && side == state.getValue(FACING) && !supportsTuck(neighbor, side)) {
            return state.setValue(TUCKED, false);
        }
        return super.updateShape(state, side, neighbor, level, pos, otherPos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    boolean canSit(BlockState state) {
        return !state.getValue(TUCKED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) return super.useWithoutItem(state, level, pos, player, hit);
        if (!player.getMainHandItem().isEmpty() || player.isPassenger()) return InteractionResult.PASS;
        Direction facing = state.getValue(FACING);
        BlockPos table = pos.relative(facing);
        if (!level.hasChunkAt(table) || !level.getWorldBorder().isWithinBounds(table)) return InteractionResult.FAIL;
        if (!state.getValue(TUCKED) && !supportsTuck(level.getBlockState(table), facing)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer actor) || !actor.mayBuild() || !mayUse(actor, pos)
                || !SakuraAccess.mayInteract(actor, table) || NativeSeatMount.occupied(level, pos)) {
            return InteractionResult.FAIL;
        }
        BlockState next = state.cycle(TUCKED);
        VoxelShape moved = next.getCollisionShape(level, pos).move(pos.getX(), pos.getY(), pos.getZ());
        if (!level.isUnobstructed(null, moved) || (next.getValue(TUCKED) && !fitsUnder(level, pos, table, moved))) {
            return InteractionResult.FAIL;
        }
        return level.setBlockAndUpdate(pos, next) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    private static boolean supportsTuck(BlockState other, Direction toward) {
        if (other.is(TABLES)) return true;
        if (other.getBlock() instanceof SlabBlock) return other.getValue(SlabBlock.TYPE) == SlabType.TOP;
        if (!(other.getBlock() instanceof StairBlock) || other.getValue(StairBlock.HALF) != Half.TOP) return false;
        Direction stair = other.getValue(StairBlock.FACING);
        StairsShape shape = other.getValue(StairBlock.SHAPE);
        return toward == stair || (shape == StairsShape.OUTER_LEFT && toward == stair.getCounterClockWise())
                || (shape == StairsShape.OUTER_RIGHT && toward == stair.getClockWise());
    }

    private static boolean fitsUnder(Level level, BlockPos pos, BlockPos table, VoxelShape moved) {
        BlockState support = level.getBlockState(table);
        if (!support.is(TABLES) && Shapes.joinIsNotEmpty(moved, support.getCollisionShape(level, table)
                .move(table.getX(), table.getY(), table.getZ()), BooleanOp.AND)) return false;
        // Chairs on the other edges of a table can project into the same space.
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos other = table.relative(side);
            if (other.equals(pos)) continue;
            if (!level.hasChunkAt(other)) return false;
            BlockState state = level.getBlockState(other);
            if (state.getBlock() instanceof TeaChairBlock && state.getValue(TUCKED)) {
                VoxelShape neighbor = state.getCollisionShape(level, other).move(other.getX(), other.getY(), other.getZ());
                if (Shapes.joinIsNotEmpty(moved, neighbor, BooleanOp.AND)) return false;
            }
        }
        return true;
    }
}
