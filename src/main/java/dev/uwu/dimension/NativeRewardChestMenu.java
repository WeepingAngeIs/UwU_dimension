package dev.uwu.dimension;

import dev.uwu.dimension.loot.PersonalLootStore;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server menu for an extraction-only reward. The standard chest screen receives slot copies. */
public final class NativeRewardChestMenu extends ChestMenu {
    private static final int REWARDS = PersonalLootStore.SLOT_COUNT;
    private static final int HOTBAR_START = REWARDS + Inventory.INVENTORY_SIZE - Inventory.getSelectionSize();

    private final ServerPlayer owner;
    private final NativeChestRewards.Access access;
    private final RewardView view;
    private long revision;

    NativeRewardChestMenu(int id, Inventory inventory, NativeChestRewards.Access access,
                          PersonalLootStore.Snapshot snapshot) {
        this(id, inventory, access, snapshot, new RewardView(snapshot.slots()));
    }

    private NativeRewardChestMenu(int id, Inventory inventory, NativeChestRewards.Access access,
                                  PersonalLootStore.Snapshot snapshot, RewardView view) {
        super(MenuType.GENERIC_9x3, id, inventory, view, 3);
        this.owner = (ServerPlayer) inventory.player;
        this.access = access;
        this.view = view;
        this.revision = snapshot.revision();
    }

    private static final class RewardView extends SimpleContainer {
        RewardView(List<ItemStack> stacks) {
            super(stacks.toArray(ItemStack[]::new));
        }

        void refresh(List<ItemStack> stacks) {
            for (int slot = 0; slot < REWARDS; slot++) setItem(slot, stacks.get(slot));
        }
    }

    @Override
    protected Slot addSlot(Slot slot) {
        if (!(slot.container instanceof RewardView rewards)) return super.addSlot(slot);
        return super.addSlot(new Slot(rewards, slot.getContainerSlot(), slot.x, slot.y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && access.valid(owner);
    }

    @Override
    public boolean isValidSlotIndex(int slot) {
        return slot == -1 || slot == -999 || slot >= 0 && slot < slots.size();
    }

    @Override
    public void clicked(int slot, int button, ClickType click, Player player) {
        if (player != owner || owner.containerMenu != this || !isValidSlotIndex(slot) || !stillValid(player)) return;
        if (slot < 0 || slot >= REWARDS) {
            super.clicked(slot, button, click, player);
            return;
        }
        // The real reward never enters the carried slot, a swap, a drop or a drag.
        if ((click == ClickType.PICKUP || click == ClickType.QUICK_MOVE)
                && (button == 0 || button == 1) && getCarried().isEmpty()) {
            claim(slot, click == ClickType.PICKUP && button == 1);
        }
    }

    private void claim(int slot, boolean half) {
        var current = NativeChestRewards.readCurrent(owner, access);
        if (current.isEmpty()) return;
        var snapshot = current.get();
        List<ItemStack> stacks = snapshot.slots();
        if (snapshot.revision() != revision) {
            view.refresh(stacks);
            revision = snapshot.revision();
            return;
        }
        ItemStack stack = stacks.get(slot);
        if (stack.isEmpty()) return;
        int amount = half ? (stack.getCount() + 1) / 2 : stack.getCount();
        int matched = claimIntoInventory(slot, stack, amount, true);
        int newStacks = claimIntoInventory(slot, stack, amount - matched, false);
        int total = matched + newStacks;
        if (total > 0) view.setItem(slot, stack.copyWithCount(stack.getCount() - total));
    }

    private int claimIntoInventory(int slot, ItemStack reward, int amount, boolean merge) {
        Inventory inventory = owner.getInventory();
        int total = 0;
        for (int to = 0; to < Inventory.INVENTORY_SIZE && total < amount; to++) {
            ItemStack target = inventory.getItem(to);
            if (merge ? (target.isEmpty() || !ItemStack.isSameItemSameComponents(target, reward)
                    || target.getCount() >= inventory.getMaxStackSize(reward)) : !target.isEmpty()) continue;

            int withdrawn = NativeChestRewards.withdraw(owner, access, slot, to, amount - total, revision);
            if (withdrawn > 0) {
                total += withdrawn;
                revision++;
            }
        }
        return total;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int from) {
        // Player shift-clicks may reorganize the player's own slots, never the reward display.
        if (player != owner || owner.containerMenu != this || !stillValid(player)
                || from < REWARDS || from >= slots.size()) return ItemStack.EMPTY;
        Slot slot = getSlot(from);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack current = slot.getItem();
        ItemStack original = current.copy();
        boolean moved = from < HOTBAR_START
                ? moveItemStackTo(current, HOTBAR_START, slots.size(), false)
                : moveItemStackTo(current, REWARDS, HOTBAR_START, false);
        if (!moved) return ItemStack.EMPTY;
        if (current.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot.container instanceof RewardView) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot.container instanceof RewardView) && super.canDragTo(slot);
    }
}
