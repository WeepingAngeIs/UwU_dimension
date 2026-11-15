package dev.uwu.test;

import dev.uwu.dimension.MoonveilTreehouse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
    static SeaWatch.View frame(ServerPlayer player,String name){
        var level=player.serverLevel();boolean dusk=name.startsWith("dusk");
        var tag=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("uwudimension",dusk?"dusk_understory":"petal_understory"));
        var house=MoonveilTreehouse.first(level,dusk);
        var center=house.at(0,0,0);
        Shot best=null;int surveyed=0;
        // Start on the outskirts of the generated housing district, beyond the building footprint.
        outer:for(int ring=1;ring<=7;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
            int cx=(center.getX()>>4)+dx*2,cz=(center.getZ()>>4)+dz*2;
            var chunk=level.getChunk(cx,cz);
            var plants=new ArrayList<Plant>();
            for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++){
                int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                for(int y=top-2;y<=top+1;y++){
                    var p=new BlockPos(x,y,z);var state=chunk.getBlockState(p);
                    if(!state.is(tag)||chunk.getBlockState(p.below()).is(tag))continue;
                    plants.add(new Plant(p,state.getBlock()));break;
                }
            }
            for(var plant:plants){
                var cluster=plants.stream().filter(p->p.at().distSqr(plant.at())<=30).toList();
                if(cluster.size()<3)continue;
                var species=new HashSet<String>();
                for(var member:cluster)species.add(BuiltInRegistries.BLOCK.getKey(member.kind()).getPath());
                for(int[] direction:new int[][]{{1,0},{0,-1},{-1,0},{0,1},{1,1},{1,-1},{-1,1},{-1,-1}}){
                    int x=plant.at().getX()+direction[0]*5,z=plant.at().getZ()+direction[1]*5;
                    int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                    if(Math.abs(ground-plant.at().getY())>2)continue;
                    var eye=new Vec3(x+.5,ground+1.60,z+.5);
                    var aim=Vec3.atCenterOf(plant.at()).add(0,.26,0);
                    var at=BlockPos.containing(eye);
                    if(!level.getBlockState(at).getCollisionShape(level,at).isEmpty()||!level.getFluidState(at).isEmpty())continue;
                    var trace=level.clip(new ClipContext(eye,aim,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,player));
                    if(trace.getType()!=HitResult.Type.MISS&&trace.getLocation().distanceToSqr(aim)>1.7)continue;
                    int visible=0;
                    for(var member:cluster){
                        var point=Vec3.atCenterOf(member.at());
                        var ray=level.clip(new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
                        if(ray.getType()==HitResult.Type.MISS||ray.getLocation().distanceToSqr(point)<1.1)visible++;
                    }
                    var shot=new Shot(eye,aim,visible,species.size(),species.toString());
                    if(best==null||shot.kinds()*4+visible>best.kinds()*4+best.count())best=shot;
                    if(visible>=7&&species.size()>=3)break outer;
                }
            }
            if(++surveyed>=64)break outer;
        }
        if(best==null||best.count()<3)throw new AssertionError("No naturally generated "+name+" patch after "+surveyed+" surveyed chunks at "+center);
        System.out.println("MOONVEIL UNDERSTORY PHOTO "+name+" real visible plants="+best.count()+" kinds="+best.species()+" eye="+best.eye()+" aim="+best.aim());
        return new SeaWatch.View(best.eye(),best.aim(),name,false);
    }
}
