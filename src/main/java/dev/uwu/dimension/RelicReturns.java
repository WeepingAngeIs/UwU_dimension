package dev.uwu.dimension;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Store the weapon in the same player save as its empty inventory slot. A projectile
 * carries a display copy. Only its owner's live claim can put a weapon into inventory. */
final class RelicReturns {
    private static final String FLIGHTS = "uwudimension.relic_returns";
    private static final String MIGRATED = "uwudimension.legacy_flight_ids";

    private static CompoundTag child(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) parent.put(key, new CompoundTag());
        return parent.getCompound(key);
    }

    private static CompoundTag saved(Player player) {
        return child(player.getPersistentData(), Player.PERSISTED_NBT_TAG);
    }

    static void reserve(Player owner, RelicFlight flight, ItemStack stack, InteractionHand hand) {
        CompoundTag entry = new CompoundTag();
        entry.put("Stack", stack.save(owner.registryAccess()));
        entry.putBoolean("Offhand", hand == InteractionHand.OFF_HAND);
        entry.putString("Level", flight.level().dimension().location().toString());
        entry.putLong("Launched", flight.level().getGameTime());
        child(saved(owner), FLIGHTS).put(flight.getStringUUID(), entry);
    }

    static boolean migrate(Player owner, RelicFlight flight, InteractionHand hand) {
        CompoundTag seen = child(saved(owner), MIGRATED);
        // A legacy entity chunk can be older than the player's last save.
        if (!seen.contains(flight.getStringUUID())) {
            if (flight.getItem().isEmpty()) return false;
            reserve(owner, flight, flight.getItem(), hand);
            seen.putBoolean(flight.getStringUUID(), true);
        }
        return active(owner, flight);
    }

    static boolean active(Player owner, RelicFlight flight) {
        CompoundTag flights = saved(owner).getCompound(FLIGHTS);
        return flights.contains(flight.getStringUUID()) && !flights.getCompound(flight.getStringUUID()).getBoolean("Ready");
    }

    static void returned(Player owner, RelicFlight flight) {
        CompoundTag flights = saved(owner).getCompound(FLIGHTS);
        if (flights.contains(flight.getStringUUID())) {
            flights.getCompound(flight.getStringUUID()).putBoolean("Ready", true);
            deliver(owner, flights, flight.getStringUUID());
        }
    }

    static void recover(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || !player.isAlive()) return;
        CompoundTag root = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!root.contains(FLIGHTS)) return;
        CompoundTag flights = root.getCompound(FLIGHTS);
        for (String key : List.copyOf(flights.getAllKeys())) {
            CompoundTag entry = flights.getCompound(key);
            if (!entry.getBoolean("Ready")) {
                // getEntity only searches loaded entities. A later-loaded visual checks
                // this same claim and disappears before it can hit or deliver again.
                boolean sameLevel = player.level().dimension().location().toString().equals(entry.getString("Level"));
                if (sameLevel && player.serverLevel().getEntity(UUID.fromString(key)) instanceof RelicFlight
                        && player.level().getGameTime() - entry.getLong("Launched") < 400) continue;
                entry.putBoolean("Ready", true);
            }
            deliver(player, flights, key);
        }
        if (flights.isEmpty()) root.remove(FLIGHTS);
    }

    private static void deliver(Player owner, CompoundTag flights, String key) {
        if (!owner.isAlive()) return;
        CompoundTag entry = flights.getCompound(key);
        ItemStack stack = ItemStack.parseOptional(owner.registryAccess(), entry.getCompound("Stack"));
        if (stack.isEmpty()) return;
        InteractionHand hand = entry.getBoolean("Offhand") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (owner.getItemInHand(hand).isEmpty()) {
            owner.setItemInHand(hand, stack);
        } else if (owner.getInventory().getFreeSlot() >= 0) {
            // Physical weapons are unstackable. Do not rely on Inventory.add's
            // creative-mode overflow path, which is allowed to consume an item.
            owner.getInventory().setItem(owner.getInventory().getFreeSlot(), stack);
            owner.getInventory().setChanged();
        } else {
            if (!entry.getBoolean("Notified")) {
                owner.displayClientMessage(Component.literal("Your returning relic is waiting. Free an inventory slot to catch it."), true);
                entry.putBoolean("Notified", true);
            }
            return;
        }
        flights.remove(key);
    }
}
