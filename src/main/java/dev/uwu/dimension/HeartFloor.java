package dev.uwu.dimension;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HeartFloor {
    private record Family(Supplier<Block> full, Supplier<SlabBlock> slab, Supplier<CarpetBlock> carpet) {}
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    private static final Map<DyeColor, Family> FAMILIES = new EnumMap<>(DyeColor.class);

    public static void register(IEventBus bus) {
        for (DyeColor color : DyeColor.values()) {
            String name = color == DyeColor.PINK ? "heartsill" : color.getName() + "_heartsill";
            Supplier<Block> full = HeartwaterFeature.HEART;
            if (color != DyeColor.PINK) {
                var tile = BLOCKS.registerSimpleBlock(name, stone(color));
                ITEMS.registerSimpleBlockItem(name, tile);
                full = tile;
            }
            var slab = BLOCKS.register(name + "_slab", () -> new SlabBlock(stone(color)));
            var carpet = BLOCKS.register(name + "_carpet", () -> new CarpetBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.PINK_CARPET).mapColor(color).lightLevel(s -> 2)));
            ITEMS.registerSimpleBlockItem(name + "_slab", slab);
            ITEMS.registerSimpleBlockItem(name + "_carpet", carpet);
            FAMILIES.put(color, new Family(full, slab, carpet));
        }
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(HeartFloor::creative);
    }

    private static BlockBehaviour.Properties stone(DyeColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.8F, 6F)
                .sound(SoundType.CALCITE).lightLevel(s -> 5);
    }

    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.COLORED_BLOCKS) return;
        for (Family family : FAMILIES.values()) {
            event.accept(family.full.get());
            event.accept(family.slab.get());
            event.accept(family.carpet.get());
        }
    }

    public static Block full(DyeColor color) { return FAMILIES.get(color).full.get(); }
    public static SlabBlock slab(DyeColor color) { return FAMILIES.get(color).slab.get(); }
    public static CarpetBlock carpet(DyeColor color) { return FAMILIES.get(color).carpet.get(); }

    private HeartFloor() {}
}
