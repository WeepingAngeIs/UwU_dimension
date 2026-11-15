package dev.uwu.test;

import dev.uwu.dimension.GroveSites;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Finds a real native patch, then looks across its flowers from ordinary standing eye height. */
final class UnderstoryCamera {
    private record Plant(BlockPos at,Block kind){}
    private record Shot(Vec3 eye,Vec3 aim,int count,int kinds,String species){}
    private record Cluster(Plant center,List<Plant> members,int different){}
    private static final Map<ServerLevel,Map<String,SeaWatch.View>> CACHE=new WeakHashMap<>();
    static SeaWatch.View frame(ServerPlayer player,String name){
        return CACHE.computeIfAbsent(player.serverLevel(),unused->new HashMap<>()).computeIfAbsent(name,unused->scout(player,name));
    }
    private static SeaWatch.View scout(ServerPlayer player,String name){
        var level=player.serverLevel();
        String side=name.startsWith("dusk")?"dusk":"petal";
        var tag=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("uwudimension",side+"_understory"));
        var land=TagKey.create(Registries.BIOME,ResourceLocation.fromNamespaceAndPath("uwudimension",side+"_understory_land"));
        var center=GroveSites.arrival(level);
        var source=level.getChunkSource();
        var generator=source.getGenerator();
        var random=source.randomState();
        int x0=center.getX()>>4,z0=center.getZ()>>4;
        Shot best=null;int surveyed=0;
        // Sample the native biome source on a 96-block grid and ask the generator for soil height
        // before loading a single candidate. A Petal or Dusk village can occupy either biome.
        outer:for(int ring=0;ring<=32;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
            int cx=x0+dx*6,cz=z0+dz*6,midX=(cx<<4)+8,midZ=(cz<<4)+8;
            int qx=QuartPos.fromBlock(midX),qz=QuartPos.fromBlock(midZ);
            if(!generator.getBiomeSource().getNoiseBiome(qx,QuartPos.fromBlock(78),qz,random.sampler()).is(land))continue;
            int height=generator.getBaseHeight(midX,midZ,Heightmap.Types.OCEAN_FLOOR_WG,level,random);
            if(height<=level.getSeaLevel()||!generator.getBiomeSource().getNoiseBiome(qx,QuartPos.fromBlock(height),qz,random.sampler()).is(land))continue;
            if(surveyed>=18)break outer;
            surveyed++;
            var chunk=level.getChunk(cx,cz);
            var plants=new ArrayList<Plant>();
            for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++){
                int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                for(int y=top-2;y<=top+1;y++){
                    var p=new BlockPos(x,y,z);var state=chunk.getBlockState(p);
                    if(!state.is(tag)||chunk.getBlockState(p.below()).is(tag)||!level.getBiome(p).is(land))continue;
                    plants.add(new Plant(p,state.getBlock()));break;
                }
            }
            var candidates=new ArrayList<Cluster>();
            for(var plant:plants){
                var members=plants.stream().filter(p->p.at().distSqr(plant.at())<=30).toList();
                if(members.size()<3)continue;
                int kinds=(int)members.stream().map(Plant::kind).distinct().count();
                candidates.add(new Cluster(plant,members,kinds));
            }
            candidates.sort(Comparator.comparingInt((Cluster c)->c.different()*4+c.members().size()).reversed());
            for(var candidate:candidates.stream().limit(5).toList()){
                var plant=candidate.center();
                for(int[] direction:new int[][]{{1,0},{0,-1},{-1,0},{0,1},{1,1},{1,-1},{-1,1},{-1,-1}}){
                    int x=plant.at().getX()+direction[0]*5,z=plant.at().getZ()+direction[1]*5;
                    if(!level.hasChunkAt(new BlockPos(x,height,z)))continue;
                    int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                    if(Math.abs(ground-plant.at().getY())>2)continue;
                    var eye=new Vec3(x+.5,ground+1.60,z+.5);
                    var aim=Vec3.atCenterOf(plant.at()).add(0,.26,0);
                    var at=BlockPos.containing(eye);
                    if(!level.getBlockState(at).getCollisionShape(level,at).isEmpty()||!level.getFluidState(at).isEmpty())continue;
                    var trace=level.clip(new ClipContext(eye,aim,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,player));
                    if(trace.getType()!=HitResult.Type.MISS&&trace.getLocation().distanceToSqr(aim)>1.7)continue;
                    int visible=0;
                    var species=new java.util.TreeSet<String>();
                    for(var member:candidate.members()){
                        var point=Vec3.atCenterOf(member.at());
                        var ray=level.clip(new ClipContext(eye,point,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,player));
                        if(ray.getType()!=HitResult.Type.MISS && ray.getLocation().distanceToSqr(point)>=1.1)continue;
                        visible++;species.add(BuiltInRegistries.BLOCK.getKey(member.kind()).getPath());
                    }
                    if(visible<3)continue;
                    var shot=new Shot(eye,aim,visible,species.size(),species.toString());
                    if(best==null||shot.kinds()*4+visible>best.kinds()*4+best.count())best=shot;
                    if(visible>=7&&species.size()>=3)break outer;
                }
            }
        }
        if(best==null)throw new AssertionError("No naturally generated "+name+" patch in "+surveyed+" eligible chunks around "+center);
        System.out.println("MOONVEIL UNDERSTORY PHOTO "+name+" real visible plants="+best.count()+" kinds="+best.species()+" native biome="
                +level.getBiome(BlockPos.containing(best.aim())).unwrapKey()+" candidate chunks="+surveyed+" eye="+best.eye()+" aim="+best.aim());
        return new SeaWatch.View(best.eye(),best.aim(),name,false);
    }
}
