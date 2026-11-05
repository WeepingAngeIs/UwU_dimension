package dev.uwu.dimension;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/** Neighbor features can grow a cherry tree across a freshly graded village lot.
 * Only a disconnected natural crown with a clear line to real soil needs a new trunk. */
final class VillageTrees {
    static void root(ServerLevel level,ChunkAccess chunk){
        var seen=new HashSet<BlockPos>();
        var pos=chunk.getPos();
        for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){
            int x=pos.getMinBlockX()+dx,z=pos.getMinBlockZ()+dz;
            int top=chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,dx,dz);
            for(int y=top;y>=Math.max(level.getMinBuildHeight(),top-20);y--){
                var at=new BlockPos(x,y,z);
                if(!seen.contains(at)&&chunk.getBlockState(at).is(Blocks.CHERRY_LOG))
                    rootComponent(level,chunk,at,seen);
            }
        }
    }
    private static void rootComponent(ServerLevel level,ChunkAccess chunk,BlockPos start,Set<BlockPos> seen){
        var queue=new ArrayDeque<BlockPos>();queue.add(start);seen.add(start);
        BlockPos lowest=start;boolean supported=false,complete=true;
        while(!queue.isEmpty()){
            var log=queue.removeFirst();
            var below=log.below();
            var support=read(level,chunk,below);
            if(support==null){complete=false;continue;}
            if(support.is(BlockTags.DIRT)||(!support.is(BlockTags.LOGS)&&!support.is(BlockTags.LEAVES)
                    &&support.isFaceSturdy(chunk,below,Direction.UP)))supported=true;
            // Consistent selection across chunk boundaries, even for a sideways branch.
            if(log.compareTo(lowest)<0)lowest=log;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=-1;dy<=1;dy++){
                var next=log.offset(dx,dy,dz);
                if(seen.contains(next))continue;
                var state=read(level,chunk,next);
                if(state==null){complete=false;continue;} // unreadable terrain may carry the real trunk
                if(state.is(Blocks.CHERRY_LOG)){seen.add(next);queue.add(next);}
            }
        }
        if(!complete||supported||!chunk.getPos().equals(new ChunkPos(lowest)))return;
        BlockPos base=lowest.below();
        while(base.getY()>=lowest.getY()-24 && base.getY()>level.getMinBuildHeight()){
            var state=chunk.getBlockState(base);
            if(state.is(BlockTags.DIRT))break;
            if(!state.isAir()&&!state.is(BlockTags.LEAVES)&&!state.canBeReplaced())return;
            if(!state.getFluidState().isEmpty())return;
            base=base.below();
        }
        if(!chunk.getBlockState(base).is(BlockTags.DIRT))return;
        var trunk=Blocks.CHERRY_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.Y);
        for(int y=base.getY()+1;y<lowest.getY();y++)chunk.setBlockState(new BlockPos(base.getX(),y,base.getZ()),trunk,false);
    }
    private static BlockState read(ServerLevel level,ChunkAccess own,BlockPos p){
        if((p.getX()>>4)==own.getPos().x&&(p.getZ()>>4)==own.getPos().z)return own.getBlockState(p);
        // This is a non-generating lookup. Full chunks already have decorated neighbors.
        var other=level.getChunkSource().getChunkForLighting(p.getX()>>4,p.getZ()>>4);
        return other==null?null:other.getBlockState(p);
    }
}
