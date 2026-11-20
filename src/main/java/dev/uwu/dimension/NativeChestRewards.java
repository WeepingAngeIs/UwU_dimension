package dev.uwu.dimension;

import com.mojang.logging.LogUtils;
import dev.uwu.dimension.loot.NativeRewardSite;
import dev.uwu.dimension.loot.PersonalLootStore;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Server-side storage access for the future generated chest and its menu. */
final class NativeChestRewards {
    // The block supplies the world namespace and site from authoritative generation data.
    // The menu must check its own identity and the site's seal on each interaction.
    record Access(BlockEntity chest, UUID worldNamespace, NativeRewardSite site, BooleanSupplier permitsUse) {
        boolean valid(ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            var pos = chest.getBlockPos();
            return level.getServer().isSameThread() && chest.getLevel() == level && !chest.isRemoved()
                    && site.matches(worldNamespace, level.dimension().location(), pos)
                    && player.isAlive() && !player.isSpectator() && player.canInteractWithBlock(pos, 1)
                    && level.hasChunkAt(pos) && level.getBlockEntity(pos) == chest
                    && SakuraAccess.mayInteract(player, pos) && permitsUse.getAsBoolean();
        }
    }

    static Optional<PersonalLootStore.Snapshot> open(ServerPlayer player, Access access, ResourceKey<LootTable> table) {
        if (!access.valid(player)) return Optional.empty();
        PersonalLootStore.Snapshot result;
        try {
            result = PersonalLootStore.open(player.getPersistentData(), player.getUUID(), access.site(),
                    () -> roll(player, access, table), player.registryAccess());
        } catch (RuntimeException failure) {
            LogUtils.getLogger().warn("Unable to open personal loot site {} for {}", access.site().id(), player.getUUID(), failure);
            return Optional.empty();
        }
        if (result.initializedNow()) CriteriaTriggers.GENERATE_LOOT.trigger(player, table);
        return Optional.of(result);
    }

    private static List<ItemStack> roll(ServerPlayer player, Access access, ResourceKey<LootTable> key) {
        if (!key.location().getNamespace().equals("uwudimension") || !key.location().getPath().startsWith("chests/")) {
            throw new IllegalArgumentException("Expected an UwU chest loot table: " + key.location());
        }
        ServerLevel level = player.serverLevel();
        var table = level.getServer().reloadableRegistries().get().lookupOrThrow(Registries.LOOT_TABLE)
                .get(key).orElseThrow(() -> new IllegalStateException("Unavailable chest loot table: " + key.location()));
        var params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(access.chest().getBlockPos()))
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck()).create(LootContextParamSets.CHEST);
        var slots = new SimpleContainer(PersonalLootStore.SLOT_COUNT);
        UUID site = access.site().id();
        UUID owner = player.getUUID();
        byte[] seedInput = ByteBuffer.allocate(32).putLong(site.getMostSignificantBits()).putLong(site.getLeastSignificantBits())
                .putLong(owner.getMostSignificantBits()).putLong(owner.getLeastSignificantBits()).array();
        long seed = UUID.nameUUIDFromBytes(seedInput).getMostSignificantBits();
        table.value().fill(slots, params, seed == 0 ? 1L : seed);
        return slots.getItems();
    }

    static int withdraw(ServerPlayer player, Access access, int from, int to, int requested, long revision) {
        if (!access.valid(player)) return 0;
        try {
            return transfer(player.getPersistentData(), player.getUUID(), access.site(), player.registryAccess(),
                    player.getInventory(), from, to, requested, revision);
        } catch (IllegalStateException failure) {
            LogUtils.getLogger().warn("Unable to withdraw from personal loot site {} for {}", access.site().id(), player.getUUID(), failure);
            return 0;
        }
    }

    static Optional<PersonalLootStore.Snapshot> readCurrent(ServerPlayer player, Access access) {
        if (!access.valid(player)) return Optional.empty();
        try {
            return Optional.of(PersonalLootStore.readExisting(player.getPersistentData(), player.getUUID(),
                    access.site(), player.registryAccess()));
        } catch (IllegalStateException failure) {
            LogUtils.getLogger().warn("Unable to read personal loot site {} for {}", access.site().id(), player.getUUID(), failure);
            return Optional.empty();
        }
    }

    // Production only transfers to a main inventory slot of the same server player. Both sides
    // serialize in that player's save. Do not transfer through a cursor, item entity, or hopper.
    static int transfer(CompoundTag data, UUID owner, NativeRewardSite site, HolderLookup.Provider registries,
                        Inventory destination, int from, int to, int requested, long revision) {
        if (requested <= 0 || from < 0 || from >= PersonalLootStore.SLOT_COUNT
                || to < 0 || to >= Inventory.INVENTORY_SIZE) return 0;
        var current = PersonalLootStore.readExisting(data, owner, site, registries);
        if (current.revision() != revision) return 0;
        ItemStack reward = current.slots().get(from);
        ItemStack held = destination.getItem(to);
        if (reward.isEmpty() || !destination.canPlaceItem(to, reward)
                || (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, reward))) return 0;
        int count = Math.min(Math.min(requested, reward.getCount()), destination.getMaxStackSize(reward) - held.getCount());
        if (count <= 0) return 0;
        ItemStack updated = reward.copyWithCount(held.getCount() + count);
        ItemStack taken = PersonalLootStore.take(data, owner, site, from, count, revision, registries);
        if (taken.isEmpty()) return 0;
        destination.setItem(to, updated);
        destination.setChanged();
        return count;
    }

    private NativeChestRewards() {}
}
