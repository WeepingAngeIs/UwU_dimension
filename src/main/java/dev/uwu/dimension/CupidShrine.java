package dev.uwu.dimension;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** One native shrine block persists the statue's owner position and facing. */
public final class CupidShrine {
    static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "uwudimension");
    public static final DeferredBlock<Block> BLOCK = BLOCKS.register("cupid_shrine",
            () -> new MarkerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_DIORITE)
                    .strength(-1.0F, 3_600_000.0F).noOcclusion().noLootTable()));
    private static final DeferredHolder<Item, BlockItem> STATUE_ITEM = ITEMS.register("cupid_shrine",
            () -> new StatueItem(BLOCK.get()));
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Marker>> ENTITY =
            ENTITIES.register("cupid_shrine", () -> BlockEntityType.Builder.of(Marker::new, BLOCK.get()).build(null));

    private CupidShrine() {}

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(CupidShrine::addCreativeItem);
    }

    private static void addCreativeItem(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(UwUDimension.id("main"))) event.accept(STATUE_ITEM.get());
    }

    private static final class StatueItem extends BlockItem {
        StatueItem(Block block) {
            super(block, new Item.Properties());
        }

        @Override public InteractionResult place(BlockPlaceContext context) {
            Player player = context.getPlayer();
            if (player == null || !player.isCreative()) return InteractionResult.FAIL;
            return super.place(context);
        }
    }

    public static BlockState state(Direction facing) {
        return BLOCK.get().defaultBlockState().setValue(FACING, facing);
    }

    /** The caller has already reserved a clear, level pad and owns this generation chunk. */
    public static boolean place(WorldGenLevel region, BlockPos pos, Direction facing) {
        if (!ReefLife.isSettlementRealm(region.getLevel())) return false;
        var before = region.getBlockState(pos);
        var state = state(facing);
        if (before == state) return true;
        if (!before.isAir() || !state.canSurvive(region, pos)) return false;
        return region.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    static final class Marker extends BlockEntity {
        private Marker(BlockPos pos, BlockState state) {
            super(ENTITY.get(), pos, state);
        }

        @Override public void onLoad() {
            super.onLoad();
            if (level != null) CupidShrineVisits.loaded(level, worldPosition);
        }

        @Override public void setRemoved() {
            if (level != null) CupidShrineVisits.unloaded(level, worldPosition);
            super.setRemoved();
        }
    }

    private static final class MarkerBlock extends BaseEntityBlock {
        private static final MapCodec<MarkerBlock> CODEC = simpleCodec(MarkerBlock::new);
        // The pedestal and seated body occupy the center of the marker, with an overhang
        // into the clear second block above it.
        private static final VoxelShape BASE = Block.box(1.25, 0, 1.25, 14.75, 5.75, 14.75);
        private static final VoxelShape BODY = Block.box(3.25, 5.75, 3.25, 12.75, 28.8, 12.75);
        private static final VoxelShape SHAPE = Shapes.or(BASE, BODY);

        private MarkerBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
        }

        @Override protected MapCodec<MarkerBlock> codec() { return CODEC; }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            return state(context.getHorizontalDirection().getOpposite());
        }

        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Marker(pos, state); }

        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

        @Override protected BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override protected BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }

        @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            BlockPos support = pos.below();
            return level.getBlockState(support).isFaceSturdy(level, support, Direction.UP);
        }

        @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                              Player player, BlockHitResult hit) {
            if (!level.dimension().equals(ReefLife.REACH) || !player.isAlive() || player.isSpectator()
                    || !player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer reader)) return InteractionResult.SUCCESS;
            if (reader.distanceToSqr(Vec3.atBottomCenterOf(pos)) > 9.0 || !SakuraAccess.mayInteract(reader, pos)) {
                return InteractionResult.PASS;
            }
            var lookedAt = reader.pick(reader.blockInteractionRange(), 1F, false);
            if (!(lookedAt instanceof BlockHitResult target) || lookedAt.getType() != HitResult.Type.BLOCK
                    || !pos.equals(target.getBlockPos())) return InteractionResult.PASS;
            CupidShrineVisits.read(reader);
            return InteractionResult.CONSUME;
        }
    }
}
