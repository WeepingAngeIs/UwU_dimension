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
            if(level.getBlockState(tree.pos(0,0,0)).is(Blocks.CHERRY_WOOD))return tree;
        }
        throw new AssertionError("No generated giant tree found at first two eligible settlements");
    }
    static void run(ServerLevel level)throws Exception {
        var tree=find(level);int wood=0,leaves=0;
        for(var entry:GiantSakuraTree.plan(0,0).entrySet()) {
            var delta=entry.getKey();var at=tree.pos(delta.getX(),delta.getY(),delta.getZ());
            var expected=entry.getValue();
            if(expected.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)) {
                var axis=expected.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS);
                if(axis!=Direction.Axis.Y) {
                    var origin=tree.pos(0,0,0);
                    var next=tree.pos(axis==Direction.Axis.X?1:0,0,axis==Direction.Axis.Z?1:0);
                    expected=expected.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                            next.getX()!=origin.getX()?Direction.Axis.X:Direction.Axis.Z);
                }
            }
            check(level.getBlockState(at).equals(expected),"authored blockstate mismatch at "+at);
            if(expected.is(Blocks.CHERRY_LEAVES))leaves++;else wood++;
        }
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)
            check(level.getBlockState(tree.pos(x,0,z).below()).is(net.minecraft.tags.BlockTags.DIRT),"unsupported authored trunk base");
        check(wood==345&&leaves==4681,"authored material count");
        System.out.println("GIANT SAKURA NATURAL PASS base="+tree.pos(0,0,0)+" height=38 wood="+wood+" leaves="+leaves);
    }
}
