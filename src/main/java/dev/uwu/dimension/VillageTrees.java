package dev.uwu.dimension;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;


/** Neighbor features can grow a cherry tree across a freshly graded village lot.
 * Only a disconnected natural crown with a clear line to real soil needs a new trunk. */
final class VillageTrees {
    static void root(ServerLevel level,ChunkAccess chunk){
        var seen=new HashSet<BlockPos>();
        var pos=chunk.getPos();
        // Section palettes reflect actual blocks, including logs below another crown
        // or a farmhouse roof. A surface heightmap is insufficient at a chunk frontier.
        var sections=chunk.getSections();
        for(int i=0;i<sections.length;i++){
            var section=sections[i];
            if(!section.maybeHas(state->state.is(Blocks.CHERRY_LOG)))continue;
            int baseY=chunk.getSectionYFromSectionIndex(i)<<4;
            for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)for(int dy=0;dy<16;dy++){
                if(!section.getBlockState(dx,dy,dz).is(Blocks.CHERRY_LOG))continue;
                var at=new BlockPos(pos.getMinBlockX()+dx,baseY+dy,pos.getMinBlockZ()+dz);
                if(!seen.contains(at))rootComponent(level,chunk,at,seen);
            }
        }
    }
    private static void rootComponent(ServerLevel level,ChunkAccess chunk,BlockPos start,Set<BlockPos> seen){
        var queue=new ArrayDeque<BlockPos>();queue.add(start);seen.add(start);
        var component=new ArrayList<BlockPos>();
        BlockPos lowest=start;boolean supported=false,complete=true;
        while(!queue.isEmpty()){
            var log=queue.removeFirst();component.add(log);
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
        if(!complete||supported)return;
        // A one- or two-log fragment has no viable crown. Do not extend a detached
        // roof-height twig all the way through a house or turn it into a lone pole.
        if(component.size()<=2){removeFragment(level,chunk,component);return;}
        if(!chunk.getPos().equals(new ChunkPos(lowest)))return;
        if(!growTrunk(chunk,lowest))removeFragment(level,chunk,component);
    }
    private static boolean growTrunk(ChunkAccess chunk,BlockPos lowest){
        BlockPos base=lowest.below();
        while(base.getY()>=lowest.getY()-24 && base.getY()>chunk.getMinBuildHeight()){
            var state=chunk.getBlockState(base);
            if(state.is(BlockTags.DIRT))break;
            if(!state.isAir()&&!state.is(BlockTags.LEAVES)&&!state.canBeReplaced())return false;
            if(!state.getFluidState().isEmpty())return false;
            base=base.below();
        }
        if(!chunk.getBlockState(base).is(BlockTags.DIRT))return false;
        var trunk=Blocks.CHERRY_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.Y);
        for(int y=base.getY()+1;y<lowest.getY();y++)chunk.setBlockState(new BlockPos(base.getX(),y,base.getZ()),trunk,false);
        return true;
    }
    private static void removeFragment(ServerLevel level,ChunkAccess chunk,List<BlockPos> logs){
        Set<BlockPos> leaves=new HashSet<>();
        for(var log:logs){
            if(!chunk.getPos().equals(new ChunkPos(log)))continue;
            chunk.setBlockState(log,Blocks.AIR.defaultBlockState(),false);
            for(var leaf:BlockPos.betweenClosed(log.offset(-6,-6,-6),log.offset(6,6,6))){
                if(log.distManhattan(leaf)>6)continue;
                var state=read(level,chunk,leaf);
                if(state!=null&&state.is(Blocks.CHERRY_LEAVES)&&!state.getValue(LeavesBlock.PERSISTENT))
                    leaves.add(leaf.immutable());
            }
        }
        // Propagate vanilla leaf-distance updates through the crown and over chunk
        // edges. Persistent ornamental leaves and adjacent trees keep their canopies.
        for(var leaf:leaves){
            var target=chunk.getPos().equals(new ChunkPos(leaf))?chunk:
                    level.getChunkSource().getChunkForLighting(leaf.getX()>>4,leaf.getZ()>>4);
            if(target instanceof ChunkAccess access)access.getBlockTicks().schedule(
                    new net.minecraft.world.ticks.ScheduledTick<>(Blocks.CHERRY_LEAVES,leaf,level.getGameTime()+1,level.nextSubTickCount()));
        }
    }
    private static BlockState read(ServerLevel level,ChunkAccess own,BlockPos p){
        if((p.getX()>>4)==own.getPos().x&&(p.getZ()>>4)==own.getPos().z)return own.getBlockState(p);
        // This is a non-generating lookup. Full chunks already have decorated neighbors.
        var other=level.getChunkSource().getChunkForLighting(p.getX()>>4,p.getZ()>>4);
        return other==null?null:other.getBlockState(p);
    }
}
