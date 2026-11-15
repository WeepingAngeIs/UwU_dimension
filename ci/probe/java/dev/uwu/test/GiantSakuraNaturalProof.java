package dev.uwu.test;

import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.GiantSakuraTree;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Locates and reads the real freshly generated settlement landmark, not a test-built tree. */
final class GiantSakuraNaturalProof {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError("Natural giant sakura: "+why);}
    static record Tree(BlossomVillage.Plan village,int x,int y,int z,int height){
        BlockPos pos(int dx,int dy,int dz){return village.site().pos(x+dx,y+1+dy,z+dz);}
    }
    static Tree find(ServerLevel level)throws Exception {
        var cls=Class.forName("dev.uwu.dimension.VillageSakura");
        var survey=cls.getDeclaredMethod("survey",ServerLevel.class,BlossomVillage.Plan.class);survey.setAccessible(true);
        for(boolean dusk:new boolean[]{false,true}){
            var village=BlossomVillage.first(level,dusk);
            Optional<?> result=(Optional<?>)survey.invoke(null,level,village);
            if(result.isEmpty())continue;
            var plot=result.get();var type=plot.getClass();int[] values=new int[4];int n=0;
            for(String key:new String[]{"x","y","z","trunk"}){var method=type.getDeclaredMethod(key);method.setAccessible(true);values[n++]=(int)method.invoke(plot);}
            var tree=new Tree(village,values[0],values[1],values[2],values[3]);
            var center=tree.pos(0,0,0);
            for(int cx=(center.getX()-19)>>4;cx<=(center.getX()+19)>>4;cx++)
                for(int cz=(center.getZ()-19)>>4;cz<=(center.getZ()+19)>>4;cz++)level.getChunk(cx,cz);
            if(level.getBlockState(tree.pos(0,0,0)).is(Blocks.CHERRY_LOG))return tree;
        }
        throw new AssertionError("No generated giant tree found at first two eligible settlements");
    }
    static void run(ServerLevel level)throws Exception {
        var tree=find(level);Set<BlockPos> actualLogs=new HashSet<>();int leaves=0;
        for(var entry:GiantSakuraTree.plan(tree.height(),level.getSeed()^tree.x()*31L^tree.z()).entrySet()){
            var delta=entry.getKey();var world=tree.pos(delta.getX(),delta.getY(),delta.getZ());
            var actual=level.getBlockState(world);
            if(entry.getValue().is(Blocks.CHERRY_LOG)){
                check(actual.is(Blocks.CHERRY_LOG),"missing actual trunk/branch at "+world);
                actualLogs.add(world);
            }else if(actual.is(Blocks.CHERRY_LEAVES))leaves++;
        }
        for(int y=0;y<17;y++)for(int x=0;x<2;x++)for(int z=0;z<2;z++)
            check(level.getBlockState(tree.pos(x,y,z)).is(Blocks.CHERRY_LOG),"missing 2x2 core at "+tree.pos(x,y,z));
        for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++){
            var base=tree.pos(x,0,z);
            check(level.getBlockState(base).is(Blocks.CHERRY_LOG),"missing flared base at "+base);
            check(level.getBlockState(base.below()).is(net.minecraft.tags.BlockTags.DIRT),"unsupported base at "+base);
        }
        Set<BlockPos> reached=new HashSet<>();ArrayDeque<BlockPos> q=new ArrayDeque<>();
        q.add(tree.pos(0,0,0));reached.add(tree.pos(0,0,0));
        while(!q.isEmpty()){
            var here=q.removeFirst();
            for(var dir:Direction.values()){var next=here.relative(dir);if(actualLogs.contains(next)&&reached.add(next))q.add(next);}
        }
        check(reached.size()==actualLogs.size()&&leaves>300,"disconnected trunk/branches or sparse canopy");
        System.out.println("GIANT SAKURA NATURAL PASS base="+tree.pos(0,0,0)+" height="+tree.height()+" logs="+actualLogs.size()+" leaves="+leaves);
    }
}
