package dev.uwu.test;

import dev.uwu.dimension.GroveSites;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Photographs generated terrain and the real bath on the ordinary graphical client. */
final class EnvironmentCamera {
    private EnvironmentCamera() {}
    static SeaWatch.View frame(ServerPlayer player,String name) {
        var level=player.serverLevel();
        if(name.startsWith("spring-")) {
            var site=GroveSites.first(level);
            BlockPos bed=site.pos(0,51,93);
            level.getChunkAt(bed);
            var expected=ResourceLocation.parse("uwudimension:sacred_spring_bed");
            if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(bed).getBlock()).equals(expected))
                throw new AssertionError("Natural sacred bath bed missing at "+bed);
            BlockPos eye=name.equals("spring-overlay")?site.pos(0,54,93):site.pos(-6,55,87);
            BlockPos aim=site.pos(3,52,96);
            level.getChunkAt(eye); level.getChunkAt(aim);
            System.out.println("ENVIRONMENT SPRING BED PASS "+bed+" eye="+eye);
            Vec3 camera=Vec3.atCenterOf(eye);
            if(name.equals("spring-overlay")) camera=camera.add(0,-.3,0);
            return new SeaWatch.View(camera,Vec3.atCenterOf(aim),name,false);
        }
        if(name.equals("giant-sakura")) return giant(player);
        return snow(player,name);
    }
    private static SeaWatch.View snow(ServerPlayer player,String name) {
        var level=player.serverLevel();
        var origin=GroveSites.arrival(level);
        var source=level.getChunkSource();
        var generator=source.getGenerator();
        var noise=source.randomState();
        var target=ResourceLocation.parse("uwudimension:"+(name.contains("dusk")?"dusk_frostlands":"starlace_frostlands"));
        for(int ring=0;ring<=50;ring++) for(int dx=-ring;dx<=ring;dx++) for(int dz=-ring;dz<=ring;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring) continue;
            int x=origin.getX()+dx*96,z=origin.getZ()+dz*96;
            if(!generator.getBiomeSource().getNoiseBiome(x>>2,78>>2,z>>2,noise.sampler()).is(target)) continue;
            int y=generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,noise);
            if(y<=level.getSeaLevel()+3 || !generator.getBiomeSource().getNoiseBiome(x>>2,y>>2,z>>2,noise.sampler()).is(target)) continue;
            BlockPos pos=new BlockPos(x,y,z);
            level.getChunkAt(pos);
            y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            BlockPos eye=new BlockPos(x,y+3,z), aim=new BlockPos(x+9,y+1,z+5);
            level.getChunkAt(aim);
            if(!level.isEmptyBlock(eye)) continue;
            System.out.println("ENVIRONMENT BIOME PASS "+name+" "+target+" at "+eye);
            return new SeaWatch.View(Vec3.atCenterOf(eye),Vec3.atCenterOf(aim),name,false);
        }
        throw new AssertionError("No generated snowy biome sample within 4800 blocks: "+target);
    }
    private static SeaWatch.View giant(ServerPlayer player) {
        var level=player.serverLevel();
        try {
            var cls=Class.forName("dev.uwu.dimension.VillageSakura");
            var survey=cls.getDeclaredMethod("survey",net.minecraft.server.level.ServerLevel.class,dev.uwu.dimension.BlossomVillage.Plan.class);
            survey.setAccessible(true);
            for(boolean dusk:new boolean[]{false,true}) {
                var village=dev.uwu.dimension.BlossomVillage.first(level,dusk);
                Optional<?> result=(Optional<?>)survey.invoke(null,level,village);
                if(result.isEmpty()) continue;
                Object plot=result.get();
                var plotClass=plot.getClass();
                var mx=plotClass.getDeclaredMethod("x");mx.setAccessible(true);
                var my=plotClass.getDeclaredMethod("y");my.setAccessible(true);
                var mz=plotClass.getDeclaredMethod("z");mz.setAccessible(true);
                int x=(int)mx.invoke(plot),y=(int)my.invoke(plot),z=(int)mz.invoke(plot);
                BlockPos base=village.site().pos(x,y,z);
                for(int cx=(base.getX()-17)>>4;cx<=(base.getX()+17)>>4;cx++)
                    for(int cz=(base.getZ()-17)>>4;cz<=(base.getZ()+17)>>4;cz++)level.getChunk(cx,cz);
                if(!level.getBlockState(village.site().pos(x,y+10,z)).is(net.minecraft.world.level.block.Blocks.CHERRY_LOG)) continue;
                var eye=village.site().pos(x+28,y+17,z-35);
                var target=village.site().pos(x,y+23,z);
                level.getChunkAt(eye);
                System.out.println("ENVIRONMENT GIANT TREE PASS base="+base);
                return new SeaWatch.View(Vec3.atCenterOf(eye),Vec3.atCenterOf(target),"giant-sakura",false);
            }
            System.out.println("ENVIRONMENT GIANT TREE UNAVAILABLE no surveyed tree at the first two villages");
            return null;
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("Giant sakura survey unavailable",error);}
    }
}

