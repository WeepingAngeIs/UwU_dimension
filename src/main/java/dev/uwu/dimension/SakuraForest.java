package dev.uwu.dimension;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Seed-only forest planning. Writes are clipped to the chunk already generating, before lighting. */
public final class SakuraForest {
    private SakuraForest(){}
    public static final int STEP_X=21,STEP_Z=21,RADIUS=17;
    private static final Map<ServerLevel,Map<Long,Optional<Tree>>> CACHE=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<Long,Boolean>> BIOMES=new WeakHashMap<>();
    private static synchronized Map<Long,Boolean> biomes(ServerLevel level){return BIOMES.computeIfAbsent(level,k->new ConcurrentHashMap<>());}
    private static final ThreadLocal<Boolean> FOREIGN=ThreadLocal.withInitial(()->false);
    public record Tree(int x,int y,int z,int turns){}
    private static final class Shapes {
        static final List<Map<BlockPos,BlockState>> ALL=make();
        static List<Map<BlockPos,BlockState>> make(){
            var result=new ArrayList<Map<BlockPos,BlockState>>();
            for(int turns=0;turns<4;turns++){
                var shape=new LinkedHashMap<BlockPos,BlockState>();
                for(var entry:GiantSakuraTree.plan(0,0).entrySet()){
                    var at=entry.getKey();int x=at.getX(),z=at.getZ();var state=entry.getValue();
                    for(int i=0;i<turns;i++){int old=x;x=-z;z=old;}
                    if((turns&1)==1&&state.hasProperty(RotatedPillarBlock.AXIS)){
                        var axis=state.getValue(RotatedPillarBlock.AXIS);
                        if(axis!=Direction.Axis.Y)state=state.setValue(RotatedPillarBlock.AXIS,axis==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X);
                    }
                    shape.put(new BlockPos(x,at.getY(),z),state);
                }
                result.add(Collections.unmodifiableMap(shape));
            }
            return List.copyOf(result);
        }
    }
    public static Map<BlockPos,BlockState> shape(Tree tree){return Shapes.ALL.get(tree.turns());}
    private static synchronized Map<Long,Optional<Tree>> cache(ServerLevel level){return CACHE.computeIfAbsent(level,k->new ConcurrentHashMap<>());}
    public static boolean target(ServerLevel level,int x,int z){
        if(!level.dimension().equals(ReefLife.REACH))return false;
        int y=GroveSites.height(level,x,z)+1;
        int qx=QuartPos.fromBlock(x),qy=QuartPos.fromBlock(y),qz=QuartPos.fromBlock(z);
        var map=biomes(level);if(map.size()>32768)map.clear();
        return map.computeIfAbsent(BlockPos.asLong(qx,qy,qz),key->{
            var source=level.getChunkSource();
            return source.getGenerator().getBiomeSource().getNoiseBiome(qx,qy,qz,source.randomState().sampler()).is(Biomes.CHERRY_GROVE);
        });
    }
    public static boolean target(ServerLevel level,BlockPos p){return target(level,p.getX(),p.getZ());}
    public static boolean target(WorldGenLevel level,BlockPos p){return target(level.getLevel(),p.getX(),p.getZ());}
    public static boolean foreignFeature(net.minecraft.world.level.levelgen.feature.Feature<?> feature){
        if(feature instanceof GiantSakuraSapling)return false;
        var id=net.minecraft.core.registries.BuiltInRegistries.FEATURE.getKey(feature);
        return feature instanceof net.minecraft.world.level.levelgen.feature.TreeFeature
                ||feature.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("tree")
                ||(id!=null&&id.getPath().contains("tree"));
    }
    public static boolean foreign(){return FOREIGN.get();}
    public static boolean enterForeign(){boolean prior=FOREIGN.get();FOREIGN.set(true);return prior;}
    public static void leaveForeign(boolean prior){if(prior)FOREIGN.set(true);else FOREIGN.remove();}
    /** True only where a non-authored tree may not write, including border spill from another biome. */
    public static boolean denyForeign(WorldGenLevel level,BlockPos p){return foreign()&&target(level,p);}
    static void bonemeal(net.neoforged.neoforge.event.entity.player.BonemealEvent event){
        if(event.getLevel() instanceof ServerLevel level && event.getState().getBlock() instanceof SaplingBlock
                && !event.getState().is(GiantSakuraSapling.SAPLING.get())&&target(level,event.getPos())){
            event.setSuccessful(false);
            if(event.getPlayer()!=null)event.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.literal("Only giant sakura saplings grow in this forest."),true);
        }
    }
    static void growth(net.neoforged.neoforge.event.level.BlockGrowFeatureEvent event){
        if(event.getLevel() instanceof WorldGenLevel level&&target(level,event.getPos())
                &&event.getFeature()!=null&&!(event.getFeature().value().feature() instanceof GiantSakuraSapling))event.setCanceled(true);
    }
    public static Optional<Tree> tree(ServerLevel level,int cellX,int cellZ){
        var map=cache(level);if(map.size()>4096)map.clear();
        return map.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(cellX,cellZ),key->survey(level,cellX,cellZ))
                .filter(tree->!SettlementLayout.rejectedForest(level,tree.x(),tree.z()));
    }
    private static Optional<Tree> survey(ServerLevel level,int cx,int cz){
        int hash=HeartwaterFeature.Valley.mix(cx,cz,level.getSeed());
        int x=cx*STEP_X+Math.floorMod(HeartwaterFeature.Valley.mix(0,cz,level.getSeed()),5)-2;
        int z=cz*STEP_Z+Math.floorMod(HeartwaterFeature.Valley.mix(cx,0,level.getSeed()),5)-2;
        if(!target(level,x,z)||!SettlementLayout.untouchedForForest(level,x-17,z-17,x+17,z+17))return Optional.empty();
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            int h=GroveSites.height(level,x+dx,z+dz);low=Math.min(low,h);high=Math.max(high,h);
        }
        if(low<64||high-low>3||high+39>=level.getMaxBuildHeight())return Optional.empty();
        if(!level.getWorldBorder().isWithinBounds(new BlockPos(x-17,high,z-17))||!level.getWorldBorder().isWithinBounds(new BlockPos(x+17,high,z+17)))return Optional.empty();
        // A full rectangular biome buffer keeps every authored block inside this biome.
        // Noise/base-height sampling never requests any live or generating neighbor chunk.
        for(int dx=-17;dx<=17;dx++)for(int dz=-17;dz<=17;dz++){
            int h=GroveSites.height(level,x+dx,z+dz);
            if(h<64||h>high+8||!target(level,x+dx,z+dz))return Optional.empty();
        }
        if(reserved(level,x,z))return Optional.empty();
        var tree=new Tree(x,high+1,z,(hash>>>8)&3);
        for(var at:shape(tree).keySet())if(GroveSites.height(level,x+at.getX(),z+at.getZ())>=tree.y()+at.getY())return Optional.empty();
        return Optional.of(tree);
    }
    private static boolean reserved(ServerLevel level,int x,int z){
        var site=GroveSites.site(level,Math.floorDiv(x+GroveSites.SPACING/2,GroveSites.SPACING),Math.floorDiv(z+GroveSites.SPACING/2,GroveSites.SPACING));
        if(!site.usable())return false;
        int dx=x-site.x(),dz=z-site.z();
        int lx=switch(site.turns()){case 1->dz;case 2->-dx;case 3->-dz;default->dx;};
        int lz=switch(site.turns()){case 1->-dx;case 2->-dz;case 3->dx;default->dz;};
        if(GroveSites.adventure(level,site)&&Math.abs(lx)<=33&&lz>=site.lip()-32&&lz<=site.lip()+27)return true;
        for(boolean dusk:new boolean[]{false,true}){
            var village=BlossomVillage.plan(level,site,dusk);
            if(village.isEmpty())continue;
            var p=village.get();
            // Reserve the complete settlement envelope and its approach, not individual trunk cells.
            if(Math.abs(lx-p.x())<=72&&Math.abs(lz-p.z())<=65)return true;
            if(lx>=Math.min(0,p.x())-22&&lx<=Math.max(0,p.x())+22&&lz>=Math.min(site.lip()-9,p.z())-22&&lz<=Math.max(site.lip()-9,p.z())+22)return true;
            var house=MoonveilTreehouse.plan(level,p);
            if(house.isPresent()&&Math.abs(lx-house.get().x())<=42&&Math.abs(lz-house.get().z())<=42)return true;
        }
        return false;
    }
    public static List<Tree> trees(ServerLevel level,net.minecraft.world.level.ChunkPos chunk){
        if(!level.dimension().equals(ReefLife.REACH))return List.of();
        var list=new ArrayList<Tree>();
        for(int x=Math.floorDiv(chunk.getMinBlockX()-20,STEP_X);x<=Math.floorDiv(chunk.getMaxBlockX()+20,STEP_X);x++)
            for(int z=Math.floorDiv(chunk.getMinBlockZ()-20,STEP_Z);z<=Math.floorDiv(chunk.getMaxBlockZ()+20,STEP_Z);z++)tree(level,x,z).ifPresent(list::add);
        return list;
    }
    private static boolean open(ServerLevel level,Tree t){
        for(int x=(t.x-17)>>4;x<=(t.x+17)>>4;x++)for(int z=(t.z-17)>>4;z<=(t.z+17)>>4;z++)
            if(!SakuraAccess.mayGrow(level,null,new BlockPos(x<<4,t.y,z<<4)))return false;
        return true;
    }
    private static boolean replaceable(BlockState state){
        return state.isAir()||state.is(net.minecraft.tags.BlockTags.LEAVES)||state.is(Blocks.CHERRY_LOG)||state.is(Blocks.CHERRY_WOOD)
                ||state.is(Blocks.PINK_PETALS)||state.is(Blocks.SHORT_GRASS)||state.is(Blocks.TALL_GRASS)||state.is(Blocks.FERN)||state.is(Blocks.LARGE_FERN)
                ||state.getBlock() instanceof FlowerBlock||state.getBlock() instanceof DimensionCropBlock;
    }
    public static void decorate(ServerLevel level,ChunkAccess chunk){
        if(!level.dimension().equals(ReefLife.REACH))return;
        var own=chunk.getPos();
        for(var tree:trees(level,own)){
            var shape=shape(tree);
            if(!open(level,tree)){SettlementLayout.rejectForest(level,tree.x(),tree.z());continue;}
            boolean safe=true;
            for(var entry:shape.entrySet()){
                var p=entry.getKey().offset(tree.x,tree.y,tree.z);if(!own.equals(new net.minecraft.world.level.ChunkPos(p)))continue;
                var old=chunk.getBlockState(p);
                if(old.hasBlockEntity()||!old.getFluidState().isEmpty()||!replaceable(old)){safe=false;break;}
            }
            if(!safe){SettlementLayout.rejectForest(level,tree.x(),tree.z());continue;}
            for(var entry:shape.entrySet()){
                var p=entry.getKey().offset(tree.x,tree.y,tree.z);if(!own.equals(new net.minecraft.world.level.ChunkPos(p)))continue;
                var old=chunk.getBlockState(p);var state=entry.getValue();
                // Crowns can merge; a neighboring authored branch always keeps priority over a leaf.
                if(state.is(Blocks.CHERRY_LEAVES)&&(old.is(Blocks.CHERRY_LOG)||old.is(Blocks.CHERRY_WOOD)))continue;
                chunk.setBlockState(p,state,false);
            }
            // Only the authorized short roots vary: bridge at most three blocks to real soil.
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                int x=tree.x+dx,z=tree.z+dz;if((x>>4)!=own.x||(z>>4)!=own.z)continue;
                int ground=GroveSites.height(level,x,z);
                for(int y=ground+1;y<tree.y;y++){
                    var p=new BlockPos(x,y,z);var old=chunk.getBlockState(p);
                    if(replaceable(old)&&old.getFluidState().isEmpty())chunk.setBlockState(p,Blocks.CHERRY_WOOD.defaultBlockState(),false);
                }
            }
        }
    }
}
