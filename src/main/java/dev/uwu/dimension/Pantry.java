package dev.uwu.dimension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Cute dimension snacks and keepsakes, found in Heartwater's Lootr chests. */
public final class Pantry {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    private static final Map<String, Supplier<Item>> FOOD = new LinkedHashMap<>();
    private static final Map<String, Supplier<Item>> KEEPSAKES = new LinkedHashMap<>();

    static {
        food("peach_blossom_mochi", 4, 0.6F, 64);
        food("strawberry_cream_cake", 6, 0.7F, 64);
        food("heart_cookie", 3, 0.4F, 64);
        food("strawberry_milk", 4, 0.6F, 16);
        food("custard_bun", 5, 0.6F, 64);
        food("pink_starfruit", 4, 0.6F, 64);
        food("moonberry", 2, 0.3F, 64);
        food("sunberry", 3, 0.4F, 64);
        food("lavender_dewberry", 2, 0.3F, 64);
        food("aqua_cherries", 2, 0.3F, 64);
        food("petal_jam", 4, 0.6F, 16);
        food("fairy_dew", 2, 0.4F, 16);

        keepsake("blossom_lantern_charm");
        keepsake("petal_parasol");
        keepsake("shrine_bell_charm");
        keepsake("seashell_picnic_basket");
    }

    private Pantry() {}

    private static void food(String id, int nutrition, float saturation, int stackSize) {
        FoodProperties food = new FoodProperties.Builder()
                .nutrition(nutrition)
                .saturationModifier(saturation)
                .build();
        Item.Properties properties = new Item.Properties().food(food).stacksTo(stackSize);
        FOOD.put(id, ITEMS.register(id, () -> new Item(properties)));
    }

    private static void keepsake(String id) {
        Item.Properties properties = new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(1);
        KEEPSAKES.put(id, ITEMS.register(id, () -> new Item(properties)));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(Pantry::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            FOOD.values().forEach(item -> event.accept(item.get()));
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            KEEPSAKES.values().forEach(item -> event.accept(item.get()));
        }
    }
}
