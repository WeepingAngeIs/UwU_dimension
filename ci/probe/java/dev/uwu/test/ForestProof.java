package dev.uwu.test;
import dev.uwu.dimension.*;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/** Actual newly generated chunks, with independent scans of every natural log/leaf. */
public final class ForestProof {
    private static void check(boolean value,String why){if(!value)throw new AssertionError("Authored forest: "+why);}
    private static int height(ServerLevel level,int x,int z){return level.getChunkSource().getGenerator().getBaseHeight(x,z,net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,level,level.getChunkSource().randomState())-1;}
    public record Cluster(int x,int z,List<SakuraForest.Tree> trees){}
    public static Cluster find(ServerLevel level){
        var site=GroveSites.first(level);int ox=Math.floorDiv(site.x(),SakuraForest.STEP_X),oz=Math.floorDiv(site.z(),SakuraForest.STEP_Z);
        int loaded=level.getChunkSource().getLoadedChunksCount();long start=System.nanoTime();
        for(int ring=0;ring<=24;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
            var center=SakuraForest.tree(level,ox+dx,oz+dz);if(center.isEmpty())continue;
            var trees=new ArrayList<SakuraForest.Tree>();
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)SakuraForest.tree(level,ox+dx+x,oz+dz+z).ifPresent(trees::add);
            if(trees.size()<16)continue;
            check(loaded==level.getChunkSource().getLoadedChunksCount(),"pure survey loaded chunks");
            System.out.println("FOREST PURE SURVEY PASS trees="+trees.size()+" ms="+(System.nanoTime()-start)/1_000_000);
            return new Cluster(center.get().x(),center.get().z(),List.copyOf(trees));
        }
        throw new AssertionError("No sixteen-tree forest cluster in bounded survey");
    }
    public static Cluster run(ServerLevel level)throws Exception {
        var cluster=find(level);long start=System.nanoTime();
        int minX=cluster.x()-36,maxX=cluster.x()+36,minZ=cluster.z()-36,maxZ=cluster.z()+36;
        for(int x=(minX-18)>>4;x<=(maxX+18)>>4;x++)for(int z=(minZ-18)>>4;z<=(maxZ+18)>>4;z++)level.getChunk(x,z);
        long elapsed=(System.nanoTime()-start)/1_000_000;
        check(elapsed<180_000,"bounded forest generation exceeded 180 seconds: "+elapsed);
        for(var a:cluster.trees())for(var b:cluster.trees())if(a!=b)check((long)(a.x()-b.x())*(a.x()-b.x())+(long)(a.z()-b.z())*(a.z()-b.z())>=441,"unsafe trunk spacing");
        Map<BlockPos,net.minecraft.world.level.block.state.BlockState> expected=new HashMap<>();
        var seen=new HashSet<SakuraForest.Tree>();
        for(int x=minX>>4;x<=maxX>>4;x++)for(int z=minZ>>4;z<=maxZ>>4;z++)seen.addAll(SakuraForest.trees(level,new ChunkPos(x,z)));
        for(var t:seen)for(var entry:SakuraForest.shape(t).entrySet()){
            var p=entry.getKey().offset(t.x(),t.y(),t.z());var old=expected.get(p);
            if(old==null||!entry.getValue().is(Blocks.CHERRY_LEAVES))expected.put(p,entry.getValue());
        }
        int logs=0,leaves=0;Set<Long> crown=new HashSet<>();Set<BlockPos> leafBlocks=new HashSet<>();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            if(!SakuraForest.target(level,x,z))continue;
            int ground=height(level,x,z);
            for(int y=ground+5;y<=ground+46;y++){
                var p=new BlockPos(x,y,z);var actual=level.getBlockState(p);
                if(actual.is(net.minecraft.tags.BlockTags.LOGS)||actual.is(net.minecraft.tags.BlockTags.LEAVES)){
                    var wanted=expected.get(p);check(wanted!=null,"unplanned tree block "+p+" "+actual);
                    check(actual.getBlock()==wanted.getBlock(),"wrong authored material at "+p+" actual="+actual+" wanted="+wanted);
                    if(actual.is(Blocks.CHERRY_LEAVES)){leaves++;crown.add(ChunkPos.asLong(x,z));leafBlocks.add(p);}else logs++;
                }
            }
        }
        for(var entry:expected.entrySet()){
            var p=entry.getKey();if(p.getX()<minX||p.getX()>maxX||p.getZ()<minZ||p.getZ()>maxZ)continue;
            check(level.getBlockState(p).getBlock()==entry.getValue().getBlock(),"incomplete authored tree union at "+p);
        }
        check(logs>500&&leaves>10_000,"sparse forest "+logs+"/"+leaves);
        // Canopies must connect horizontally in the real world, not only overlap in a plan.
        Set<Long> todo=new HashSet<>(crown);int largest=0;
        while(!todo.isEmpty()){
            var queue=new ArrayDeque<Long>();long first=todo.iterator().next();todo.remove(first);queue.add(first);int size=0;
            while(!queue.isEmpty()){
                var p=new ChunkPos(queue.removeFirst());size++;
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){long q=ChunkPos.asLong(p.x+d[0],p.z+d[1]);if(todo.remove(q))queue.add(q);}
            }largest=Math.max(largest,size);
        }
        check(largest>=crown.size()*.70,"canopy is not contiguous: "+largest+"/"+crown.size());
        Set<BlockPos> unvisited=new HashSet<>(leafBlocks);int connected=0;
        while(!unvisited.isEmpty()){
            var queue=new ArrayDeque<BlockPos>();var first=unvisited.iterator().next();unvisited.remove(first);queue.add(first);int size=0;
            while(!queue.isEmpty()){
                var p=queue.removeFirst();size++;
                for(var direction:Direction.values()){var q=p.relative(direction);if(unvisited.remove(q))queue.add(q);}
            }connected=Math.max(connected,size);
        }
        check(connected>=leaves*.70,"3D leaf canopy disconnected: "+connected+"/"+leaves);
        System.out.println("FOREST 3D CANOPY PASS connected="+connected+" leaves="+leaves);
        System.out.println("FOREST NATURAL PASS seed="+level.getSeed()+" center="+cluster.x()+","+cluster.z()+" trees="+seen.size()+" logs="+logs+" leaves="+leaves+" canopy="+largest+"/"+crown.size()+" generationMs="+elapsed);
        saplings(level,cluster);
        if(net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims"))claims(level,cluster);
        return cluster;
    }
    private static void claims(ServerLevel level,Cluster cluster)throws Exception {
        SakuraForest.Tree tree=null;
        for(int x=Math.floorDiv(cluster.x(),SakuraForest.STEP_X)+12;x<Math.floorDiv(cluster.x(),SakuraForest.STEP_X)+32&&tree==null;x++)
            for(int z=Math.floorDiv(cluster.z(),SakuraForest.STEP_Z)-10;z<=Math.floorDiv(cluster.z(),SakuraForest.STEP_Z)+10;z++){
                var candidate=SakuraForest.tree(level,x,z);
                if(candidate.isPresent()&&!level.hasChunkAt(new BlockPos(candidate.get().x(),candidate.get().y(),candidate.get().z()))){tree=candidate.get();break;}
            }
        check(tree!=null,"unexplored claim fixture absent");
        var apiType=Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
        var api=apiType.getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,level.getServer());
        var manager=apiType.getMethod("getServerClaimsManager").invoke(api);
        var type=Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
        int cx=tree.x()>>4,cz=tree.z()>>4;var pos=new BlockPos(tree.x(),tree.y(),tree.z());
        check(type.getMethod("get",net.minecraft.resources.ResourceLocation.class,int.class,int.class).invoke(manager,level.dimension().location(),cx,cz)==null,"claim fixture already claimed");
        var claim=type.getMethod("claim",net.minecraft.resources.ResourceLocation.class,UUID.class,int.class,int.class,int.class,boolean.class);
        try {
            check(claim.invoke(manager,level.dimension().location(),UUID.randomUUID(),0,cx,cz,false)!=null,"claim creation failed");
            level.getChunk(cx,cz);
            check(!level.getBlockState(pos).is(Blocks.CHERRY_WOOD)&&!level.getBlockState(pos).is(Blocks.CHERRY_LOG),"worldgen tree entered a protected claim");
        } finally {type.getMethod("unclaim",net.minecraft.resources.ResourceLocation.class,int.class,int.class).invoke(manager,level.dimension().location(),cx,cz);}
        check(SakuraForest.tree(level,Math.floorDiv(tree.x()+2,SakuraForest.STEP_X),Math.floorDiv(tree.z()+2,SakuraForest.STEP_Z)).isEmpty(),"rejected tree was offered again after unclaim");
        for(int x=(tree.x()-17)>>4;x<=(tree.x()+17)>>4;x++)for(int z=(tree.z()-17)>>4;z<=(tree.z()+17)>>4;z++)level.getChunk(x,z);
        java.nio.file.Files.writeString(java.nio.file.Path.of("forest-rejected-root.txt"),tree.x()+","+tree.y()+","+tree.z());
        check(!level.getBlockState(pos).is(Blocks.CHERRY_WOOD)&&!level.getBlockState(pos).is(Blocks.CHERRY_LOG),"unclaim caused retrogen");
        System.out.println("FOREST CLAIMGEN PASS claimed new terrain refuses tree, unclaim does not rewrite it");
    }
    private static void saplings(ServerLevel level,Cluster cluster){
        var at=new BlockPos(cluster.x(),level.getMaxBuildHeight()-15,cluster.z());
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)level.setBlockAndUpdate(at.offset(dx,-1,dz),Blocks.DIRT.defaultBlockState());
        for(var block:List.of(Blocks.CHERRY_SAPLING,Blocks.OAK_SAPLING,Blocks.BIRCH_SAPLING,Blocks.SPRUCE_SAPLING)){
            var state=block.defaultBlockState().setValue(SaplingBlock.STAGE,1);level.setBlockAndUpdate(at,state);
            ((SaplingBlock)block).advanceTree(level,at,state,RandomSource.create(4));
            check(level.getBlockState(at).is(block),"ordinary sapling grew: "+block);
        }
        for(String id:List.of("oak","birch","cherry","uwudimension:heartbloom_tree","uwudimension:starpetal_tree","uwudimension:moonberry_tree")){
            var key=net.minecraft.resources.ResourceLocation.parse(id.contains(":")?id:"minecraft:"+id);
            var feature=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(key);
            check(feature!=null,"missing configured tree "+key);
            check(!feature.place(level,level.getChunkSource().getGenerator(),RandomSource.create(5),at),"configured tree bypassed forest policy "+key);
        }
        int modTrees=0;
        for(var entry:level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).entrySet()){
            if(!entry.getKey().location().getNamespace().equals("cobblemon")||!SakuraForest.foreignFeature(entry.getValue().feature()))continue;
            check(!entry.getValue().place(level,level.getChunkSource().getGenerator(),RandomSource.create(6),at),"Cobblemon tree bypassed theme "+entry.getKey());modTrees++;
        }
        check(modTrees>0,"Cobblemon tree generators were not tested");
        System.out.println("FOREST COBBLEMON GENERATORS PASS "+modTrees);
        // An ordinary tree still grows through the same production path in another biome.
        BlockPos outside=null;
        for(int r=64;r<=2048&&outside==null;r+=32)for(int sign:new int[]{-1,1})if(!SakuraForest.target(level,cluster.x()+r*sign,cluster.z())){outside=at.offset(r*sign,0,0);break;}
        check(outside!=null,"outside-biome fixture absent");
        for(int x=(outside.getX()-16)>>4;x<=(outside.getX()+16)>>4;x++)for(int z=(outside.getZ()-16)>>4;z<=(outside.getZ()+16)>>4;z++)level.getChunk(x,z);
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)level.setBlockAndUpdate(outside.offset(dx,-1,dz),Blocks.DIRT.defaultBlockState());
        var oak=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(net.minecraft.resources.ResourceLocation.parse("minecraft:oak"));
        check(oak.place(level,level.getChunkSource().getGenerator(),RandomSource.create(10),outside),"outside-biome oak path changed");
        for(var p:BlockPos.betweenClosed(outside.offset(-8,0,-8),outside.offset(8,12,8))){
            var state=level.getBlockState(p);
            if(state.is(net.minecraft.tags.BlockTags.LOGS)||state.is(net.minecraft.tags.BlockTags.LEAVES))check(!SakuraForest.target(level,p),"foreign tree spilled across biome border");
        }
        check(!SakuraForest.target(level.getServer().overworld(),cluster.x(),cluster.z()),"Overworld biome affected");
        System.out.println("FOREST SAPLING PASS ordinary/fruit configured trees blocked, other biome oak and Overworld unchanged");
    }
}
