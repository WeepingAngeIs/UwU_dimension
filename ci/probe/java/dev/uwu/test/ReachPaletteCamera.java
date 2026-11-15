package dev.uwu.test;

import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.SakuraMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Finds actual newly generated terrain and pebble beds. No blocks are placed or removed for either frame. */
final class ReachPaletteCamera {
    private static final int[][] ANGLES={{0,1},{1,1},{1,0},{1,-1},{0,-1},{-1,-1},{-1,0},{-1,1}};

    static SeaWatch.View view(ServerPlayer player,String name){
        boolean wet=name.equals("rainbow-riverbed");
        if(!wet&&!name.equals("mistgrass-shore"))throw new IllegalArgumentException(name);
        var level=player.serverLevel();
        var source=level.getChunkSource();var generator=source.getGenerator();var noise=source.randomState();
        var origin=GroveSites.arrival(level);int sea=generator.getSeaLevel(),tried=0;
        // Survey cheap terrain estimates first. This keeps us out of deep ocean, mountain peaks and large forests.
        for(int ring=0;ring<=40;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
            int x=origin.getX()+dx*48, z=origin.getZ()+dz*48;
            int estimate=generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,noise);
            if(wet ? estimate>sea-3||estimate<sea-20 : estimate>sea+8||estimate<sea-2)continue;
            var chunk=level.getChunk(x>>4,z>>4);
            for(int sx=0;sx<16;sx++)for(int sz=0;sz<16;sz++){
                int bx=chunk.getPos().getMinBlockX()+sx,bz=chunk.getPos().getMinBlockZ()+sz;
                int by=level.getHeight(Heightmap.Types.OCEAN_FLOOR,bx,bz)-1;
                if(by>sea+12||by<sea-23)continue;
                var floor=new BlockPos(bx,by,bz);
                if(!level.getBlockState(floor).is(wet?SakuraMaterials.GRAVEL.get():SakuraMaterials.GRASS.get()))continue;
                if(wet && !level.getFluidState(floor.above(2)).is(FluidTags.WATER))continue;
                for(int[] dir:ANGLES){
                    var eye=floor.offset(dir[0]*(wet?3:4),wet?3:4,dir[1]*(wet?3:4));
                    if(wet ? !level.getFluidState(eye).is(FluidTags.WATER) : !level.isEmptyBlock(eye)||!level.canSeeSky(eye))continue;
                    if(!wet){
                        // Frame grass in the foreground with visible natural water beyond it.
                        boolean waterfront=false;
                        for(int i=2;i<=18;i+=2){
                            var bank=floor.offset(-dir[0]*i,0,-dir[1]*i);
                            var surface=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,bank).below();
                            if(level.getFluidState(surface).is(FluidTags.WATER)&&level.canSeeSky(surface.above())){waterfront=true;break;}
                        }
                        if(!waterfront)continue;
                    }
                    var from=Vec3.atCenterOf(eye);var focus=Vec3.atCenterOf(floor);
                    var hit=level.clip(new ClipContext(from,focus,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
                    if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(floor))continue;
                    if(!wet)focus=focus.add(-dir[0]*1.5,1.3,-dir[1]*1.5);
                    System.out.println("SAKURA NATIVE PALETTE PHOTO "+name+" eye="+eye+" terrain="+floor+" biome="+level.getBiome(floor).unwrapKey());
                    return new SeaWatch.View(from,focus,name,false);
                }
            }
            if(++tried>=110)throw new AssertionError("Native "+name+" habitat missing in "+tried+" candidate shoreline chunks");
        }
        throw new AssertionError("Native "+name+" habitat missing near "+origin);
    }
}
