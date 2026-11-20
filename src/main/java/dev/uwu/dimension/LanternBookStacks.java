package dev.uwu.dimension;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

final class LanternBookStacks {
    private static final String TABLE = "/data/uwudimension/loot_table/books/lantern_for_both_shores.json";
    private static final JsonObject BOOK = readBookDefinition();

    private LanternBookStacks() {}

    static void display(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        output.accept(create(parameters.holders()));
    }

    static ItemStack create(HolderLookup.Provider registries) {
        return ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), BOOK).getOrThrow();
    }

    static boolean isLanternBook(ItemStack stack) {
        return stack.is(Items.WRITTEN_BOOK) && stack.has(DataComponents.WRITTEN_BOOK_CONTENT)
                && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                        .getBoolean("uwudimension_lantern_lore");
    }

    // A remote Creative client has no server loot context. Read the fixed item's own
    // component definition from the shipped table, retaining its title, art and links.
    private static JsonObject readBookDefinition() {
        try (var stream = LanternBookStacks.class.getResourceAsStream(TABLE)) {
            if (stream == null) throw new IllegalStateException("Missing bundled book: " + TABLE);
            var reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            var table = JsonParser.parseReader(reader).getAsJsonObject();
            var entry = only(only(table, "pools"), "entries");
            var function = only(entry, "functions");
            if (!"minecraft:item".equals(entry.get("type").getAsString())
                    || !"minecraft:written_book".equals(entry.get("name").getAsString())
                    || !"minecraft:set_components".equals(function.get("function").getAsString())) {
                throw new IllegalStateException("Expected a signed book with fixed components in " + TABLE);
            }
            var book = new JsonObject();
            book.add("id", entry.get("name"));
            book.add("components", function.getAsJsonObject("components"));
            return book;
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read " + TABLE, exception);
        }
    }

    private static JsonObject only(JsonObject parent, String key) {
        var entries = parent.getAsJsonArray(key);
        if (entries.size() != 1) throw new IllegalStateException("Expected one entry in " + TABLE + " / " + key);
        return entries.get(0).getAsJsonObject();
    }
}
