package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;

/** Uses biome noise before requesting terrain. Checks real water depth at the selected column. */
public final class ReefCharts {
    private ReefCharts() {}
    public static BlockPos nearest(ServerLevel level, BlockPos origin, int skipRadius) {
        var gen=level.getChunkSource().getGenerator();
        var noise=level.getChunkSource().randomState();
        int grid=64, sea=gen.getSeaLevel();
        for(int ring=0;ring<=64;ring++) {
            for(int dx=-ring;dx<=ring;dx++) for(int dz=-ring;dz<=ring;dz++) {
                if(Math.max(Math.abs(dx),Math.abs(dz))!=ring || ((double)dx*dx+dz*dz)*grid*grid<(double)skipRadius*skipRadius)continue;
                int x=origin.getX()+dx*grid,z=origin.getZ()+dz*grid;
                if(!gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(sea-5),QuartPos.fromBlock(z),noise.sampler()).is(ReefLife.REEF))continue;
                int estimate=gen.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,noise);
                if(estimate>sea-8)continue;
                level.getChunkAt(new BlockPos(x,sea,z));
                int bed=level.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z);
                BlockPos swim=new BlockPos(x,Math.min(sea-4,bed+4),z);
                if(sea-bed>=7 && level.getBiome(swim).is(ReefLife.REEF)
                        && level.getFluidState(swim).is(FluidTags.WATER) && level.getFluidState(swim.above()).is(FluidTags.WATER))
                    return swim;
            }
        }
        return null;
    }
}
