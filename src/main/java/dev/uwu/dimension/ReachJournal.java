package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;

/** A vanilla signed book can be opened, shelved or lent to another traveller. */
final class ReachJournal {
    static final String STAGE = "uwudimension_journal_stage";
    static int chapter(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(STAGE);
    }
    private static void mark(ServerPlayer player, int chapter) {
        CompoundTag saved = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        saved.putInt(STAGE, chapter);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, saved);
    }
    static void arrive(ServerPlayer player) {
        if (chapter(player) == 0) {
            mark(player, 1);
            give(player);
            player.sendSystemMessage(Component.literal("A keeper left you a field journal. Read it beside the bell."));
        }
    }
    static void discover(ServerPlayer player, int next) {
        if (chapter(player) >= next) return;
        mark(player, next);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (journal(stack)) write(stack, next);
        }
        player.sendSystemMessage(Component.literal("A new field sketch appears in your journal. Recover a copy with /sakura journal."));
    }
    static boolean journal(ItemStack stack) {
        return stack.is(Items.WRITTEN_BOOK) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean("uwudimension_journal");
    }
    static void give(ServerPlayer player) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("uwudimension_journal", true);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        write(book, Math.max(1, chapter(player)));
        if (!player.addItem(book)) player.drop(book, false);
    }
    private static Filterable<Component> page(char picture, String heading, String words) {
        var icon = Component.literal(String.valueOf(picture)).withStyle(s -> s.withFont(UwUDimension.id("field_sketches")));
        return Filterable.passThrough(Component.empty().append(icon).append("\n").append(
                Component.literal(heading).withStyle(net.minecraft.ChatFormatting.DARK_PURPLE)).append("\n\n" + words));
    }
    private static void write(ItemStack book, int stage) {
        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(page('A', "The bell and the falls", "From the Petal bell, find the mossy spring. Follow the tideglass through its falling water. The locket remembers the place you left, even if you borrow another."));
        pages.add(page('B', "Kitchen garden", "Till soil here in the Reach. Plant sakura rice and sweetbeans by water. A Heartberry Burger grants Strength II. Bring seeds, lanterns or a hot meal to a village bell to help restore its commons."));
        pages.add(page('C', "The two houses", "Petal and Dusk keep different customs. Ask a keeper about joining before offering four emeralds. Friends can gather at the same village bell to share provisions."));
        pages.add(page('D', "Relics", "Draw the Cupid Bow to befriend a creature. Left-click a foe to direct your allies. A thrown blade returns to its keeper. Read its inscription for the throwing gesture."));
        pages.add(page('E', "Reef lights", "Blush Reef shoals shelter swimmers and water Pokémon. Sneak-use your locket for bearings. Surface lights spread gently. Deep lights gather. Ink among the sparks points toward a pursuer."));
        if (stage >= 2) pages.add(page('E', "Under the falls", "Follow the boardwalk across the bright pool. Turn down the lit steps. The plum mosaic marks the court. Sound its bell together when everyone is ready."));
        if (stage >= 2) pages.add(page('C', "Cutting roseglass", "In pink Sazanami Tuff, Y 10 to 54, an iron pick frees roseglass. Refine the prism in a furnace. Petalweave pieces protect travellers. Look up each piece in your recipe viewer."));
        if (stage >= 2) pages.add(page('B', "Under one roof", "Weave a Petal House banner from a pink banner, pink petals and a roseglass prism. For Dusk House, combine a purple banner, an ink sac and a refined Vesper Opal."));
        if (stage >= 3) pages.add(page('F', "The violet seam", "Search Nightshale at Y -48 to -8 with a diamond pick. Fire the Vesper Opal for Nightbriar armor. A lantern made with both minerals is a gift from both houses."));
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Field Notes from Sakura Reach"),
                "The Petal Cartographers", 0, List.copyOf(pages), true));
    }
}
