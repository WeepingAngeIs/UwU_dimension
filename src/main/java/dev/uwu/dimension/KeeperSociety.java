package dev.uwu.dimension;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Persistent index of real entities. Reading a roster never loads a chunk. */
final class KeeperSociety extends SavedData {
    private static final String TRUCE = "uwudimension_shared_truce";
    private static final String OATH = "uwudimension_fellowship";
    private static final String OFFER = "uwudimension_recruit_offer";
    private static final String EXPIRES = "uwudimension_recruit_until";
    private record Entry(UUID owner, String name, String faction, String job, String realm,
                         BlockPos lastSeen, BlockPos home, boolean settled) {}
    private final Map<UUID, Entry> entries = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(KeeperSociety::commands);
    }

    static boolean hasTruce(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(TRUCE);
    }

    static boolean confirmRecruit(Player player, Keeper keeper) {
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        if (data.hasUUID(OFFER) && data.getUUID(OFFER).equals(keeper.getUUID()) && data.getLong(EXPIRES) > now) {
            data.remove(OFFER);
            data.remove(EXPIRES);
            return true;
        }
        data.putUUID(OFFER, keeper.getUUID());
        data.putLong(EXPIRES, now + 300);
        String faction = keeper.dusk() ? "Dusk" : "Petal";
        String benefit = keeper.dusk() ? "Dusk guards will welcome you." : "Petal neighbors share food and healing.";
        player.sendSystemMessage(Component.literal("Join " + faction + " for four emeralds? " + benefit
                + " This keeper can follow or settle at home. Recruit from one settlement at a time. "
                + "Crouch and use four emeralds on this same keeper again within 15 seconds to agree."));
        return false;
    }

    static String givenName(Keeper keeper) {
        String[] first = keeper.dusk()
                ? new String[]{"Iris", "Aster", "Vesper", "Sable", "Rin", "Lune", "Fenn", "Viola"}
                : new String[]{"Hana", "Momo", "Neri", "Yuzu", "Mio", "Nami", "Aoi", "Ruri"};
        String[] last = {"Reed", "Silk", "Bell", "Pond", "Moon", "Leaf", "Mist", "Bloom"};
        int hash = keeper.getUUID().hashCode();
        return first[hash & 7] + " " + last[(hash >>> 3) & 7];
    }

    private static KeeperSociety data(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(KeeperSociety::new, KeeperSociety::load, null), "uwudimension_keepers");
    }

    static void remember(ServerLevel level, Keeper keeper) {
        if (keeper.ownerId() == null) return;
        KeeperSociety roster = data(level);
        String name = keeper.hasCustomName() ? keeper.getCustomName().getString() : givenName(keeper);
        Entry next = new Entry(keeper.ownerId(), name, keeper.allegiance(), KeeperCraft.of(keeper).title,
                level.dimension().location().toString(), keeper.blockPosition(), keeper.hearth(), keeper.isSettled());
        if (!next.equals(roster.entries.put(keeper.getUUID(), next))) roster.setDirty();
    }

    static void forget(ServerLevel level, UUID id) {
        KeeperSociety roster = data(level);
        if (roster.entries.remove(id) != null) roster.setDirty();
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("keepers")
                .executes(ctx -> list(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("roster").executes(ctx -> list(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("truce")
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayerOrException();
                            player.sendSystemMessage(Component.literal("Reconciliation ends your recruitment oath and grants peaceful passage in both villages. "
                                    + "Your companions stay with you. Use /keepers truce confirm to accept."));
                            return 1;
                        })
                        .then(Commands.literal("confirm").executes(ctx -> truce(ctx.getSource().getPlayerOrException())))));
    }

    private static int truce(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag saved = root.getCompound(Player.PERSISTED_NBT_TAG);
        saved.remove(OATH);
        saved.putBoolean(TRUCE, true);
        root.put(Player.PERSISTED_NBT_TAG, saved);
        root.remove(OFFER);
        root.remove(EXPIRES);
        player.sendSystemMessage(Component.literal("The two villages recognize your truce. You may pledge again when ready."));
        return 1;
    }

    private static int list(ServerPlayer player) {
        String oath = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getString(OATH);
        player.sendSystemMessage(Component.literal("Your settlement: " + (oath.isEmpty() ? "unpledged" : oath)
                + (hasTruce(player) ? " (truce)" : "") + ". Companions:"));
        var roster = data(player.serverLevel()).entries.entrySet().stream()
                .filter(e -> e.getValue().owner().equals(player.getUUID()))
                .sorted(Comparator.comparing(e -> e.getValue().name())).toList();
        for (var row : roster) {
            Entry e = row.getValue();
            boolean loaded = false;
            for (var level : player.server.getAllLevels()) {
                if (level.getEntity(row.getKey()) instanceof Keeper) { loaded = true; break; }
            }
            player.sendSystemMessage(Component.literal(e.name() + " · " + e.job() + " · " + e.faction()
                    + " · " + (e.settled() ? "settled" : "following") + " · " + (loaded ? "loaded" : "unloaded / last seen")
                    + " · " + e.realm() + " " + e.lastSeen().toShortString()
                    + (e.home() == null ? "" : " · home " + e.home().toShortString())));
        }
        if (roster.isEmpty()) player.sendSystemMessage(Component.literal("No recorded companions yet. Meet a keeper with four emeralds."));
        return roster.size();
    }

    private static KeeperSociety load(CompoundTag root, HolderLookup.Provider access) {
        var data = new KeeperSociety();
        for (Tag tag : root.getList("Companions", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) tag;
            if (!row.hasUUID("Id") || !row.hasUUID("Owner")) continue;
            data.entries.put(row.getUUID("Id"), new Entry(row.getUUID("Owner"), row.getString("Name"),
                    row.getString("Faction"), row.getString("Job"), row.getString("Realm"), BlockPos.of(row.getLong("Last")),
                    row.contains("Home") ? BlockPos.of(row.getLong("Home")) : null, row.getBoolean("Settled")));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider access) {
        var list = new ListTag();
        entries.forEach((id, e) -> {
            var row = new CompoundTag();
            row.putUUID("Id", id); row.putUUID("Owner", e.owner()); row.putString("Name", e.name());
            row.putString("Faction", e.faction()); row.putString("Job", e.job()); row.putString("Realm", e.realm());
            row.putLong("Last", e.lastSeen().asLong()); row.putBoolean("Settled", e.settled());
            if (e.home() != null) row.putLong("Home", e.home().asLong());
            list.add(row);
        });
        root.put("Companions", list);
        return root;
    }
}
