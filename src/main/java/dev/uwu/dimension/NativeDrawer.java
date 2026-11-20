package dev.uwu.dimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

final class NativeDrawer {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "uwudimension");
    static final DeferredBlock<SilkwoodDrawerBlock> DRAWER = BLOCKS.register("silkwood_drawer",
            () -> new SilkwoodDrawerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL)));
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SilkwoodDrawerBlockEntity>> BLOCK_ENTITY =
            ENTITIES.register("silkwood_drawer", () -> BlockEntityType.Builder.of(SilkwoodDrawerBlockEntity::new, DRAWER.get()).build(null));

    static void register(IEventBus bus) {
        ITEMS.registerSimpleBlockItem("silkwood_drawer", DRAWER);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener((RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLOCK_ENTITY.get(), (drawer, side) -> new InvWrapper(drawer)));
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(DRAWER.get());
        });
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() ->
                ((FireBlock) Blocks.FIRE).setFlammable(DRAWER.get(), 5, 20)));
    }

    private NativeDrawer() {}
}
