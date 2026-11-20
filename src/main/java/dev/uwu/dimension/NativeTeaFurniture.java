package dev.uwu.dimension;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

final class NativeTeaFurniture {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");

    static final Family SILKWOOD = family("silkwood", Blocks.CHERRY_PLANKS);
    static final Family VELVETWOOD = family("velvetwood", Blocks.DARK_OAK_PLANKS);
    static final Family WOVEN_REED = family("woven_reed", Blocks.BAMBOO_MOSAIC);

    record Family(DeferredBlock<TeaChairBlock> chairHandle, DeferredBlock<TeaTableBlock> tableHandle) {
        TeaChairBlock chair() { return chairHandle.get(); }
        TeaTableBlock table() { return tableHandle.get(); }
    }

    private static Family family(String material, Block wood) {
        String chairId = material + "_tea_chair";
        String tableId = material + "_tea_table";
        var chair = BLOCKS.register(chairId, () -> new TeaChairBlock(BlockBehaviour.Properties.ofFullCopy(wood).noOcclusion()));
        var table = BLOCKS.register(tableId, () -> new TeaTableBlock(BlockBehaviour.Properties.ofFullCopy(wood).noOcclusion()));
        ITEMS.registerSimpleBlockItem(chairId, chair);
        ITEMS.registerSimpleBlockItem(tableId, table);
        return new Family(chair, table);
    }

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
                BLOCKS.getEntries().forEach(block -> event.accept(block.get()));
            }
        });
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            FireBlock fire = (FireBlock) Blocks.FIRE;
            BLOCKS.getEntries().forEach(block -> fire.setFlammable(block.get(), 5, 20));
        }));
    }

    private NativeTeaFurniture() {}
}
