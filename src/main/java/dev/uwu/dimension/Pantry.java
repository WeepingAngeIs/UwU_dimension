package dev.uwu.dimension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Pastel foods and hearty meals found in Heartwater's Lootr chests. */
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
        foodWithRemainder("strawberry_milk", 3, 0.4F, 16, Items.GLASS_BOTTLE);
        food("chorus_fruit_pie_slice", 4, 0.5F, 64);
        foodWithRemainder("chorus_blossom_tea", 3, 0.4F, 16, Items.GLASS_BOTTLE);
        foodWithRemainder("dragon_breath_soda", 3, 0.4F, 16, Items.GLASS_BOTTLE);
        foodWithRemainder("shulker_soup", 6, 0.7F, 16, Items.BOWL);
        foodWithRemainder("strider_moss_stew", 8, 0.6F, 16, Items.BOWL);
        food("nether_fungus_skewer", 5, 0.6F, 64);
        food("stuffed_hoglin_roast", 8, 0.8F, 16);
        food("magma_gelatin", 5, 0.6F, 64);

        foodWithRemainder("blossom_ice_cream_bucket", 10, 0.9F, 1, Items.BUCKET);
        food("strawberry_ice_cream_cake", 9, 0.9F, 16);
        food("flower_stuffed_chicken", 13, 1.0F, 1);
        food("heartberry_burger", 11, 0.95F, 16);
        food("blossom_hot_pocket", 8, 0.8F, 16);
        foodWithRemainder("uwu_ramen_bowl", 12, 0.95F, 1, Items.BOWL);
        food("cherry_picnic_bento", 12, 1.0F, 1);
        food("berry_cinnamon_roll", 8, 0.8F, 16);
        food("starberry_waffle_stack", 9, 0.9F, 16);
        foodWithRemainder("creamy_mushroom_stew", 10, 0.9F, 1, Items.BOWL);
        food("loaded_heart_potato", 9, 0.85F, 16);
        food("rose_berry_pie_slice", 8, 0.8F, 16);
        food("pastel_sakura_dumplings", 9, 0.85F, 16);
        food("honey_glazed_ham", 12, 1.0F, 1);
        foodWithRemainder("moonberry_pudding", 8, 0.85F, 16, Items.BOWL);
        food("chicken_flower_pot_pie", 11, 0.95F, 1);

        food("sakura_katsu_curry", 14, 1.0F, 1);
        food("heart_omurice", 12, 1.0F, 1);
        food("cherry_onigiri_bento", 11, 0.95F, 1);
        food("golden_taiyaki", 8, 0.8F, 16);
        foodWithRemainder("cozy_udon_hotpot", 13, 1.0F, 1, Items.BOWL);
        food("sakura_melonpan", 8, 0.8F, 16);
        food("strawberry_shokupan", 9, 0.9F, 16);
        food("sweet_bean_dorayaki", 8, 0.8F, 16);
        food("sakura_mochi_ice_cream", 8, 0.85F, 16);
        foodWithRemainder("maple_ice_cream_sundae", 10, 0.9F, 1, Items.BOWL);
        food("maple_poutine", 14, 1.0F, 1);
        food("tourtiere_hand_pie", 12, 1.0F, 1);
        food("nanaimo_heart_bar", 8, 0.85F, 16);
        food("maple_butter_tart", 8, 0.85F, 16);
        food("saskatoon_berry_pie_slice", 9, 0.9F, 16);
        foodWithRemainder("maple_glazed_salmon_bowl", 12, 0.95F, 1, Items.BOWL);
    }

    private Pantry() {}

    private static void food(String id, int nutrition, float saturation, int stackSize) {
        registerFood(id, nutrition, saturation, stackSize, null);
    }

    private static void foodWithRemainder(String id, int nutrition, float saturation, int stackSize, Item remainder) {
        registerFood(id, nutrition, saturation, stackSize, remainder);
    }

    private static void registerFood(String id, int nutrition, float saturation, int stackSize, Item remainder) {
        FoodBuff buff = buffFor(id);
        FoodProperties.Builder builder = new FoodProperties.Builder()
                .nutrition(nutrition)
                .saturationModifier(saturation)
                .alwaysEdible()
                .effect(() -> new MobEffectInstance(buff.effect(), buff.durationTicks(), buff.amplifier()), 1.0F);
        if (remainder != null) {
            builder.usingConvertsTo(remainder);
        }
        Item.Properties properties = new Item.Properties()
                .food(builder.build())
                .stacksTo(stackSize);
        FOOD.put(id, ITEMS.register(id, () -> new PantryFoodItem(properties, buff.effect(), buff.durationTicks(), buff.amplifier())));
    }

    private static FoodBuff buffFor(String id) {
        return switch (id) {
            case "sweet_berry_cheesecake_slice" -> buff(MobEffects.REGENERATION, 8, 0);
            case "apple_pie_slice" -> buff(MobEffects.MOVEMENT_SPEED, 8, 0);
            case "uwu_pancake_stack" -> buff(MobEffects.JUMP, 12, 0);
            case "pink_melon_popsicle" -> buff(MobEffects.MOVEMENT_SPEED, 8, 0);
            case "pink_fruit_salad" -> buff(MobEffects.JUMP, 12, 0);
            case "sweet_berry_cookie" -> buff(MobEffects.REGENERATION, 8, 0);
            case "blush_cabbage_roll" -> buff(MobEffects.DAMAGE_RESISTANCE, 10, 0);
            case "strawberry_milk" -> buff(MobEffects.MOVEMENT_SPEED, 8, 0);
            case "chorus_fruit_pie_slice" -> buff(MobEffects.JUMP, 8, 0);
            case "chorus_blossom_tea" -> buff(MobEffects.DAMAGE_RESISTANCE, 8, 0);
            case "dragon_breath_soda" -> buff(MobEffects.MOVEMENT_SPEED, 8, 0);
            case "shulker_soup" -> buff(MobEffects.DAMAGE_RESISTANCE, 12, 0);
            case "strider_moss_stew" -> buff(MobEffects.DAMAGE_BOOST, 16, 0);
            case "nether_fungus_skewer" -> buff(MobEffects.DAMAGE_BOOST, 10, 0);
            case "stuffed_hoglin_roast" -> buff(MobEffects.DAMAGE_BOOST, 18, 0);
            case "magma_gelatin" -> buff(MobEffects.JUMP, 10, 0);

            case "blossom_ice_cream_bucket" -> buff(MobEffects.JUMP, 20, 1);
            case "strawberry_ice_cream_cake" -> buff(MobEffects.REGENERATION, 18, 0);
            case "flower_stuffed_chicken" -> buff(MobEffects.DAMAGE_RESISTANCE, 32, 1);
            case "heartberry_burger" -> buff(MobEffects.DAMAGE_BOOST, 24, 1);
            case "blossom_hot_pocket" -> buff(MobEffects.DAMAGE_BOOST, 16, 0);
            case "uwu_ramen_bowl" -> buff(MobEffects.REGENERATION, 28, 1);
            case "cherry_picnic_bento" -> buff(MobEffects.MOVEMENT_SPEED, 28, 1);
            case "berry_cinnamon_roll" -> buff(MobEffects.MOVEMENT_SPEED, 16, 0);
            case "starberry_waffle_stack" -> buff(MobEffects.JUMP, 18, 0);
            case "creamy_mushroom_stew" -> buff(MobEffects.DAMAGE_RESISTANCE, 22, 1);
            case "loaded_heart_potato" -> buff(MobEffects.DAMAGE_RESISTANCE, 18, 0);
            case "rose_berry_pie_slice" -> buff(MobEffects.REGENERATION, 16, 0);
            case "pastel_sakura_dumplings" -> buff(MobEffects.DAMAGE_RESISTANCE, 18, 0);
            case "honey_glazed_ham" -> buff(MobEffects.DAMAGE_BOOST, 28, 1);
            case "moonberry_pudding" -> buff(MobEffects.REGENERATION, 16, 0);
            case "chicken_flower_pot_pie" -> buff(MobEffects.DAMAGE_RESISTANCE, 24, 1);

            case "sakura_katsu_curry" -> buff(MobEffects.DAMAGE_BOOST, 40, 1);
            case "heart_omurice" -> buff(MobEffects.REGENERATION, 28, 1);
            case "cherry_onigiri_bento" -> buff(MobEffects.MOVEMENT_SPEED, 24, 1);
            case "golden_taiyaki" -> buff(MobEffects.DAMAGE_BOOST, 16, 0);
            case "cozy_udon_hotpot" -> buff(MobEffects.DAMAGE_RESISTANCE, 32, 1);
            case "sakura_melonpan" -> buff(MobEffects.MOVEMENT_SPEED, 16, 0);
            case "strawberry_shokupan" -> buff(MobEffects.REGENERATION, 18, 0);
            case "sweet_bean_dorayaki" -> buff(MobEffects.JUMP, 16, 0);
            case "sakura_mochi_ice_cream" -> buff(MobEffects.JUMP, 18, 0);
            case "maple_ice_cream_sundae" -> buff(MobEffects.MOVEMENT_SPEED, 20, 1);
            case "maple_poutine" -> buff(MobEffects.DAMAGE_RESISTANCE, 40, 1);
            case "tourtiere_hand_pie" -> buff(MobEffects.DAMAGE_BOOST, 28, 1);
            case "nanaimo_heart_bar" -> buff(MobEffects.REGENERATION, 16, 0);
            case "maple_butter_tart" -> buff(MobEffects.MOVEMENT_SPEED, 16, 0);
            case "saskatoon_berry_pie_slice" -> buff(MobEffects.REGENERATION, 20, 0);
            case "maple_glazed_salmon_bowl" -> buff(MobEffects.DAMAGE_RESISTANCE, 28, 1);
            default -> throw new IllegalArgumentException("No pantry buff configured for " + id);
        };
    }

    private static FoodBuff buff(Holder<MobEffect> effect, int durationSeconds, int amplifier) {
        return new FoodBuff(effect, durationSeconds * 20, amplifier);
    }

    private record FoodBuff(Holder<MobEffect> effect, int durationTicks, int amplifier) {}

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
