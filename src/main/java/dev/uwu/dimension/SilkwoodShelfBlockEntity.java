package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SilkwoodShelfBlockEntity extends BlockEntity implements Clearable {
    public static final int SLOTS = 4;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private boolean ejecting;

    public SilkwoodShelfBlockEntity(BlockPos pos, BlockState state) {
        super(NativeShelf.BLOCK_ENTITY.get(), pos, state);
    }

    public boolean isSlotEmpty(int slot) {
        return slot < 0 || slot >= SLOTS || items.get(slot).isEmpty();
    }

    /** Display consumers receive a snapshot; mutations go through a server interaction. */
    public ItemStack displayItem(int slot) {
        return isSlotEmpty(slot) ? ItemStack.EMPTY : items.get(slot).copy();
    }

    public boolean isCovered() {
        if (level == null) return false;
        BlockPos above = worldPosition.above();
        return SilkwoodShelfBlock.coversDisplay(level, above, level.getBlockState(above));
    }

    boolean transfer(int slot, Player player, InteractionHand hand) {
        if (ejecting || slot < 0 || slot >= SLOTS || !(player instanceof ServerPlayer actor) || actor.serverLevel() != level || isRemoved()) return false;
        ItemStack held = actor.getItemInHand(hand);
        if (held.isEmpty()) {
            if (items.get(slot).isEmpty()) return false;
            actor.setItemInHand(hand, items.set(slot, ItemStack.EMPTY));
        } else {
            if (!items.get(slot).isEmpty()) return false;
            items.set(slot, actor.getAbilities().instabuild ? held.copy() : held.copyAndClear());
        }
        actor.getInventory().setChanged();
        markUpdated();
        return true;
    }

    void ejectUnderCover() {
        if (!(level instanceof ServerLevel server) || isRemoved() || ejecting) return;
        Direction front = getBlockState().getValue(SilkwoodShelfBlock.FACING);
        boolean changed = false;
        ejecting = true;
        try {
            for (int slot = 0; slot < SLOTS && !isRemoved(); slot++) {
                ItemStack stored = items.get(slot);
                if (stored.isEmpty()) continue;
                Direction side = slot % 2 == 0 ? front.getClockWise() : front.getCounterClockWise();
                double y = worldPosition.getY() + (slot < 2 ? .54 : .24);
                ItemEntity drop = new ItemEntity(server, worldPosition.getX() + .5, y, worldPosition.getZ() + .5,
                        stored.copy(), 0, 0, 0);
                if (!fitsAtLip(server, drop, front, side, .20) && !fitsAtLip(server, drop, side, front, .32)) continue;

                items.set(slot, ItemStack.EMPTY);
                boolean spawned = false;
                try {
                    spawned = server.addFreshEntity(drop);
                } finally {
                    if (!spawned) {
                        if (!isRemoved()) items.set(slot, stored);
                        else Containers.dropItemStack(server, worldPosition.getX(), y, worldPosition.getZ(), stored);
                    }
                }
                changed |= spawned;
            }
        } finally {
            ejecting = false;
            if (changed) markUpdated();
        }
    }

    /** Straddle the edge so a wall on the other side cannot trap a dropped stack. */
    private boolean fitsAtLip(ServerLevel server, ItemEntity drop, Direction edge, Direction along, double offset) {
        BlockPos neighbor = worldPosition.relative(edge);
        if (!server.hasChunkAt(neighbor) || drop.getType().isBlockDangerous(server.getBlockState(neighbor))) return false;
        double x = worldPosition.getX() + .5 + .5 * edge.getStepX() + offset * along.getStepX();
        double z = worldPosition.getZ() + .5 + .5 * edge.getStepZ() + offset * along.getStepZ();
        drop.setPos(x, drop.getY(), z);
        return server.getWorldBorder().isWithinBounds(drop.getBoundingBox()) && server.noCollision(drop, drop.getBoundingBox());
    }

    void dropDisplayedItems() {
        if (!(level instanceof ServerLevel server)) return;
        NonNullList<ItemStack> removed = NonNullList.create();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!isSlotEmpty(slot)) removed.add(items.set(slot, ItemStack.EMPTY));
        }
        if (!removed.isEmpty()) {
            markUpdated();
            Containers.dropContents(server, worldPosition, removed);
        }
    }

    @Override
    public void clearContent() {
        items.clear();
        markUpdated();
    }

    private void markUpdated() {
        if (level instanceof ServerLevel server && !isRemoved()) {
            setChanged();
            BlockState state = getBlockState();
            server.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return ContainerHelper.saveAllItems(new CompoundTag(), items, registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
