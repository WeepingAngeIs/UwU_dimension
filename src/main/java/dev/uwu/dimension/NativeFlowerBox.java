package dev.uwu.dimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

final class NativeFlowerBox {
    private static final String NAME = "silkwood_flower_box";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "uwudimension");
    static final DeferredBlock<SilkwoodFlowerBoxBlock> BOX = BLOCKS.register(NAME,
            () -> new SilkwoodFlowerBoxBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_PLANKS).noOcclusion()));
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SilkwoodFlowerBoxBlockEntity>> BLOCK_ENTITY =
            ENTITIES.register(NAME, () -> BlockEntityType.Builder.of(SilkwoodFlowerBoxBlockEntity::new, BOX.get()).build(null));

    static void register(IEventBus bus) {
        ITEMS.registerSimpleBlockItem(NAME, BOX);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(BOX.get());
        });
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() ->
                ((FireBlock) Blocks.FIRE).setFlammable(BOX.get(), 5, 20)));
    }

    private NativeFlowerBox() {}
}
