package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Joinery, paving, and carved stone used by the two settlements and their underground chambers. */
final class ReachArchitecture {
    private static final String MOD = "uwudimension";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD);
    private static final List<Supplier<? extends Block>> DISPLAY = new ArrayList<>();

    record Wood(DeferredBlock<Block> boards, DeferredBlock<RotatedPillarBlock> beam, DeferredBlock<StairBlock> stairs,
                DeferredBlock<SlabBlock> slab, DeferredBlock<FenceBlock> fence, DeferredBlock<TrapDoorBlock> shutter,
                DeferredBlock<DoorBlock> door) {}
    static final Wood PETAL = wood("silkwood");
    static final Wood DUSK = wood("velvetwood");
    static final DeferredBlock<Block> PLASTER = stone("petalsilk_plaster", 0);
    static final DeferredBlock<Block> GLAZE = stone("camellia_glaze", 0);
    static final DeferredBlock<Block> PEARL = stone("shellstone_ashlar", 0);
    static final DeferredBlock<Block> SLATE = stone("vesper_ashlar", 0);
    static final DeferredBlock<Block> SCALE = stone("lotus_scale_tile", 0);
    static final DeferredBlock<Block> RIB = stone("fluted_nacre", 0);
    static final DeferredBlock<Block> TRACERY = stone("starvein_frieze", 9);
    static final DeferredBlock<Block> PILOT = stone("moonpearl_lamp", 13);
    static final DeferredBlock<StairBlock> PEARL_STEP = add("shellstone_steps", () -> new StairBlock(PEARL.get().defaultBlockState(), like(Blocks.BRICK_STAIRS)));
    static final DeferredBlock<StairBlock> SLATE_STEP = add("vesper_steps", () -> new StairBlock(SLATE.get().defaultBlockState(), like(Blocks.BRICK_STAIRS)));

    private static final DeferredBlock<StainedGlassBlock> BLUSH = window("blush",DyeColor.PINK);
    private static final DeferredBlock<StainedGlassBlock> OPAL = window("opal",DyeColor.WHITE);
    private static final DeferredBlock<StainedGlassBlock> DEW = window("dew",DyeColor.LIGHT_BLUE);
    private static final DeferredBlock<StainedGlassBlock> IRIS = window("iris",DyeColor.PURPLE);
    private static final DeferredBlock<StainedGlassBlock> ORCHID = window("orchid",DyeColor.MAGENTA);
    static Block window(boolean dusk,int lane) {
        return (lane==0 ? OPAL : lane<0 ? (dusk?ORCHID:BLUSH) : (dusk?IRIS:DEW)).get();
    }
    private static DeferredBlock<StainedGlassBlock> window(String name,DyeColor dye) {
        return add(name+"_leaded_glass", () -> new StainedGlassBlock(dye,like(Blocks.PINK_STAINED_GLASS)) {});
    }
    static Wood wood(boolean dusk) { return dusk ? DUSK : PETAL; }
    static Block paving(boolean dusk) { return (dusk ? SLATE : PEARL).get(); }
    static Block steps(boolean dusk) { return (dusk ? SLATE_STEP : PEARL_STEP).get(); }
    private static Wood wood(String id) {
        var boards = add(id+"_planks", () -> new Block(like(Blocks.CHERRY_PLANKS)));
        var beam = add(id+"_beam", () -> new RotatedPillarBlock(like(Blocks.STRIPPED_CHERRY_LOG)));
        var stair = add(id+"_stairs", () -> new StairBlock(boards.get().defaultBlockState(), like(Blocks.CHERRY_STAIRS)));
        var slab = add(id+"_slab", () -> new SlabBlock(like(Blocks.CHERRY_SLAB)));
        var fence = add(id+"_fence", () -> new FenceBlock(like(Blocks.CHERRY_FENCE)));
        DeferredBlock<TrapDoorBlock> shutter = add(id+"_shutter", () -> new TrapDoorBlock(BlockSetType.CHERRY,like(Blocks.CHERRY_TRAPDOOR)) {});
        DeferredBlock<DoorBlock> door = add(id+"_door", () -> new DoorBlock(BlockSetType.CHERRY,like(Blocks.CHERRY_DOOR)) {});
        return new Wood(boards,beam,stair,slab,fence,shutter,door);
    }
    private static BlockBehaviour.Properties like(Block model) {return BlockBehaviour.Properties.ofFullCopy(model);}
    private static DeferredBlock<Block> stone(String name, int light) {
        return add(name, () -> new Block(like(Blocks.CALCITE).lightLevel(state -> light)));
    }
    private static <B extends Block> DeferredBlock<B> add(String name, Supplier<B> make) {
        var holder=BLOCKS.register(name,make);
        ITEMS.registerSimpleBlockItem(name,holder);
        DISPLAY.add(holder);
        return holder;
    }
    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(ReachArchitecture::display);
    }
    private static void display(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey()==CreativeModeTabs.BUILDING_BLOCKS) DISPLAY.forEach(block -> event.accept(block.get()));
    }
}
