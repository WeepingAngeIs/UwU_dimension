package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SilkwoodDrawerBlockEntity extends RandomizableContainerBlockEntity {
    private static final int SLOTS = 27;
    private NonNullList<ItemStack> slots = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    private final ContainerOpenersCounter viewers = new ContainerOpenersCounter() {
        @Override
        protected boolean isOwnContainer(Player player) {
            return !player.isSpectator() && player.containerMenu instanceof ChestMenu menu
                    && menu.getContainer() == SilkwoodDrawerBlockEntity.this;
        }

        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            moveDrawer(level, pos, state, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            moveDrawer(level, pos, state, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int current) {}
    };

    public SilkwoodDrawerBlockEntity(BlockPos pos, BlockState state) {
        super(NativeDrawer.BLOCK_ENTITY.get(), pos, state);
    }

    private static void moveDrawer(Level level, BlockPos pos, BlockState state, boolean open) {
        if (state.getValue(SilkwoodDrawerBlock.OPEN) == open) return;
        Direction front = state.getValue(SilkwoodDrawerBlock.FACING);
        level.playSound(null, pos.getX() + .5 + front.getStepX() * .5, pos.getY() + .5,
                pos.getZ() + .5 + front.getStepZ() * .5, open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE,
                SoundSource.BLOCKS, .5F, .9F + level.random.nextFloat() * .1F);
        level.setBlock(pos, state.setValue(SilkwoodDrawerBlock.OPEN, open), Block.UPDATE_ALL);
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return slots;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        slots = items;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, slots, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        slots = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            setLootTable(null);
            setLootTableSeed(0);
            ContainerHelper.loadAllItems(tag, slots, registries);
        }
    }

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public boolean canOpen(Player player) {
        if (player instanceof ServerPlayer actor && (actor.serverLevel() != level || !SakuraAccess.mayInteract(actor, worldPosition))) {
            return false;
        }
        return super.canOpen(player);
    }

    @Override
    protected AbstractContainerMenu createMenu(int syncId, Inventory inventory) {
        return ChestMenu.threeRows(syncId, inventory, this);
    }

    @Override
    public void startOpen(Player player) {
        if (level != null && !level.isClientSide && !isRemoved() && !player.isSpectator()) {
            viewers.incrementOpeners(player, level, worldPosition, getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (level != null && !level.isClientSide && !isRemoved() && !player.isSpectator() && viewers.getOpenerCount() > 0) {
            viewers.decrementOpeners(player, level, worldPosition, getBlockState());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && getBlockState().getValue(SilkwoodDrawerBlock.OPEN)) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    void recheckOpen() {
        if (level == null || level.isClientSide || isRemoved()) return;
        viewers.recheckOpeners(level, worldPosition, getBlockState());
        // A saved open face can outlive the menu that opened it when the world closes.
        BlockState state = getBlockState();
        if (viewers.getOpenerCount() == 0 && state.getValue(SilkwoodDrawerBlock.OPEN)) {
            level.setBlock(worldPosition, state.setValue(SilkwoodDrawerBlock.OPEN, false), Block.UPDATE_ALL);
        }
    }
}
