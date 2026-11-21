package dev.uwu.dimension.lush;

import net.minecraft.core.BlockPos;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** A real food item, separate from both the vanilla berry and its local Jade caption. */
public final class UwUCaveBerries {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    public static final DeferredItem<Item> BERRY = ITEMS.registerSimpleItem("uwu_berry",
            new Item.Properties().food(Foods.GLOW_BERRIES));

    private UwUCaveBerries() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(UwUCaveBerries::creative);
    }

    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) event.accept(BERRY.get());
    }

    public static ItemStack harvest(Level level, BlockPos pos, ItemStack vanilla) {
        return vanilla.is(Items.GLOW_BERRIES) && LushCaveScope.contains(level, pos)
                ? new ItemStack(BERRY.get(), vanilla.getCount()) : vanilla;
    }
}
