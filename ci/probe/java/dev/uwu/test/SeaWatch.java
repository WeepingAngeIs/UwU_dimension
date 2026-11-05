package dev.uwu.test;

import java.util.*;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.uwu.dimension.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;

/** Every aquatic subject is surveyed in generated terrain. Animals are observed as the world ticks. */
final class SeaWatch {
    private static final Set<String> AQUATIC=Set.of("wooper","barboach","chinchou","luvdisc","finneon","horsea",
            "remoraid","shellder","wishiwashi","corsola","mantyke","alomomola","magikarp","tentacool","goldeen",
            "seaking","wailmer","arrokuda","bruxish","krabby","qwilfish","basculin","seadra","staryu","clawitzer","carvanha");
    record View(Vec3 eye,Vec3 subject,String file,boolean sea) {}
    private static final Set<String> seen=new TreeSet<>();
    private static final Set<UUID> fish=new HashSet<>(), dolphins=new HashSet<>();
    static void reset(){seen.clear();fish.clear();dolphins.clear();}
    static void observe(ServerPlayer p) {
        ServerLevel l=p.serverLevel();AABB around=p.getBoundingBox().inflate(72,26,72);
        for(var mon:l.getEntitiesOfClass(PokemonEntity.class,around)) {
            String kind=mon.getPokemon().getSpecies().getName().toLowerCase(Locale.ROOT);
            if(AQUATIC.contains(kind)&&mon.isInWater()&&l.getFluidState(mon.blockPosition()).is(FluidTags.WATER)
                    &&seen.add(kind))System.out.println("NATIVE AQUATIC "+kind+" in real water at "+mon.blockPosition());
        }
        for(var mob:l.getEntitiesOfClass(AbstractFish.class,around))if(mob.isInWater())fish.add(mob.getUUID());
        for(var mob:l.getEntitiesOfClass(Dolphin.class,around))if(mob.getType()==ReefLife.DOLPHIN.get()&&mob.isInWater())dolphins.add(mob.getUUID());
    }
    static boolean stocked(){return !seen.isEmpty()&&fish.size()>=3&&!dolphins.isEmpty();}
    static String counts(){return "aquatic Pokemon="+seen+", fish="+fish.size()+", Ribbon Dolphins="+dolphins.size();}
    static View reef(ServerPlayer p,BlockPos origin,String name) {
        ServerLevel l=p.serverLevel();
        BlockPos near=ReefCharts.nearest(l,origin,256);
        if(near==null)throw new AssertionError("No natural reef within chart range of "+origin);
        // Inspect actual generated coral heads, then choose a clear stretch of water to photograph them.
        int best=-1;BlockPos cam=null,subject=null;
        for(int x=near.getX()-24;x<=near.getX()+24;x+=3)for(int z=near.getZ()-24;z<=near.getZ()+24;z+=3) {
            int bed=l.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z);
            BlockPos center=new BlockPos(x,bed-1,z);
            if(!coral(l,center) || !l.getBiome(center.above()).is(ReefLife.REEF))continue;
            int diversity=0;
            for(int dx=-4;dx<=4;dx+=2)for(int dz=-4;dz<=4;dz+=2) {
                int y=l.getHeight(Heightmap.Types.OCEAN_FLOOR,x+dx,z+dz);
                if(coral(l,new BlockPos(x+dx,y-1,z+dz)))diversity++;
            }
            for(int[] d:new int[][]{{-5,-7},{7,-5},{-7,5},{5,7}}) {
                var eye=center.offset(d[0],4,d[1]);
                if(!l.getBlockState(eye).is(Blocks.WATER)||!l.getBlockState(eye.above()).is(Blocks.WATER)
                    ||!l.getBlockState(eye.below()).is(Blocks.WATER))continue;
                if(l.clip(new ClipContext(Vec3.atCenterOf(eye),Vec3.atCenterOf(center.above()),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS)continue;
                int score=diversity*4+Math.min(0,bed-54);
                if(score>best){best=score;cam=eye;subject=center;}
            }
        }
        if(cam==null)throw new AssertionError("No living coral vista near "+near);
        int samples=0,wet=0,deep=0;
        // Every sample represents a 4x4 column within Cobblemon's actual 16–64 block player band.
        for(int dx=-64;dx<=64;dx+=4)for(int dz=-64;dz<=64;dz+=4){
            int dist=dx*dx+dz*dz;if(dist<256||dist>4096)continue;
            int x=cam.getX()+dx,z=cam.getZ()+dz;
            int bottom=l.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z);
            int top=l.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
            samples++;
            if(top-bottom>=5 && l.getBlockState(new BlockPos(x,top-2,z)).is(Blocks.WATER)){wet+=top-bottom;deep++;}
        }
        if(deep<samples*.15 || wet*16<12000)throw new AssertionError("Inadequate aquatic spawn band "+wet*16+" blocks");
        System.out.println("NATURAL REEF seed="+l.getSeed()+" "+name+" camera="+cam+" wetVolumeEstimate="+wet*16+" deepColumns="+deep+"/"+samples+" coralScore="+best);
        return new View(Vec3.atCenterOf(cam),Vec3.atCenterOf(subject.above()),name,true);
    }
    private static boolean coral(ServerLevel l,BlockPos p) {
        var b=l.getBlockState(p);
        return b.is(BlockTags.CORAL_BLOCKS)||b.is(BlockTags.CORALS)||BuiltInRegistries.BLOCK.getKey(b.getBlock()).getPath().equals("nacre_rose");
    }
}
