package dev.uwu.dimension.traversal;

import dev.uwu.dimension.Arsenal;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Main-thread input state for one server. Each lease belongs to one player object in one level. */
public final class TraversalInputSessions {
    private static final class Lease {
        final ServerPlayer owner;
        final UUID token = UUID.randomUUID();
        final ItemStack[] hotbar;
        int selectedSlot;
        ItemStack selectedWeapon;

        Lease(ServerPlayer owner, ItemStack[] hotbar) {
            this.owner = owner;
            this.hotbar = hotbar;
            selectedSlot = owner.getInventory().selected;
            selectedWeapon = Inventory.isHotbarSlot(selectedSlot) ? hotbar[selectedSlot] : null;
        }
    }

    private final TraversalRuntime runtime;
    private final Map<ServerLevel, Map<Integer, Lease>> levels = new IdentityHashMap<>();

    public TraversalInputSessions(TraversalRuntime runtime) {
        this.runtime = runtime;
    }

    /** A qualified peer needs a new grant on incarnation change or first equip of an unwitnessed stack. */
    public void offer(ServerPlayer player, Consumer<TraversalInputPacket> send) {
        if (player == null || send == null) return;
        ServerLevel level = player.serverLevel();
        if (!current(level, player)) return;
        Map<Integer, Lease> leases = levels.computeIfAbsent(level, key -> new HashMap<>());
        int id = player.getId();
        Lease previous = leases.get(id);
        if (observeEquipment(previous, player)) return;
        ItemStack[] hotbar = new ItemStack[Inventory.getSelectionSize()];
        for (int slot = 0; slot < hotbar.length; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (traversalItem(stack)) hotbar[slot] = stack;
        }
        Lease lease = new Lease(player, hotbar);
        leases.put(id, lease);
        try {
            send.accept(new TraversalInputPacket(TraversalInputPacket.Command.LEASE, lease.token,
                level.dimension().location(), -1));
        } catch (RuntimeException | Error failure) {
            // A failed send can be retried. Never reinstate a replaced player's lease.
            leases.remove(id, lease);
            if (leases.isEmpty()) levels.remove(level, leases);
            throw failure;
        }
    }

    /** True means the input envelope was accepted. The existing runtime decides whether the skill can activate. */
    public boolean receive(ServerPlayer sender, TraversalInputPacket packet) {
        if (sender == null || packet == null || packet.command() == null) return false;
        ServerLevel level = sender.serverLevel();
        if (!current(level, sender) || !level.dimension().location().equals(packet.dimension())
                || !Inventory.isHotbarSlot(packet.slot()) || packet.slot() != sender.getInventory().selected) return false;
        Map<Integer, Lease> leases = levels.get(level);
        Lease lease = leases == null ? null : leases.get(sender.getId());
        if (lease == null || lease.owner != sender || !lease.token.equals(packet.lease())) return false;
        ItemStack held = sender.getMainHandItem();
        if (held != lease.hotbar[packet.slot()] || held != sender.getInventory().getItem(packet.slot())) return false;
        Arsenal.Relic relic = switch (packet.command()) {
            case ACTIVATE_VESPER -> Arsenal.Relic.VESPER_SCYTHE;
            case TOGGLE_SNOWKISS, ACTIVATE_SNOWKISS -> Arsenal.Relic.FROST_WHIP;
            case LEASE -> null;
        };
        if (relic == null || !held.is(Arsenal.WEAPONS.get(relic).get())) return false;
        TraversalPackets.Action action = packet.command() == TraversalInputPacket.Command.TOGGLE_SNOWKISS
            ? TraversalPackets.Action.TOGGLE : TraversalPackets.Action.ACTIVATE;
        // A new selected slot may already be covered by this grant. Retire the former weapon's state first.
        observeEquipment(lease, sender);
        runtime.request(sender, new TraversalPackets.Request(action));
        return true;
    }

    /** Version-2 peers without the optional channel retain the original receipt-time weapon selection. */
    public boolean receiveLegacy(ServerPlayer sender, TraversalPackets.Request legacy) {
        if (sender == null || legacy == null || legacy.action() == null || !current(sender.serverLevel(), sender)
                || sender.connection == null || !sender.connection.isAcceptingMessages()
                || sender.connection.hasChannel(TraversalInputPacket.TYPE)) return false;
        runtime.request(sender, legacy);
        return true;
    }

    /** Run before the existing runtime level tick so an old weapon cannot pull for an extra tick. */
    public void tick(ServerLevel level) {
        Map<Integer, Lease> leases = levels.get(level);
        if (leases != null) {
            leases.values().removeIf(lease -> !current(level, lease.owner));
            if (leases.isEmpty()) levels.remove(level);
        }
        for (ServerPlayer player : level.players()) {
            Lease lease = leases == null ? null : leases.get(player.getId());
            if (observeEquipment(lease, player)) continue;
            if (player.connection != null && player.connection.isAcceptingMessages()
                    && player.connection.hasChannel(TraversalInputPacket.TYPE)) {
                offer(player, packet -> PacketDistributor.sendToPlayer(player, packet));
            }
        }
    }

    /** Call on departure from the old level, including brief transfers that finish before its next tick. */
    public void forget(ServerLevel oldLevel, ServerPlayer player) {
        Map<Integer, Lease> leases = levels.get(oldLevel);
        if (leases == null || player == null) return;
        int id = player.getId();
        Lease lease = leases.get(id);
        if (lease != null && lease.owner == player) leases.remove(id);
        if (leases.isEmpty()) levels.remove(oldLevel);
    }

    public void unload(ServerLevel level) {
        levels.remove(level);
    }

    /** Observe authoritative selection and report whether the current credential still covers it. */
    private boolean observeEquipment(Lease lease, ServerPlayer player) {
        if (lease == null || lease.owner != player) return false;
        int slot = player.getInventory().selected;
        ItemStack held = player.getMainHandItem();
        ItemStack weapon = Inventory.isHotbarSlot(slot) && traversalItem(held) ? held : null;
        if (weapon != lease.selectedWeapon || (weapon != null && slot != lease.selectedSlot)) {
            // A delayed press for the departed slot must stay invalid when that exact weapon is selected again.
            if (lease.selectedWeapon != null) lease.hotbar[lease.selectedSlot] = null;
            lease.selectedSlot = slot;
            lease.selectedWeapon = weapon;
            runtime.equipmentChanged(player);
        }
        if (weapon == null) {
            // A witnessed weapon left the selected slot. Seeing it there again needs a fresh grant.
            if (Inventory.isHotbarSlot(slot)) lease.hotbar[slot] = null;
            return true;
        }
        return lease.hotbar[slot] == weapon;
    }

    private static boolean traversalItem(ItemStack stack) {
        return stack.is(Arsenal.WEAPONS.get(Arsenal.Relic.FROST_WHIP).get())
                || stack.is(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get());
    }

    private static boolean current(ServerLevel level, ServerPlayer player) {
        return level != null && player.level() == level && player.isAlive() && !player.isRemoved()
            && level.getEntity(player.getId()) == player;
    }
}
