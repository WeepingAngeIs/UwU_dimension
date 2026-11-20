package dev.uwu.dimension;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class LanternStorybookBlock extends BaseEntityBlock {
    static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    static final EnumProperty<Edition> EDITION = EnumProperty.create("edition", Edition.class);
    private static final MapCodec<LanternStorybookBlock> CODEC = simpleCodec(LanternStorybookBlock::new);
    private static final ResourceLocation CONTENTS = UwUDimension.id("lantern_storybook_contents");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 3, 15);

    enum Edition implements StringRepresentable {
        PETAL("petal"), DUSK("dusk");
        private final String name;
        Edition(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    LanternStorybookBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EDITION, Edition.PETAL));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, EDITION);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LanternStorybookEntity(pos, state);
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
                                               LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (side == Direction.DOWN && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, side, neighbor, level, pos, neighborPos);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer reader && level.getBlockEntity(pos) instanceof LanternStorybookEntity entity) {
            LanternStorybook.open(reader, entity.book(level.registryAccess()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        var entity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        params.withDynamicDrop(CONTENTS, output -> {
            var registries = params.getLevel().registryAccess();
            var book = entity instanceof LanternStorybookEntity saved ? saved.book(registries) : LanternBookStacks.create(registries);
            if (!book.isEmpty()) output.accept(book);
        });
        return super.getDrops(state, params);
    }
}
