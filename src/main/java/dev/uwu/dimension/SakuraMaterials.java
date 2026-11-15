package dev.uwu.dimension;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;

/** Sakura's native substrate and ore faces. Width, height, lighting, drops and tool tiers follow the source mineral. */
public final class SakuraMaterials {
    private static final String MOD="uwudimension";
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MOD);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MOD);
    private static final Map<Block,DeferredBlock<? extends Block>> PALETTE=new LinkedHashMap<>();
    private static final List<DeferredItem<Item>> MATERIALS=new java.util.ArrayList<>();
    private static final List<Supplier<? extends Item>> BUILDING=new java.util.ArrayList<>();
    private static volatile Map<BlockState,BlockState> states;

    public static final DeferredBlock<GrassBlock> GRASS=variant("sakura_grass_block",Blocks.GRASS_BLOCK,()->new GrassBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK).mapColor(MapColor.COLOR_LIGHT_BLUE)) {
        @Override public BlockState getToolModifiedState(BlockState state,UseOnContext context,ItemAbility action,boolean simulate) {
            if(action==ItemAbilities.HOE_TILL || action==ItemAbilities.SHOVEL_FLATTEN)
                return Blocks.GRASS_BLOCK.getToolModifiedState(Blocks.GRASS_BLOCK.defaultBlockState(),context,action,simulate);
            return super.getToolModifiedState(state,context,action,simulate);
        }
    });
    public static final DeferredBlock<ColoredFallingBlock> GRAVEL=variant("rainbow_gravel",Blocks.GRAVEL,()->
            new ColoredFallingBlock(new net.minecraft.util.ColorRGBA(0xFFC8B7CF), BlockBehaviour.Properties.ofFullCopy(Blocks.GRAVEL).mapColor(MapColor.COLOR_PINK)));
    public static final DeferredBlock<Block> STONE=stone("sakura_stone",Blocks.STONE);
    public static final DeferredBlock<RotatedPillarBlock> SLATE=variant("sakura_deepslate",Blocks.DEEPSLATE,()->
            new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE)) {});
    public static final DeferredBlock<Block> SILVER_ORE=building("silver_ore",()->new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    public static final DeferredBlock<Block> DEEP_SILVER_ORE=building("deepslate_silver_ore",()->new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE)));
    public static final DeferredBlock<Block> SILVER_BLOCK=building("silver_block",()->new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredBlock<Block> RAW_BLOCK=building("raw_silver_block",()->new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.RAW_IRON_BLOCK)));
    public static final DeferredBlock<ChainBlock> CHAIN=building("silver_chain",()->new ChainBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHAIN)) {});
    public static final DeferredItem<Item> RAW=ingredient("raw_silver"), INGOT=ingredient("silver_ingot"), NUGGET=ingredient("silver_nugget");
    static {
        for(String rock:List.of("andesite","diorite","granite","tuff"))stone("sakura_"+rock,vanilla(rock));
        for(String mineral:List.of("coal","copper","iron","gold","redstone","lapis","diamond","emerald")){
            for(String depth:List.of("","deepslate_")){
                var source=vanilla(depth+mineral+"_ore");
                String name=depth+"sakura_"+mineral+"_ore";
                var props=BlockBehaviour.Properties.ofFullCopy(source);
                if(mineral.equals("redstone"))variant(name,source,()->new RedStoneOreBlock(props) {});
                else {
                    var xp=switch(mineral){
                        case "coal"->UniformInt.of(0,2); case "lapis"->UniformInt.of(2,5);
                        case "diamond","emerald"->UniformInt.of(3,7); default->null;
                    };
                    if(xp==null)variant(name,source,()->new Block(props));
                    else variant(name,source,()->new DropExperienceBlock(xp,props) {});
                }
            }
        }
    }
    private static Block vanilla(String path){return BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(path));}
    private static DeferredBlock<Block> stone(String name,Block source){return variant(name,source,()->new Block(BlockBehaviour.Properties.ofFullCopy(source)));}
    private static <T extends Block> DeferredBlock<T> building(String name,Supplier<T> factory) {
        var block=BLOCKS.register(name,factory);
        BUILDING.add(ITEMS.registerSimpleBlockItem(name,block));
        return block;
    }
    private static <T extends Block> DeferredBlock<T> variant(String name,Block vanilla,Supplier<T> factory){
        var block=building(name,factory);PALETTE.put(vanilla,block);return block;
    }
    private static DeferredItem<Item> ingredient(String name){
        var item=ITEMS.registerSimpleItem(name,new Item.Properties());MATERIALS.add(item);return item;
    }
    private SakuraMaterials(){}
    public static void register(IEventBus bus){
        BLOCKS.register(bus);ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent event)->{
            if(event.getTabKey()==CreativeModeTabs.BUILDING_BLOCKS)BUILDING.forEach(item->event.accept(item.get()));
            if(event.getTabKey()==CreativeModeTabs.INGREDIENTS)MATERIALS.forEach(item->event.accept(item.get()));
        });
    }
    private static synchronized Map<BlockState,BlockState> prepare(){
        if(states==null){
            Map<BlockState,BlockState> result=new IdentityHashMap<>();
            PALETTE.forEach((vanilla,custom)->vanilla.getStateDefinition().getPossibleStates().forEach(
                    state->result.put(state,custom.get().withPropertiesOf(state))));
            states=result;
        }
        return states;
    }

    /** Called only by the new-generation SPAWN task after neighboring block-writing features and before entities spawn.
     * Swap equivalent states in section storage so heightmaps, fluids, lights and adjacent chunks need no recalculation. */
    public static void finish(ChunkAccess chunk){
        var swaps=states;
        if(swaps==null)swaps=prepare();
        for(var section:chunk.getSections()){
            if(section.hasOnlyAir() || !section.maybeHas(swaps::containsKey))continue;
            section.acquire();
            try {
                for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                    var next=swaps.get(section.getBlockState(x,y,z));
                    if(next!=null)section.setBlockState(x,y,z,next,false);
                }
            }finally{section.release();}
        }
    }
}
