package dev.uwu.dimension;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** One shared, deterministic tree plan, used by both new landmarks and the sapling. */
public final class GiantSakuraTree {
    private GiantSakuraTree() {}

    /** Coordinates are relative to the north-west trunk block at soil level + 1. */
    public static Map<BlockPos,BlockState> plan(int height,long seed) {
        Map<BlockPos,BlockState> wood=new LinkedHashMap<>(), leaves=new LinkedHashMap<>();
        BlockState log=Blocks.CHERRY_LOG.defaultBlockState();
        BlockState leaf=Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);
        for(int y=0;y<height;y++)for(int x=0;x<2;x++)for(int z=0;z<2;z++)
            wood.put(new BlockPos(x,y,z),log);
        // A grounded 4x4 root flare tapers asymmetrically into the 2x2 core.
        // Every base column is supported; the smaller side buttresses are
        // face-connected to the core at every height.
        for(int y=0;y<3;y++)for(int x:new int[]{-1,2})for(int z:new int[]{-1,2})
            wood.put(new BlockPos(x,y,z),log);
        for(int y=0;y<12;y++)for(int d=0;d<2;d++){
            if(y<12)wood.put(new BlockPos(d,y,-1),log);
            if(y<10)wood.put(new BlockPos(2,y,d),log);
            if(y<9)wood.put(new BlockPos(d,y,2),log);
            if(y<11)wood.put(new BlockPos(-1,y,d),log);
        }

        int[][] boughs={{-9,4,-2,8,4,7},{6,5,-7,8,5,7},{9,3,5,7,4,8},
                {-5,6,8,8,5,7},{0,7,0,7,5,7},{-4,8,-6,6,4,6}};
        for(int[] b:boughs){
            int start=height/2;
            int steps=Math.max(1,Math.max(Math.abs(b[0]),Math.max(Math.abs(b[2]),height+b[1]-start)));
            int x=0,y=start,z=0;
            for(int i=1;i<=steps;i++){
                int tx=Math.round(b[0]*i/(float)steps), tz=Math.round(b[2]*i/(float)steps);
                int ty=start+Math.round((height+b[1]-start)*i/(float)steps);
                while(x!=tx){x+=Integer.signum(tx-x);wood.putIfAbsent(new BlockPos(x,y,z),log.setValue(RotatedPillarBlock.AXIS,Direction.Axis.X));}
                while(z!=tz){z+=Integer.signum(tz-z);wood.putIfAbsent(new BlockPos(x,y,z),log.setValue(RotatedPillarBlock.AXIS,Direction.Axis.Z));}
                while(y!=ty){y+=Integer.signum(ty-y);wood.putIfAbsent(new BlockPos(x,y,z),log);}
                if(i*3<steps){
                    wood.putIfAbsent(new BlockPos(x+1,y,z),log);
                    wood.putIfAbsent(new BlockPos(x,y,z+1),log);
                }
            }
        }
        // Generate all wood first: no canopy lobe can overwrite any trunk or branch.
        for(int[] b:boughs){
            int bx=b[0], by=height+b[1]+2, bz=b[2], rx=b[3], ry=b[4], rz=b[5];
            for(int x=-rx;x<=rx;x++)for(int z=-rz;z<=rz;z++)for(int y=-ry;y<=ry;y++){
                double edge=(double)(x*x)/(rx*rx)+(double)(y*y)/(ry*ry)+(double)(z*z)/(rz*rz);
                if(edge>1 || by+y<height)continue;
                int noise=HeartwaterFeature.Valley.mix(bx+x,bz+z,seed+y);
                if((edge>.72 && (noise&7)==0) || (edge<=.72 && (noise&127)==0))continue;
                var at=new BlockPos(bx+x,by+y,bz+z);
                if(!wood.containsKey(at))leaves.putIfAbsent(at,leaf);
            }
        }
        wood.putAll(leaves);
        return wood;
    }
}
