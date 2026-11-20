package dev.uwu.dimension;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

final class LanternStorybook {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "uwudimension");
    static final DeferredBlock<LanternStorybookBlock> BLOCK = BLOCKS.register("lantern_storybook",
            () -> new LanternStorybookBlock(BlockBehaviour.Properties.of().strength(0.3F)
                    .sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)));
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LanternStorybookEntity>> ENTITY =
            ENTITIES.register("lantern_storybook",
                    () -> BlockEntityType.Builder.of(LanternStorybookEntity::new, BLOCK.get()).build(null));

    private LanternStorybook() {}

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(LanternStorybook::network);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LanternStorybook::place);
    }

    private static BlockState displayState(Direction readerSide, boolean dusk) {
        return BLOCK.get().defaultBlockState().setValue(LanternStorybookBlock.FACING, readerSide)
                .setValue(LanternStorybookBlock.EDITION,
                        dusk ? LanternStorybookBlock.Edition.DUSK : LanternStorybookBlock.Edition.PETAL);
    }

    // The authored style-zero home already has a low bookshelf. This adds its book
    // during new-house decoration, using the same local coordinates on either street side.
    static void furnishReadingHouse(HeartwaterFeature.Valley valley, BlossomVillage.House house, boolean dusk) {
        var level = valley.level.getLevel();
        if (house.style() != 0 || !ReefLife.isSettlementRealm(level) || SettlementLayout.historical(level, valley.site)) return;
        int x = house.x() - house.width() + 1;
        int z = house.z() + house.depth() - 1;
        if (!valley.inside(x, z)) return;
        int y = house.floor() + 2;
        var state = displayState(valley.face(Direction.EAST), dusk);
        if (valley.planning()) {
            valley.put(x, y, z, state);
            return;
        }
        var target = valley.site.pos(x, y, z);
        if (!valley.chunk.getBlockState(target).isAir()
                || !valley.chunk.getBlockState(target.below()).is(Blocks.BOOKSHELF)) return;
        // Stage the block entity together with the block, including in a proto chunk.
        valley.level.setBlock(target, state, Block.UPDATE_CLIENTS);
    }

    private static void place(UseItemOnBlockEvent event) {
        if (event.getUsePhase() != UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK
                || event.getFace() != Direction.UP) return;
        var player = event.getPlayer();
        if (player == null || player.isSpectator() || !player.isSecondaryUseActive()
                || !player.mayBuild() || !LanternBookStacks.isLanternBook(event.getItemStack())) return;

        var level = event.getLevel();
        var support = event.getPos();
        var base = level.getBlockState(support);
        if (base.getBlock() instanceof LecternBlock) return;
        var target = support.above();
        var book = event.getItemStack();
        if (level.isOutsideBuildHeight(target) || !level.getWorldBorder().isWithinBounds(target)
                || !level.isEmptyBlock(target) || !player.mayUseItemAt(target, Direction.UP, book)) return;
        var state = displayState(player.getDirection().getOpposite(), base.is(MoonveilWood.PLANKS.get()));
        if (!state.canSurvive(level, target) || !level.isUnobstructed(state, target, CollisionContext.of(player))) return;
        if (level.isClientSide) {
            event.cancelWithResult(ItemInteractionResult.SUCCESS);
            return;
        }
        if (!(player instanceof ServerPlayer owner) || !SakuraAccess.mayInteract(owner, target)) {
            event.cancelWithResult(ItemInteractionResult.FAIL);
            return;
        }

        // ITEM_AFTER_BLOCK runs before NeoForge starts its normal Item.useOn snapshot.
        // Delay neighbor updates and consumption until protection listeners approve this placement.
        var before = BlockSnapshot.create(level.dimension(), level, target, Block.UPDATE_ALL);
        if (!level.setBlock(target, state, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_INVISIBLE)) {
            event.cancelWithResult(ItemInteractionResult.FAIL);
            return;
        }
        boolean accepted = false;
        try {
            if (level.getBlockEntity(target) instanceof LanternStorybookEntity entity) {
                entity.store(book);
                accepted = !EventHooks.onBlockPlace(owner, before, Direction.UP)
                        && level.getBlockEntity(target) == entity
                        && owner.getItemInHand(event.getHand()) == book && !book.isEmpty();
            }
        } finally {
            if (!accepted) before.restore(Block.UPDATE_ALL);
        }
        if (!accepted) {
            event.cancelWithResult(ItemInteractionResult.FAIL);
            return;
        }
        book.consume(1, owner);
        level.markAndNotifyBlock(target, level.getChunkAt(target), before.getState(), state, Block.UPDATE_ALL, Block.UPDATE_LIMIT);
        level.playSound(null, target, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(GameEvent.BLOCK_PLACE, target, GameEvent.Context.of(owner, state));
        event.cancelWithResult(ItemInteractionResult.CONSUME);
    }

    record OpenContents(WrittenBookContent content) implements CustomPacketPayload {
        static final Type<OpenContents> TYPE = new Type<>(UwUDimension.id("open_lantern_storybook"));
        @Override public Type<OpenContents> type() { return TYPE; }
    }

    private static void network(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(OpenContents.TYPE,
                WrittenBookContent.STREAM_CODEC.map(OpenContents::new, OpenContents::content),
                (message, context) -> LanternStorybookClient.open(message.content()));
    }

    static void open(ServerPlayer reader, ItemStack book) {
        var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content != null) PacketDistributor.sendToPlayer(reader, new OpenContents(content));
    }
}
