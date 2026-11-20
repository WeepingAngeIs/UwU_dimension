package dev.uwu.dimension.loot;

import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Personal reward inventory persisted with its owner. Call on the logical server thread. */
public final class PersonalLootStore {
    public static final int SLOT_COUNT = 27;
    private static final String STORE = "uwudimension.personal_loot";
    private static final String VERSION = "Version";
    private static final String OWNER = "Owner";
    private static final String SITES = "Sites";
    private static final String REVISION = "Revision";
    private static final String SLOTS = "Slots";
    private static final int FORMAT = 1;
    private static final ThreadLocal<Set<UUID>> ACTIVE = ThreadLocal.withInitial(HashSet::new);

    public record Snapshot(List<ItemStack> slots, long revision, boolean initializedNow) {
        public Snapshot {
            slots = copySlots(slots);
        }

        @Override
        public List<ItemStack> slots() {
            return copySlots(slots);
        }
    }

    private record Location(CompoundTag persisted, CompoundTag sites) {
        CompoundTag entry(UUID site) {
            return sites == null ? null : compound(sites, site.toString());
        }
    }

    public static Snapshot open(CompoundTag ownerData, UUID owner, NativeRewardSite site,
                                Supplier<List<ItemStack>> roll, HolderLookup.Provider registries) {
        return withOwner(owner, () -> {
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);
            UUID siteId = site.id();
            CompoundTag entry = locate(ownerData, owner).entry(siteId);
            if (entry != null) return decodeEntry(entry, ops);

            List<ItemStack> rolled = roll.get();
            if (rolled == null || rolled.size() > SLOT_COUNT) throw invalid("wrong rolled slot count");
            List<ItemStack> slots = new ArrayList<>(SLOT_COUNT);
            for (ItemStack stack : rolled) {
                if (stack == null) throw invalid("roll returned a null stack");
                slots.add(stack.copy());
            }
            while (slots.size() < SLOT_COUNT) slots.add(ItemStack.EMPTY);

            ListTag encoded = new ListTag();
            for (ItemStack stack : slots) encoded.add(encodeSlot(stack, ops));
            Snapshot snapshot = new Snapshot(slots, 1L, true);
            commit(ownerData, owner, siteId, null, newEntry(1L, encoded));
            return snapshot;
        });
    }

    public static Snapshot readExisting(CompoundTag ownerData, UUID owner, NativeRewardSite site,
                                        HolderLookup.Provider registries) {
        return withOwner(owner, () -> decodeEntry(requireEntry(ownerData, owner, site.id()),
                registries.createSerializationContext(NbtOps.INSTANCE)));
    }

    public static ItemStack take(CompoundTag ownerData, UUID owner, NativeRewardSite site,
                                 int slot, int maxCount, long expectedRevision, HolderLookup.Provider registries) {
        if (slot < 0 || slot >= SLOT_COUNT || maxCount <= 0 || expectedRevision < 1) return ItemStack.EMPTY;
        return withOwner(owner, () -> {
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);
            UUID siteId = site.id();
            CompoundTag previous = requireEntry(ownerData, owner, siteId).copy();
            Snapshot snapshot = decodeEntry(previous, ops);
            if (snapshot.revision() != expectedRevision) return ItemStack.EMPTY;
            ItemStack existing = snapshot.slots.get(slot);
            if (existing.isEmpty()) return ItemStack.EMPTY;
            if (snapshot.revision() == Long.MAX_VALUE) throw invalid("reward revision overflow");

            int removedCount = Math.min(maxCount, existing.getCount());
            ItemStack removed = existing.copyWithCount(removedCount);
            ItemStack remainder = existing.copyWithCount(existing.getCount() - removedCount);
            ListTag slots = ((ListTag) previous.get(SLOTS)).copy();
            slots.set(slot, encodeSlot(remainder, ops));
            commit(ownerData, owner, siteId, previous, newEntry(snapshot.revision() + 1, slots));
            return removed;
        });
    }

    private static CompoundTag requireEntry(CompoundTag ownerData, UUID owner, UUID site) {
        CompoundTag entry = locate(ownerData, owner).entry(site);
        if (entry == null) throw invalid("reward site has not been initialized");
        return entry;
    }

    private static Location locate(CompoundTag ownerData, UUID owner) {
        CompoundTag persisted = compound(ownerData, Player.PERSISTED_NBT_TAG);
        CompoundTag store = persisted == null ? null : compound(persisted, STORE);
        if (store == null) return new Location(persisted, null);

        if (!store.contains(VERSION, Tag.TAG_INT) || store.getInt(VERSION) != FORMAT) {
            throw invalid("unknown reward store format");
        }
        if (!store.hasUUID(OWNER) || !owner.equals(store.getUUID(OWNER))) {
            throw invalid("reward store belongs to a different or malformed owner");
        }
        CompoundTag sites = compound(store, SITES);
        if (sites == null) throw invalid("missing reward sites");
        return new Location(persisted, sites);
    }

    private static CompoundTag compound(CompoundTag parent, String key) {
        Tag value = parent.get(key);
        if (value == null) return null;
        if (value instanceof CompoundTag compound) return compound;
        throw invalid("expected compound at " + key);
    }

    private static Snapshot decodeEntry(CompoundTag entry, DynamicOps<Tag> ops) {
        if (!entry.contains(REVISION, Tag.TAG_LONG) || entry.getLong(REVISION) < 1) {
            throw invalid("invalid reward revision");
        }
        if (!(entry.get(SLOTS) instanceof ListTag encoded) || encoded.size() != SLOT_COUNT) {
            throw invalid("wrong stored slot count");
        }
        List<ItemStack> slots = new ArrayList<>(SLOT_COUNT);
        for (Tag value : encoded) {
            if (!(value instanceof CompoundTag stored)) throw invalid("invalid stored reward slot");
            slots.add(stored.isEmpty() ? ItemStack.EMPTY : decodeSlot(stored, ops));
        }
        return new Snapshot(slots, entry.getLong(REVISION), false);
    }

    private static ItemStack decodeSlot(CompoundTag encoded, DynamicOps<Tag> ops) {
        ItemStack stack = ItemStack.STRICT_CODEC.parse(ops, encoded.copy()).result()
                .orElseThrow(() -> invalid("could not decode a reward item"));
        if (stack.isEmpty()) throw invalid("encoded reward item was empty");
        return stack;
    }

    private static CompoundTag encodeSlot(ItemStack stack, DynamicOps<Tag> ops) {
        if (stack.isEmpty()) return new CompoundTag();
        Tag result = ItemStack.STRICT_CODEC.encodeStart(ops, stack.copy()).result()
                .orElseThrow(() -> invalid("could not encode a reward item"));
        if (!(result instanceof CompoundTag encoded) || encoded.isEmpty()) {
            throw invalid("reward item codec did not produce an item compound");
        }
        if (!ItemStack.matches(stack, decodeSlot(encoded, ops))) {
            throw invalid("reward item did not round-trip");
        }
        return encoded;
    }

    private static CompoundTag newEntry(long revision, ListTag slots) {
        CompoundTag entry = new CompoundTag();
        entry.putLong(REVISION, revision);
        entry.put(SLOTS, slots);
        return entry;
    }

    private static void commit(CompoundTag ownerData, UUID owner, UUID site, CompoundTag previous, CompoundTag next) {
        // The roll/codec can run callbacks. Re-read the destination to retain unrelated persisted data.
        Location current = locate(ownerData, owner);
        if (!Objects.equals(current.entry(site), previous)) throw invalid("reward changed during transaction");
        if (current.sites() != null) {
            current.sites().put(site.toString(), next);
            return;
        }
        CompoundTag sites = new CompoundTag();
        sites.put(site.toString(), next);
        CompoundTag store = new CompoundTag();
        store.putInt(VERSION, FORMAT);
        store.putUUID(OWNER, owner);
        store.put(SITES, sites);
        if (current.persisted() != null) current.persisted().put(STORE, store);
        else {
            CompoundTag persisted = new CompoundTag();
            persisted.put(STORE, store);
            ownerData.put(Player.PERSISTED_NBT_TAG, persisted);
        }
    }

    private static <T> T withOwner(UUID owner, Supplier<T> operation) {
        Set<UUID> active = ACTIVE.get();
        if (!active.add(Objects.requireNonNull(owner))) throw invalid("reentrant reward access for one owner");
        try {
            return operation.get();
        } finally {
            active.remove(owner);
            if (active.isEmpty()) ACTIVE.remove();
        }
    }

    private static List<ItemStack> copySlots(List<ItemStack> slots) {
        return slots.stream().map(ItemStack::copy).toList();
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Personal loot: " + reason);
    }

    private PersonalLootStore() {}
}
