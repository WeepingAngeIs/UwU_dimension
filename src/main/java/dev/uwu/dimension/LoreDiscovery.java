package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Personal early discovery state is stored with the player, not in shared loot. */
final class LoreDiscovery {
    private static final String NATURAL = "uwu_lore_natural_chest";
    private static final String ATTEMPTS = "uwu_lore_chests";
    private static final String PENDING = "uwu_lore_pending";
    private static final String AWARDED = "uwu_lore_awarded";
    private record Candidate(long position, int tick) {}
    private static final java.util.Map<java.util.UUID, Candidate> candidates = new java.util.HashMap<>();

    private LoreDiscovery() {}

    static void chestInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND)
            candidates.remove(player.getUUID());
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()
                || !RealmResidents.applies(player.serverLevel()) || !SakuraAccess.mayInteract(player, event.getPos())) return;
        if (!(player.serverLevel().getBlockState(event.getPos()).getBlock() instanceof ChestBlock)) return;
        BlockEntity chest = player.serverLevel().getBlockEntity(event.getPos());
        if (!(chest instanceof RandomizableContainerBlockEntity)) return;

        // Existing unopened generated loot chests carry a loot table. Keep the marker on
        // the block entity after its first opening so other players can discover it too.
        if (!chest.getPersistentData().getBoolean(NATURAL)) {
            CompoundTag data = chest.saveWithFullMetadata(player.registryAccess());
            if (data.getString("LootTable").isEmpty()) return;
            chest.getPersistentData().putBoolean(NATURAL, true);
            chest.setChanged();
        }

        BlockPos position = event.getPos();
        var state = player.serverLevel().getBlockState(position);
        if (state.hasProperty(ChestBlock.TYPE) && state.hasProperty(ChestBlock.FACING)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = position.relative(ChestBlock.getConnectedDirection(state));
            BlockEntity paired = player.serverLevel().getBlockEntity(other);
            if (paired instanceof RandomizableContainerBlockEntity) {
                paired.getPersistentData().putBoolean(NATURAL, true);
                paired.setChanged();
            }
            if (other.asLong() < position.asLong()) position = other;
        }

        candidates.put(player.getUUID(), new Candidate(position.asLong(), player.tickCount));
    }

    static void containerOpened(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Candidate candidate = candidates.remove(player.getUUID());
        if (candidate == null || candidate.tick() != player.tickCount
                || !(event.getContainer() instanceof net.minecraft.world.inventory.ChestMenu)) return;
        CompoundTag root = player.getPersistentData();
        CompoundTag saved = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (saved.getBoolean(AWARDED) || saved.getBoolean(PENDING)) return;
        long[] previous = saved.getLongArray(ATTEMPTS);
        for (long seen : previous) if (seen == candidate.position()) return;
        if (previous.length >= 2) return;
        long[] attempts = java.util.Arrays.copyOf(previous, previous.length + 1);
        attempts[previous.length] = candidate.position();
        saved.putLongArray(ATTEMPTS, attempts);
        if (player.getRandom().nextFloat() < 0.75F) saved.putBoolean(PENDING, true);
        root.put(Player.PERSISTED_NBT_TAG, saved);
        deliver(player);
    }

    static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0) return;
        candidates.remove(player.getUUID());
        ReachJournal.refresh(player);
        deliver(player);
    }

    private static void deliver(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag saved = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!saved.getBoolean(PENDING) || player.getInventory().getFreeSlot() < 0) return;
        ItemStack book = createBook();
        if (!player.addItem(book)) return;
        saved.putBoolean(PENDING, false);
        saved.putBoolean(AWARDED, true);
        root.put(Player.PERSISTED_NBT_TAG, saved);
        player.sendSystemMessage(Component.literal("You found an old tale, A Lantern for Both Shores."));
    }

    private static Filterable<Component> page(char illustration, String words) {
        Component icon = Component.literal(String.valueOf(illustration))
                .withStyle(style -> style.withFont(UwUDimension.id("lore_sketches")));
        return Filterable.passThrough(Component.empty().append(icon).append("\n\n").append(words));
    }

    private static ItemStack createBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("uwudimension_lantern_lore", true);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(page('A', "By the third night of rain, the bridge was gone. A Petal keeper saved its planks. A Dusk keeper saved its pegs. Each waited on a different bank."));
        pages.add(page('B', "Anya, Goddess of Cupids, learned patience from her Cupid father and how to keep a lamp alive from her succubus mother. She asked who was stranded."));
        pages.add(page('C', "The first plank came from Petal. The first peg came from Dusk. By dawn, their families crossed. Together they hung a lantern where their work met."));
        pages.add(page('D', "When the sky cleared, a glint showed beneath Anya's cloak: ivory wings, pink ribbon, and a golden-rimmed heart. The keepers learned its name: the Cupid Bow."));
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("A Lantern for Both Shores"), "The Petal Cartographers",
                0, List.copyOf(pages), true));
        return book;
    }
}
