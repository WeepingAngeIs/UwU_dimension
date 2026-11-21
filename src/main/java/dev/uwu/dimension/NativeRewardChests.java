package dev.uwu.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

final class NativeRewardChests {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "uwudimension");
    private static final Component TITLE = Component.translatable("container.uwudimension.reward_chest");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);
    static final DeferredBlock<RewardChestBlock> PETAL = BLOCKS.register("petal_reward_chest", NativeRewardChests::makeBlock);
    static final DeferredBlock<RewardChestBlock> DUSK = BLOCKS.register("dusk_reward_chest", NativeRewardChests::makeBlock);
    static final DeferredBlock<RewardChestBlock> PEARL = BLOCKS.register("pearl_reward_chest", NativeRewardChests::makeBlock);
    static final Supplier<SoundEvent> DUSK_OPEN = UwUDimension.SOUNDS.register("dusk_chest_open",
            () -> SoundEvent.createVariableRangeEvent(UwUDimension.id("dusk_chest_open")));
    static final Supplier<SoundEvent> DUSK_CLOSE = UwUDimension.SOUNDS.register("dusk_chest_close",
            () -> SoundEvent.createVariableRangeEvent(UwUDimension.id("dusk_chest_close")));
    static final Supplier<BlockEntityType<NativeRewardChestBlockEntity>> BLOCK_ENTITY =
            ENTITIES.register("native_reward_chest", () -> BlockEntityType.Builder.of(
                    NativeRewardChestBlockEntity::new, PETAL.get(), DUSK.get(), PEARL.get()).build(null));

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ENTITIES.register(bus);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, GeneratedRewardPlacement::checkManagedLootr);
    }

    private static RewardChestBlock makeBlock() {
        return new RewardChestBlock(BlockBehaviour.Properties.of().noOcclusion().strength(2.5F).noLootTable());
    }

    private static boolean reconcile(ServerLevel level, NativeRewardChestBlockEntity chest,
                                     NativeRewardAssignment assignment) {
        var site = assignment.site();
        var position = chest.getBlockPos();
        if (!level.getServer().isSameThread() || !ReefLife.isReach(level) || !level.hasChunkAt(position)
                || level.getBlockEntity(position) != chest || !hasTheme(chest, assignment.kind())
                || !site.matches(site.worldNamespace(), level.dimension().location(), position)) return false;
        if (assigned(level, chest, assignment)) return true;
        try {
            var receipt = NativeRewardIntents.forWorld(level.getServer()).find(assignment);
            if (receipt.isEmpty() || receipt.get().backend() != NativeRewardIntents.Backend.NATIVE) return false;
            var registry = NativeRewardRegistry.forNewChest(level, site.worldNamespace());
            return registry != null && registry.reconcile(site.worldNamespace(), level.dimension().location(),
                    position, assignment);
        } catch (IOException failure) {
            LogUtils.getLogger().warn("Unable to recover native reward at {}", position, failure);
            return false;
        }
    }

    private static boolean hasTheme(NativeRewardChestBlockEntity chest, NativeRewardAssignment.Kind kind) {
        return BuiltInRegistries.BLOCK.getKey(chest.getBlockState().getBlock()).equals(UwUDimension.id(kind.theme));
    }

    private static boolean assigned(ServerLevel level, NativeRewardChestBlockEntity chest,
                                    NativeRewardAssignment assignment) {
        var registry = NativeRewardRegistry.get(level);
        return registry != null && hasTheme(chest, assignment.kind()) && assignment.equals(chest.assignment())
                && assignment.authorizes(registry.namespace, level.dimension().location(), chest.getBlockPos(),
                        registry.original(assignment.site().id()));
    }

    private static void open(ServerPlayer player, NativeRewardChestBlockEntity chest) {
        ServerLevel level = player.serverLevel();
        boolean dusk = chest.getBlockState().is(DUSK.get());
        if (dusk && player.containerMenu instanceof NativeRewardChestMenu current && current.isViewerOf(chest)) return;
        var assignment = chest.assignment();
        if (assignment == null || !reconcile(level, chest, assignment)) return;
        var access = new NativeChestRewards.Access(chest, assignment.site().worldNamespace(), assignment.site(),
                () -> assigned(level, chest, assignment)
                        && assignment.unlocked(bell -> MirrorCourt.nativeRewardUnlocked(level, bell)));
        var table = ResourceKey.create(Registries.LOOT_TABLE, assignment.kind().table);
        NativeChestRewards.open(player, access, table).ifPresentOrElse(snapshot -> {
            if (!access.valid(player)) return;
            if (player.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
                    new NativeRewardChestMenu(id, inventory, access, snapshot), TITLE)).isPresent()) {
                if (dusk && player.containerMenu instanceof NativeRewardChestMenu menu) {
                    chest.viewerOpened(menu);
                }
                LoreDiscovery.nativeRewardOpened(player, chest.getBlockPos());
            }
        }, () -> {
            if (!assignment.unlocked(bell -> MirrorCourt.nativeRewardUnlocked(level, bell))) {
                player.displayClientMessage(Component.translatable("container.uwudimension.reward_chest.court_locked"), true);
            }
        });
    }

    static final class RewardChestBlock extends HorizontalDirectionalBlock implements EntityBlock {
        private static final MapCodec<RewardChestBlock> CODEC = simpleCodec(RewardChestBlock::new);

        private RewardChestBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override protected MapCodec<RewardChestBlock> codec() {
            return CODEC;
        }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override public NativeRewardChestBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new NativeRewardChestBlockEntity(pos, state);
        }

        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.is(DUSK.get()) && level.getBlockEntity(pos) instanceof NativeRewardChestBlockEntity chest) {
                chest.recheckViewers(level);
            }
        }

        @Override protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int event, int value) {
            if (state.is(DUSK.get()) && level.getBlockEntity(pos) instanceof NativeRewardChestBlockEntity chest) {
                return chest.triggerEvent(event, value);
            }
            return super.triggerEvent(state, level, pos, event, value);
        }

        @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                             BlockHitResult hit) {
            if (player instanceof ServerPlayer serverPlayer
                    && level.getBlockEntity(pos) instanceof NativeRewardChestBlockEntity chest) {
                open(serverPlayer, chest);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
    }

    private NativeRewardChests() {}
}
