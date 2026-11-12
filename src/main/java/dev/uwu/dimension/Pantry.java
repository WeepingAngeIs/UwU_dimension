package dev.uwu.dimension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Pastel takes on familiar dishes, found in Heartwater's Lootr chests. */
public final class Pantry {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    private static final Map<String, Supplier<Item>> FOOD = new LinkedHashMap<>();

    static {
        food("sweet_berry_cheesecake_slice", 4, 0.5F, 64);
        food("apple_pie_slice", 4, 0.5F, 64);
        food("uwu_pancake_stack", 6, 0.6F, 64);
        food("pink_melon_popsicle", 3, 0.5F, 64);
        food("pink_fruit_salad", 6, 0.7F, 16);
        food("sweet_berry_cookie", 3, 0.4F, 64);
        food("blush_cabbage_roll", 5, 0.6F, 64);
        food("strawberry_milk", 3, 0.4F, 16);
        food("chorus_fruit_pie_slice", 4, 0.5F, 64);
        food("chorus_blossom_tea", 3, 0.4F, 16);
        food("dragon_breath_soda", 3, 0.4F, 16);
        food("shulker_soup", 6, 0.7F, 16);
        food("strider_moss_stew", 8, 0.6F, 16);
        food("nether_fungus_skewer", 5, 0.6F, 64);
        food("stuffed_hoglin_roast", 8, 0.8F, 16);
        food("magma_gelatin", 5, 0.6F, 64);
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

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(Pantry::addToCreativeTab);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            FOOD.values().forEach(item -> event.accept(item.get()));
        }
    }
}
