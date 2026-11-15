package dev.uwu.test;

import dev.uwu.dimension.MoonveilTreehouse;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Photographs real generated Petal and Dusk treehouses from an open entrance-side approach and inside. */
final class TreehouseCamera {
    private static Vec3 point(BlockPos p){return Vec3.atCenterOf(p);}
    private static boolean clear(ServerPlayer player,Vec3 spot){
        var level=player.serverLevel();
        var p=BlockPos.containing(spot);
        level.getChunkAt(p);
        return level.getBlockState(p).getCollisionShape(level,p).isEmpty()&&level.getFluidState(p).isEmpty();
    }
    private static double obstruction(ServerPlayer player,Vec3 start,Vec3 end){
        var hit=player.serverLevel().clip(new ClipContext(start,end,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.MISS?0:hit.getLocation().distanceTo(end);
    }
    static SeaWatch.View frame(ServerPlayer player,String name){
        if(name.endsWith("-understory"))return UnderstoryCamera.frame(player,name);
        var level=player.serverLevel();
        var plot=MoonveilTreehouse.first(level,name.contains("dusk"));
        var center=plot.at(0,0,0);
        level.getChunkAt(center);
        boolean inside=name.endsWith("interior");
        Vec3 best=null,aim=null;
        double score=Double.POSITIVE_INFINITY;
        if(inside){
            // Show the downstairs joinery/workshop in Petal and the upstairs bedrooms in Dusk.
            int floor=plot.dusk()?7:0;
            var table=point(plot.at(3,floor+1,0));
            var detail=plot.dusk()?point(plot.bedHead(0)):point(plot.job(0));
            var target=table.add(detail).scale(.5).add(0,.55,0);
            for(int x:new int[]{-2,0,-1,-3,1})for(int z:new int[]{-5,4,-3,3,1}){
                var eye=point(plot.at(x,floor+2,z)).add(0,.12,0);
                if(eye.distanceTo(target)<3.5||!clear(player,eye))continue;
                double blocked=obstruction(player,eye,target);
                double rank=blocked*8+Math.abs(eye.distanceTo(target)-6.8);
                if(rank<score){score=rank;best=eye;aim=target;}
            }
        }else{
            var target=point(plot.at(0,18,0));
            var crown=point(plot.at(0,30,0));
            var entry=point(plot.door().above(2));
            for(int x:new int[]{-25,25,-15,15,0,-35,35})for(int y:new int[]{14,20,26})for(int z:new int[]{-41,-50,-58}){
                var eye=point(plot.at(x,y,z));
                if(!clear(player,eye))continue;
                double obscuresTrunk=obstruction(player,eye,target),obscuresDoor=obstruction(player,eye,entry);
                // The rays can meet the house itself. Penalize trees in the foreground much more heavily.
                double rank=Math.max(0,obscuresTrunk-10)*3+Math.max(0,obscuresDoor-3)*2+
                        Math.max(0,obstruction(player,eye,crown)-15)+Math.abs(eye.distanceTo(target)-50)*.25;
                if(rank<score){score=rank;best=eye;aim=target;}
            }
        }
        if(best==null||score>(inside?22:55))throw new AssertionError("No clear native "+name+" at "+center+" score="+score);
        System.out.println("MOONVEIL PHOTO "+name+" origin="+center+" eye="+best+" target="+aim+" viewScore="+score);
        return new SeaWatch.View(best,aim,name,false);
    }
}
