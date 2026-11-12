package dev.uwu.dimension;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Lanterns and short-lived light sources used by the pastel combat effects. */
public final class HeartLanterns {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");

    public static final DeferredBlock<LanternBlock> HEART = BLOCKS.register("heart_neon_lantern", () ->
            new LanternBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(1.0F)
                    .sound(SoundType.LANTERN).noOcclusion().lightLevel(state -> 15)));
    static final DeferredBlock<HeartFlashLightBlock> FLASH = BLOCKS.register("heart_flash_light",
            HeartFlashLightBlock::new);
    private static final DeferredItem<BlockItem> HEART_ITEM = ITEMS.registerSimpleBlockItem("heart_neon_lantern", HEART);

    private HeartLanterns() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(HeartLanterns::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(HEART_ITEM.get());
    }
}
