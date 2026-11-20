package dev.uwu.dimension;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SilkwoodFlowerBoxBlock extends BaseEntityBlock {
    public static final MapCodec<SilkwoodFlowerBoxBlock> CODEC = simpleCodec(SilkwoodFlowerBoxBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    public static final EnumProperty<SilkwoodShelfBlock.Join> HORIZONTAL = SilkwoodShelfBlock.HORIZONTAL;
    public static final TagKey<Item> PLACEABLES = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("uwudimension", "flower_box_placeables"));
    public static final TagKey<Item> BANNED = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("uwudimension", "flower_box_banned"));

    private static final VoxelShape FLOOR_NS = box(0, 0, 4, 16, 6, 12);
    private static final VoxelShape FLOOR_EW = box(4, 0, 0, 12, 6, 16);
    private static final VoxelShape WALL_N = box(0, 10, 8, 16, 16, 16);
    private static final VoxelShape WALL_S = box(0, 10, 0, 16, 16, 8);
    private static final VoxelShape WALL_W = box(8, 10, 0, 16, 16, 16);
    private static final VoxelShape WALL_E = box(0, 10, 0, 8, 16, 16);

    public SilkwoodFlowerBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(ATTACHED, false).setValue(HORIZONTAL, SilkwoodShelfBlock.Join.SINGLE));
    }

    @Override protected MapCodec<SilkwoodFlowerBoxBlock> codec() { return CODEC; }
    @Override public SilkwoodFlowerBoxBlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SilkwoodFlowerBoxBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected boolean isPathfindable(BlockState state, PathComputationType path) { return false; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (!state.getValue(ATTACHED)) return facing.getAxis() == Direction.Axis.Z ? FLOOR_NS : FLOOR_EW;
        return switch (facing) {
            case NORTH -> WALL_N;
            case SOUTH -> WALL_S;
            case WEST -> WALL_W;
            case EAST -> WALL_E;
            default -> throw new IllegalStateException("Flower box facing must be horizontal");
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ATTACHED, HORIZONTAL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        boolean wall = clicked.getAxis().isHorizontal();
        Direction front = wall ? clicked : context.getHorizontalDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, front).setValue(ATTACHED, wall);
        return connected(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!state.getValue(ATTACHED)) return true;
        Direction facing = state.getValue(FACING);
        BlockPos back = pos.relative(facing.getOpposite());
        return level.getBlockState(back).isFaceSturdy(level, back, facing);
    }

    private BlockState connected(BlockState state, LevelAccessor level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        boolean wall = state.getValue(ATTACHED);
        boolean cw = joins(level, pos.relative(facing.getClockWise()), facing, wall);
        boolean ccw = joins(level, pos.relative(facing.getCounterClockWise()), facing, wall);
        var join = SilkwoodShelfBlock.Join.SINGLE;
        if (cw && ccw) join = SilkwoodShelfBlock.Join.MIDDLE;
        else if (cw) join = SilkwoodShelfBlock.Join.RIGHT;
        else if (ccw) join = SilkwoodShelfBlock.Join.LEFT;
        return state.setValue(HORIZONTAL, join);
    }

    private boolean joins(LevelAccessor level, BlockPos pos, Direction facing, boolean wall) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState neighbor = level.getBlockState(pos);
        if (!neighbor.is(this) || neighbor.getValue(ATTACHED) != wall) return false;
        Direction other = neighbor.getValue(FACING);
        return other == facing || !wall && other == facing.getOpposite();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        if (state.getValue(ATTACHED)) {
            if (side == facing.getOpposite() && !neighbor.isFaceSturdy(level, neighborPos, facing)) return Blocks.AIR.defaultBlockState();
            if (side == Direction.UP && level instanceof ServerLevel && neighbor.isFaceSturdy(level, neighborPos, Direction.DOWN)
                    && level.getBlockEntity(pos) instanceof SilkwoodFlowerBoxBlockEntity box) box.ejectUnderCover();
        }
        if (side.getAxis().isHorizontal() && side.getAxis() != facing.getAxis()) return connected(state, level, pos);
        return state;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        var join = state.getValue(HORIZONTAL);
        if (join == SilkwoodShelfBlock.Join.LEFT) join = SilkwoodShelfBlock.Join.RIGHT;
        else if (join == SilkwoodShelfBlock.Join.RIGHT) join = SilkwoodShelfBlock.Join.LEFT;
        return state.rotate(mirror.getRotation(state.getValue(FACING))).setValue(HORIZONTAL, join);
    }

    public static boolean acceptsPlant(ItemStack stack) {
        return stack.getItem() instanceof BlockItem && stack.is(PLACEABLES) && !stack.is(BANNED);
    }

    public static int slotAt(Direction facing, BlockPos pos, BlockHitResult hit) {
        if (!facing.getAxis().isHorizontal() || hit.getType() != HitResult.Type.BLOCK || !pos.equals(hit.getBlockPos())) return -1;
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        if (!(local.x >= 0 && local.x <= 1 && local.y >= 0 && local.y <= 1 && local.z >= 0 && local.z <= 1)) return -1;
        Direction across = facing.getClockWise();
        double coordinate = local.get(across.getAxis());
        if (across.getAxisDirection() == Direction.AxisDirection.POSITIVE) coordinate = 1 - coordinate;
        return coordinate < .5 ? 0 : 1;
    }

    /** Block-local soil anchor at the center of the plant model, rotated to its final orientation. */
    public static Vec3 plantOffset(BlockState state, int slot) {
        if (slot < 0 || slot >= SilkwoodFlowerBoxBlockEntity.SLOTS) throw new IllegalArgumentException("Invalid flower box slot");
        Direction facing = state.getValue(FACING);
        Direction across = facing.getClockWise();
        boolean wall = state.getValue(ATTACHED);
        double side = slot == 0 ? .25 : -.25;
        double back = wall ? .25 : 0;
        return new Vec3(.5 + side * across.getStepX() - back * facing.getStepX(), (wall ? 15 : 5) / 16.0 - 1 / 64.0,
                .5 + side * across.getStepZ() - back * facing.getStepZ());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        int slot = slotAt(state.getValue(FACING), pos, hit);
        ItemStack held = player.getItemInHand(hand);
        boolean lift = held.isEmpty();
        if (slot < 0 || !lift && !acceptsPlant(held)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof SilkwoodFlowerBoxBlockEntity box)) return ItemInteractionResult.FAIL;
        if (!level.isClientSide && (!(player instanceof ServerPlayer actor) || actor.serverLevel() != level
                || !FurnitureSeatBlock.mayUse(actor, pos))) return ItemInteractionResult.FAIL;
        if (box.isCovered()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        boolean empty = box.isSlotEmpty(slot);
        if (lift && empty) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!lift && !empty) return ItemInteractionResult.CONSUME;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        var sound = lift ? SoundEvents.ITEM_PICKUP : ((BlockItem)held.getItem()).getBlock()
                .defaultBlockState().getSoundType(level, pos, player).getBreakSound();
        if (!box.transfer(slot, player, hand)) return ItemInteractionResult.FAIL;
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1F, 1F);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!level.isClientSide && !state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof SilkwoodFlowerBoxBlockEntity box) {
            box.dropPlants();
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
