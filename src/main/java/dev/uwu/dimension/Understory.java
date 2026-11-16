package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;

/** Dry, rooted flora of Sakura Reach. Vegetation is decorated once, while its chunk is new. */
public final class Understory {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("uwudimension");
    private static final List<DeferredBlock<? extends Block>> PLANTS=new ArrayList<>();
    private static final TagKey<Block> SOIL=TagKey.create(Registries.BLOCK,UwUDimension.id("understory_soil"));
    private static final TagKey<Biome> PETAL_LAND=land("petal"), DUSK_LAND=land("dusk");
    private static TagKey<Biome> land(String district){
        return TagKey.create(Registries.BIOME,UwUDimension.id(district+"_understory_land"));
    }
    private static final class Flora {
        final DeferredBlock<LowPlant> grass, fern, clover, bell, aster, sorrel;
        final DeferredBlock<HighGrass> tall;
        final List<TagKey<Block>> blooms;
        Flora(String prefix){
            blooms=List.of("spring","summer","frost").stream().map(s->
                    TagKey.create(Registries.BLOCK,UwUDimension.id(prefix+s+"_blooms"))).toList();
            grass=low(prefix+"short_grass",6);
            fern=low(prefix+"lace_fern",10);
            clover=low(prefix+"star_clover",4);
            bell=low(prefix+"rain_bell",12);
            aster=low(prefix+"wood_aster",10);
            sorrel=low(prefix+"frost_sorrel",7);
            tall=BLOCKS.register(prefix+"tall_grass",HighGrass::new);
            item(prefix+"tall_grass",tall);
        }
        // The spring, summer and first-frost accents are stable regional mixes, editable with block tags.
        Block flower(int season,RandomSource random){
            if(random.nextInt(5)==0)return clover.get();
            return BuiltInRegistries.BLOCK.getTag(blooms.get(season)).flatMap(pool->pool.getRandomElement(random))
                    .map(holder->holder.value()).filter(block->block instanceof LowPlant).orElse(clover.get());
        }
    }
    private static final Flora PETAL=new Flora("petal_"), DUSK=new Flora("dusk_");
    private static <T extends Block> void item(String name,DeferredBlock<T> block){
        ITEMS.registerSimpleBlockItem(name,block);
        PLANTS.add(block);
    }
    private static DeferredBlock<LowPlant> low(String name,int height){
        var block=BLOCKS.register(name,()->new LowPlant(height));
        item(name,block);
        return block;
    }
    private static boolean rooted(BlockState soil,BlockGetter level,BlockPos pos){
        return soil.is(SOIL) && soil.getFluidState().isEmpty() && soil.isFaceSturdy(level,pos,Direction.UP);
    }
    /** These rooted decorations yield to trees just like vanilla grass and flowers. */
    static boolean isVegetation(BlockState state){
        return state.getBlock() instanceof LowPlant || state.getBlock() instanceof HighGrass;
    }
    private static final class LowPlant extends BushBlock {
        private static final com.mojang.serialization.MapCodec<LowPlant> CODEC=com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(
                fields->fields.group(com.mojang.serialization.Codec.intRange(1,16).fieldOf("height").forGetter((LowPlant plant)->plant.height),
                        propertiesCodec()).apply(fields,LowPlant::new));
        private final VoxelShape shape;
        private final int height;
        LowPlant(int height){
            this(height,BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS).noOcclusion());
        }
        LowPlant(int height,BlockBehaviour.Properties properties){
            super(properties);
            this.height=height;
            shape=Block.box(2,0,2,14,height,14);
        }
        @Override protected com.mojang.serialization.MapCodec<LowPlant> codec(){return CODEC;}
        @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
            var offset=state.getOffset(level,pos);
            return shape.move(offset.x,offset.y,offset.z);
        }
        @Override protected boolean mayPlaceOn(BlockState soil,BlockGetter level,BlockPos pos){return rooted(soil,level,pos);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){
            return context.getLevel().getFluidState(context.getClickedPos()).isEmpty()?super.getStateForPlacement(context):null;
        }
    }
    private static final class HighGrass extends DoublePlantBlock {
        HighGrass(){super(BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS).noOcclusion());}
        @Override protected boolean mayPlaceOn(BlockState soil,BlockGetter level,BlockPos pos){return rooted(soil,level,pos);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){
            var level=context.getLevel();var pos=context.getClickedPos();
            return level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty()?super.getStateForPlacement(context):null;
        }
    }
    public static void register(IEventBus bus){
        BLOCKS.register(bus);ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent event)->{
            if(event.getTabKey()==CreativeModeTabs.NATURAL_BLOCKS)PLANTS.forEach(b->event.accept(b.get()));
        });
    }
    private static boolean validSpace(WorldGenLevel level,BlockPos pos){
        var state=level.getBlockState(pos);
        return state.getFluidState().isEmpty() && (state.isAir() || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.SNOW)
                && state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)==1);
    }
    private static boolean place(WorldGenLevel level,BlockPos pos,Block block){
        if(!validSpace(level,pos) || !rooted(level.getBlockState(pos.below()),level,pos.below()))return false;
        if(block instanceof HighGrass){
            if(pos.getY()+1>=level.getMaxBuildHeight() || !validSpace(level,pos.above()))return false;
            DoublePlantBlock.placeAt(level,block.defaultBlockState(),pos,Block.UPDATE_CLIENTS);
            return true;
        }
        return level.setBlock(pos,block.defaultBlockState(),Block.UPDATE_CLIENTS);
    }
    static boolean decorate(WorldGenLevel level,BlockPos origin){
        int x0=origin.getX() & ~15, z0=origin.getZ() & ~15;
        // Independent RNG keeps ores, gardens and existing landmarks on their original random stream.
        var random=RandomSource.create(HeartwaterFeature.Valley.mix(x0,z0,level.getSeed()^0x461ae967L));
        int season=Math.floorMod(HeartwaterFeature.Valley.mix(Math.floorDiv(x0,96),Math.floorDiv(z0,96),level.getSeed()),3);
        boolean any=false;
        // Six small clumps per chunk. Only sample the owning chunk; never scan a column or load neighbours.
        for(int clump=0;clump<6;clump++){
            int centerX=x0+3+random.nextInt(10),centerZ=z0+3+random.nextInt(10);
            int style=random.nextInt(10);
            for(int attempt=0;attempt<7;attempt++){
                int x=centerX+random.nextInt(4)-random.nextInt(4);
                int z=centerZ+random.nextInt(4)-random.nextInt(4);
                var pos=new BlockPos(x,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);
                // Neighboring chunks can still decorate shallow banks after this chunk.
                // Keep land flora above the waterline where gravel disks can replace the soil.
                if(pos.getY()<=level.getSeaLevel()+1)continue;
                var biome=level.getBiome(pos);
                Flora flora=biome.is(DUSK_LAND)?DUSK:biome.is(PETAL_LAND)?PETAL:null;
                if(flora==null)continue;
                Block plant;
                if(attempt==0 || style<4)plant=flora.grass.get();
                else if(style<6)plant=flora.fern.get();
                else if(style==6)plant=random.nextBoolean()?flora.tall.get():flora.grass.get();
                else if(style==7)plant=flora.clover.get();
                else plant=flora.flower(season,random);
                any|=place(level,pos,plant);
            }
        }
        return any;
    }
    private Understory(){}
}
