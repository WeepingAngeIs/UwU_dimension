package dev.uwu.test;

import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.GroveSites;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Frame the actual furnishings in the world. Reject camera locations behind walls or inside furniture. */
final class FurnishingCamera {
    static SeaWatch.View view(ServerPlayer player, String name) {
        var level=player.serverLevel();
        if (name.equals("bathhouse-bench") || name.equals("archive-study")) {
            var valley=GroveSites.first(level);
            boolean bath=name.equals("bathhouse-bench");
            BlockPos feature=bath?valley.pos(-9,53,91):valley.pos(23,-18,143);
            FurnishingProof.furniture(level,feature,bath?"cherry_bench":"dark_oak_table");
            List<Vec3> eyes=new ArrayList<>();
            for (int i : new int[]{5,-5,3,-3})
                for (int j : new int[]{4,-4,2,-2})
                    eyes.add(Vec3.atCenterOf(bath?valley.pos(-9+i,55,91+j):valley.pos(23+i,-16,143+j)));
            var scene = bath ? List.of(feature,valley.pos(-9,54,94)) : List.of(feature,valley.pos(23,-17,143),valley.pos(24,-18,143));
            return frame(player,name,scene,eyes);
        }
        boolean dusk=name.equals("dusk-reading-room");
        var village=BlossomVillage.first(level,dusk);
        var house=village.houses().stream().filter(h->h.style()==0).findFirst().orElseThrow();
        List<BlockPos> subjects=new ArrayList<>();
        List<Vec3> eyes=new ArrayList<>();
        if (name.equals("pink-glass-skylight")) {
            for (int lane=-1;lane<=1;lane++) subjects.add(FurnishingProof.skylight(level,village,house,lane));
            int roof=subjects.stream().mapToInt(BlockPos::getY).max().orElseThrow()-house.floor();
            for (int dx : new int[]{-7,-6,4}) for (int dz : new int[]{-4,4,0})
                eyes.add(Vec3.atCenterOf(FurnishingProof.inside(village,house,dx,roof+5,dz)));
        } else if (name.equals("pastel-glass-windows")) {
            for (int lane=-1;lane<=1;lane++) subjects.add(FurnishingProof.inside(village,house,lane,3,house.depth()));
            for (int dx : new int[]{-2,2,0,-3,3}) for (int dz : new int[]{5,4,6})
                eyes.add(Vec3.atCenterOf(FurnishingProof.inside(village,house,dx,3,house.depth()+dz)));
        } else {
            String wood=dusk?"dark_oak":"cherry";
            var table=FurnishingProof.inside(village,house,1-house.width(),1,-1);
            var chair=FurnishingProof.inside(village,house,1-house.width(),1,0);
            var drawer=FurnishingProof.inside(village,house,house.width()-1,1,house.depth()-1);
            FurnishingProof.furniture(level,table,wood+"_table");
            FurnishingProof.furniture(level,chair,wood+"_chair");
            FurnishingProof.furniture(level,drawer,wood+"_drawer");
            if (dusk) {
                var sofa=FurnishingProof.inside(village,house,house.width()-1,1,0);
                FurnishingProof.furniture(level,sofa,"purple_sofa");
                subjects.add(sofa);
                subjects.add(FurnishingProof.inside(village,house,house.width()-1,2,1));
            }
            else { subjects.add(table); subjects.add(chair); }
            for (int dx : new int[]{0,1,2,-1,-2,3,-3}) for (int dz : new int[]{-3,2,3,-2,0}) {
                eyes.add(Vec3.atCenterOf(FurnishingProof.inside(village,house,dx,3,dz)));
                eyes.add(Vec3.atCenterOf(FurnishingProof.inside(village,house,dx,2,dz)).add(0,.3,0));
            }
        }
        return frame(player,name,subjects,eyes);
    }

    private static SeaWatch.View frame(ServerPlayer p,String name,List<BlockPos> subjects,List<Vec3> eyes) {
        var level=p.serverLevel();
        Vec3 center=Vec3.ZERO;
        for (var subject:subjects) center=center.add(Vec3.atCenterOf(subject));
        center=center.scale(1D/subjects.size());
        for (var eye:eyes) {
            level.getChunkAt(BlockPos.containing(eye));
            if (eye.distanceToSqr(center)<10 || !level.noCollision(p,new AABB(eye.x-.3,eye.y-1.61,eye.z-.3,eye.x+.3,eye.y+.18,eye.z+.3))) continue;
            boolean visible=true;
            for (var pos:subjects) {
                var hit=level.clip(new ClipContext(eye,Vec3.atCenterOf(pos),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
                if (hit.getType()!=HitResult.Type.MISS && !subjects.contains(hit.getBlockPos())) { visible=false; break; }
            }
            if (visible) return new SeaWatch.View(eye,center,name,false);
        }
        throw new AssertionError("No clear camera for "+name+" at "+subjects);
    }
}
