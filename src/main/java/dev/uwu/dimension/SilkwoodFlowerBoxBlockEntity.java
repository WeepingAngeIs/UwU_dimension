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

public final class SilkwoodFlowerBoxBlockEntity extends BlockEntity implements Clearable {
    public static final int SLOTS = 2;
    private final NonNullList<ItemStack> plants = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private boolean ejecting;

    public SilkwoodFlowerBoxBlockEntity(BlockPos pos, BlockState state) {
        super(NativeFlowerBox.BLOCK_ENTITY.get(), pos, state);
    }

    public boolean isSlotEmpty(int slot) {
        return slot < 0 || slot >= SLOTS || plants.get(slot).isEmpty();
    }

    public ItemStack displayItem(int slot) {
        return isSlotEmpty(slot) ? ItemStack.EMPTY : plants.get(slot).copy();
    }

    public boolean isCovered() {
        if (level == null || !getBlockState().getValue(SilkwoodFlowerBoxBlock.ATTACHED)) return false;
        BlockPos above = worldPosition.above();
        return level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN);
    }

    boolean transfer(int slot, Player player, InteractionHand hand) {
        if (slot < 0 || slot >= SLOTS || ejecting || isRemoved()
                || !(player instanceof ServerPlayer actor) || actor.serverLevel() != level) return false;
        ItemStack held = actor.getItemInHand(hand);
        if (held.isEmpty()) {
            ItemStack stored = plants.get(slot);
            if (stored.isEmpty()) return false;
            actor.setItemInHand(hand, stored.split(1));
            if (stored.isEmpty()) plants.set(slot, ItemStack.EMPTY);
        } else {
            if (!isSlotEmpty(slot) || !SilkwoodFlowerBoxBlock.acceptsPlant(held)) return false;
            plants.set(slot, actor.getAbilities().instabuild ? held.copyWithCount(1) : held.split(1));
        }
        actor.getInventory().setChanged();
        markUpdated();
        return true;
    }

    void ejectUnderCover() {
        if (!(level instanceof ServerLevel server) || isRemoved() || ejecting) return;
        Direction front = getBlockState().getValue(SilkwoodFlowerBoxBlock.FACING);
        boolean changed = false;
        ejecting = true;
        try {
            for (int slot = 0; slot < SLOTS && !isRemoved(); slot++) {
                if (isSlotEmpty(slot)) continue;
                ItemStack stored = plants.get(slot);
                Direction side = slot == 0 ? front.getClockWise() : front.getCounterClockWise();
                ItemEntity drop = new ItemEntity(server, worldPosition.getX() + .5, worldPosition.getY() + .70,
                        worldPosition.getZ() + .5, stored.copy(), 0, 0, 0);
                if (!fitsAtLip(server, drop, front, side) && !fitsAtLip(server, drop, side, front)) continue;
                plants.set(slot, ItemStack.EMPTY);
                boolean spawned = false;
                try {
                    spawned = server.addFreshEntity(drop);
                } finally {
                    if (!spawned) {
                        if (!isRemoved()) plants.set(slot, stored);
                        else Containers.dropItemStack(server, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stored);
                    }
                }
                changed |= spawned;
            }
        } finally {
            ejecting = false;
            if (changed) markUpdated();
        }
    }

    private boolean fitsAtLip(ServerLevel server, ItemEntity drop, Direction edge, Direction along) {
        BlockPos neighbor = worldPosition.relative(edge);
        if (!server.hasChunkAt(neighbor) || drop.getType().isBlockDangerous(server.getBlockState(neighbor))) return false;
        double x = worldPosition.getX() + .5 + .5 * edge.getStepX() + .25 * along.getStepX();
        double z = worldPosition.getZ() + .5 + .5 * edge.getStepZ() + .25 * along.getStepZ();
        drop.setPos(x, drop.getY(), z);
        return server.getWorldBorder().isWithinBounds(drop.getBoundingBox()) && server.noCollision(drop, drop.getBoundingBox());
    }

    void dropPlants() {
        if (!(level instanceof ServerLevel server)) return;
        NonNullList<ItemStack> removed = NonNullList.create();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!isSlotEmpty(slot)) removed.add(plants.set(slot, ItemStack.EMPTY));
        }
        if (!removed.isEmpty()) {
            markUpdated();
            Containers.dropContents(server, worldPosition, removed);
        }
    }

    @Override
    public void clearContent() {
        plants.clear();
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
        plants.clear();
        ContainerHelper.loadAllItems(tag, plants, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, plants, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return ContainerHelper.saveAllItems(new CompoundTag(), plants, registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
