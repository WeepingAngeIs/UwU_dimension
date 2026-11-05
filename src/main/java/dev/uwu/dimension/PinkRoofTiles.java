package dev.uwu.dimension;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

final class PinkRoofTiles {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    private record Tiles(DeferredBlock<Block> full, DeferredBlock<SlabBlock> slab, DeferredBlock<StairBlock> stair) {}
    private static final Tiles PETAL = family("sakura");
    private static final Tiles DUSK = family("mulberry");
    private static Tiles family(String glaze) {
        String name=glaze+"_roof_tile";
        var tile=BLOCKS.registerSimpleBlock(name,BlockBehaviour.Properties.ofFullCopy(Blocks.TERRACOTTA));
        var slab=BLOCKS.register(name+"_slab",()->new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BRICK_SLAB)));
        var stair=BLOCKS.register(name+"_stairs",()->new StairBlock(tile.get().defaultBlockState(),BlockBehaviour.Properties.ofFullCopy(Blocks.BRICK_STAIRS)));
        ITEMS.registerSimpleBlockItem(name,tile);
        ITEMS.registerSimpleBlockItem(name+"_slab",slab);
        ITEMS.registerSimpleBlockItem(name+"_stairs",stair);
        return new Tiles(tile,slab,stair);
    }
    static Block tile(boolean dusk) {return (dusk?DUSK:PETAL).full().get();}
    static Block slab(boolean dusk) {return (dusk?DUSK:PETAL).slab().get();}
    static Block stairs(boolean dusk) {return (dusk?DUSK:PETAL).stair().get();}
    static void register(IEventBus bus) {BLOCKS.register(bus);ITEMS.register(bus);}
}
