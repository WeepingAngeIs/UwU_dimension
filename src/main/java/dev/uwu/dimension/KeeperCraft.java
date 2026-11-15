package dev.uwu.dimension;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Local trades use vanilla workstations so the normal work, restock and sleep routines apply. */
enum KeeperCraft {
    BAKER("Baker", "My smoker is warm. Take something for the waterfall walk.", Items.BREAD, Items.PUMPKIN_PIE),
    GARDENER("Gardener", "Young shoots like tilled soil here. I save seed for every season.", Items.BEETROOT_SEEDS, Items.MELON_SEEDS),
    HEALER("Healer", "Pack honey and food before you descend beneath the waterfall.", Items.HONEY_BOTTLE, Items.HONEY_BOTTLE),
    CARTOGRAPHER("Cartographer", "The archive path runs behind the falls. Follow the inlaid lamps.", Items.COMPASS, Items.SPYGLASS),
    HOST("Festival host", "When the village bell rings, we gather in the square.", Items.PINK_CANDLE, Items.PURPLE_CANDLE),
    RESIDENT("Resident", "Our market welcomes travelers from either shore.", Items.PINK_PETALS, Items.SOUL_LANTERN);

    final String title, greeting;
    private final Item petal, dusk;
    KeeperCraft(String title, String greeting, Item petal, Item dusk) {
        this.title = title;
        this.greeting = greeting;
        this.petal = petal;
        this.dusk = dusk;
    }
    Item wares(boolean dark) { return dark ? dusk : petal; }
    static KeeperCraft of(Keeper keeper) {
        VillagerProfession job = keeper.getVillagerData().getProfession();
        return switch (BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).getPath()) {
            case "butcher" -> BAKER;
            case "farmer" -> GARDENER;
            case "cleric" -> HEALER;
            case "cartographer" -> CARTOGRAPHER;
            case "shepherd" -> HOST;
            default -> RESIDENT;
        };
    }
}
