package dev.uwu.dimension;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class SilkwoodShelfBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public enum Join implements StringRepresentable {
        SINGLE, LEFT, MIDDLE, RIGHT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final MapCodec<SilkwoodShelfBlock> CODEC = simpleCodec(SilkwoodShelfBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Join> HORIZONTAL = EnumProperty.create("horizontal", Join.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final Map<Direction, Map<Join, VoxelShape>> SHAPES = shelfShapes();

    public SilkwoodShelfBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(HORIZONTAL, Join.SINGLE).setValue(WATERLOGGED, false));
    }

    private static Map<Direction, Map<Join, VoxelShape>> shelfShapes() {
        var shapes = new EnumMap<Direction, Map<Join, VoxelShape>>(Direction.class);
        VoxelShape board = box(0, 14, 0, 16, 16, 16);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape left = bracket(facing, facing.getClockWise());
            VoxelShape right = bracket(facing, facing.getCounterClockWise());
            shapes.put(facing, Map.of(Join.SINGLE, Shapes.or(board, left, right), Join.LEFT, Shapes.or(board, left),
                    Join.RIGHT, Shapes.or(board, right), Join.MIDDLE, board));
        }
        return shapes;
    }

    private static VoxelShape bracket(Direction facing, Direction side) {
        int x = 8 + 6 * side.getStepX() - 3 * facing.getStepX();
        int z = 8 + 6 * side.getStepZ() - 3 * facing.getStepZ();
        int halfX = facing.getAxis() == Direction.Axis.X ? 5 : 2;
        int halfZ = facing.getAxis() == Direction.Axis.Z ? 5 : 2;
        return box(x - halfX, 6, z - halfZ, x + halfX, 14, z + halfZ);
    }

    /** Block-local display origin, already rotated to the shelf's facing. */
    public static Vec3 displayOffset(Direction facing, int slot) {
        if (!facing.getAxis().isHorizontal() || slot < 0 || slot >= SilkwoodShelfBlockEntity.SLOTS) {
            throw new IllegalArgumentException("Invalid shelf facing or slot");
        }
        Direction across = facing.getClockWise();
        double side = .2 - .4 * (slot % 2);
        double front = .2 - .4 * (slot / 2);
        return new Vec3(.5 + side * across.getStepX() + front * facing.getStepX(), 1.20,
                .5 + side * across.getStepZ() + front * facing.getStepZ());
    }

    public static int slotAt(Direction facing, BlockPos pos, BlockHitResult hit) {
        if (!facing.getAxis().isHorizontal() || hit.getType() != HitResult.Type.BLOCK
                || hit.getDirection() != Direction.UP || !pos.equals(hit.getBlockPos())) return -1;
        Vec3 relative = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        if (!(relative.x >= 0 && relative.x <= 1 && relative.z >= 0 && relative.z <= 1
                && Math.abs(relative.y - 1) < 1.0E-5)) return -1;
        return half(relative, facing.getClockWise()) + 2 * half(relative, facing);
    }

    private static int half(Vec3 hit, Direction axis) {
        double coordinate = hit.get(axis.getAxis());
        if (axis.getAxisDirection() == Direction.AxisDirection.POSITIVE) coordinate = 1 - coordinate;
        return coordinate < .5 ? 0 : 1;
    }

    @Override protected MapCodec<SilkwoodShelfBlock> codec() { return CODEC; }
    @Override public SilkwoodShelfBlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SilkwoodShelfBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected boolean useShapeForLightOcclusion(BlockState state) { return true; }
    @Override protected boolean isPathfindable(BlockState state, PathComputationType path) { return false; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING)).get(state.getValue(HORIZONTAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HORIZONTAL, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        return defaultBlockState().setValue(FACING, facing).setValue(HORIZONTAL, joinAt(level, pos, facing))
                .setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
    }

    private Join joinAt(LevelAccessor level, BlockPos pos, Direction facing) {
        boolean cw = joins(level, pos.relative(facing.getClockWise()), facing);
        boolean ccw = joins(level, pos.relative(facing.getCounterClockWise()), facing);
        if (cw && ccw) return Join.MIDDLE;
        if (cw) return Join.RIGHT;
        return ccw ? Join.LEFT : Join.SINGLE;
    }

    public BlockState pairedState(Direction facing, Direction joinedSide) {
        if (!facing.getAxis().isHorizontal() || !joinedSide.getAxis().isHorizontal() || facing.getAxis() == joinedSide.getAxis()) {
            throw new IllegalArgumentException("A shelf joins its horizontal side");
        }
        Join join = joinedSide == facing.getClockWise() ? Join.RIGHT : Join.LEFT;
        return defaultBlockState().setValue(FACING, facing).setValue(HORIZONTAL, join);
    }

    private boolean joins(LevelAccessor level, BlockPos pos, Direction facing) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState neighbor = level.getBlockState(pos);
        return neighbor.is(this) && neighbor.getValue(FACING) == facing;
    }

    static boolean coversDisplay(BlockGetter level, BlockPos above, BlockState cover) {
        return cover.isFaceSturdy(level, above, Direction.DOWN)
                && !cover.getVisualShape(level, above, CollisionContext.empty()).isEmpty();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (side == Direction.UP && level instanceof ServerLevel && coversDisplay(level, neighborPos, neighbor)
                && level.getBlockEntity(pos) instanceof SilkwoodShelfBlockEntity shelf) shelf.ejectUnderCover();
        Direction facing = state.getValue(FACING);
        if (side.getAxis().isHorizontal() && side.getAxis() != facing.getAxis()) {
            return state.setValue(HORIZONTAL, joinAt(level, pos, facing));
        }
        return state;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        Join mirrored = switch (state.getValue(HORIZONTAL)) {
            case LEFT -> Join.RIGHT;
            case RIGHT -> Join.LEFT;
            default -> state.getValue(HORIZONTAL);
        };
        return state.rotate(mirror.getRotation(state.getValue(FACING))).setValue(HORIZONTAL, mirrored);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        int slot = slotAt(state.getValue(FACING), pos, hit);
        if (slot < 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof SilkwoodShelfBlockEntity shelf)) return ItemInteractionResult.FAIL;
        if (!level.isClientSide && (!(player instanceof ServerPlayer actor) || actor.serverLevel() != level
                || actor.isSpectator() || !actor.isAlive() || actor instanceof FakePlayer
                || !actor.canInteractWithBlock(pos, 1) || !level.getWorldBorder().isWithinBounds(pos)
                || !SakuraAccess.mayInteract(actor, pos))) return ItemInteractionResult.FAIL;
        if (shelf.isCovered()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        boolean take = player.getItemInHand(hand).isEmpty();
        boolean emptySlot = shelf.isSlotEmpty(slot);
        if (take && emptySlot) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!take && !emptySlot) return ItemInteractionResult.CONSUME;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!shelf.transfer(slot, player, hand)) return ItemInteractionResult.FAIL;
        level.playSound(null, pos, take ? SoundEvents.ITEM_PICKUP : SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1F, 1F);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!level.isClientSide && !state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof SilkwoodShelfBlockEntity shelf) {
            shelf.dropDisplayedItems();
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
