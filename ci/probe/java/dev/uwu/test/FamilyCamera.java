package dev.uwu.test;

import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.Keeper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Arrange actual generated residents by a normal doorway, retaining each resident's real age and acquired job. */
final class FamilyCamera {
    private record Resident(Keeper keeper, Vec3 home, float yaw) {}
    private static final List<Resident> posed=new ArrayList<>();
    static SeaWatch.View arrange(ServerPlayer player) {
        var level=player.serverLevel();
        var village=BlossomVillage.first(level,false);
        var locals=level.getEntitiesOfClass(Keeper.class,new AABB(village.plaza()).inflate(58));
        var child=locals.stream().filter(Keeper::isBaby).findFirst().orElse(null);
        var workers=locals.stream().filter(k->!k.isBaby() && k.getVillagerData().getProfession()!=VillagerProfession.NONE
                && k.getVillagerData().getProfession()!=VillagerProfession.NITWIT).toList();
        if(child==null||workers.size()<2)return null;
        var first=workers.getFirst();
        var second=workers.stream().filter(k->k.getVillagerData().getProfession()!=first.getVillagerData().getProfession()).findFirst().orElse(null);
        if(second==null)return null;
        for(var house:village.houses()){
            var s=village.site();int front=house.z()-house.depth()*house.side();
            var spot=s.pos(house.x(),house.floor()+1,front-house.side()*2);
            var other=s.pos(house.x()+1,house.floor()+1,front-house.side()*2);
            var axis=Vec3.atCenterOf(other).subtract(Vec3.atCenterOf(spot));
            var street=s.pos(house.x(),house.floor()+1,front-house.side()*9);
            var center=Vec3.atBottomCenterOf(spot);
            var eye=Vec3.atCenterOf(street).add(axis.scale(2)).add(0,1.15,0);
            if(!level.getBlockState(BlockPos.containing(eye)).getCollisionShape(level,BlockPos.containing(eye)).isEmpty())continue;
            var subjects=List.of(first,child,second);
            for(int i=0;i<subjects.size();i++){
                var k=subjects.get(i);var pos=center.add(axis.scale((i-1)*1.05));
                pos=new Vec3(pos.x,house.floor()+1,pos.z);
                posed.add(new Resident(k,k.position(),k.getYRot()));
                if(k.isSleeping())k.stopSleeping();
                k.setNoAi(true);k.setNoGravity(true);k.getNavigation().stop();
                var gaze=eye.subtract(pos);
                float yaw=(float)Math.toDegrees(Math.atan2(-gaze.x,gaze.z));
                k.teleportTo(pos.x,pos.y,pos.z);k.setYRot(yaw);k.setYHeadRot(yaw);k.setYBodyRot(yaw);
            }
            System.out.println("FAMILY PORTRAIT generated "+first.getVillagerData().getProfession()+" + child + "+second.getVillagerData().getProfession()+" heights="+first.getBbHeight()+"/"+child.getBbHeight());
            return new SeaWatch.View(eye,center.add(0,1.45,0),"keeper-family",false);
        }
        throw new AssertionError("Could not frame a village family by their doorway");
    }
    static void release(){
        for(var p:posed){var k=p.keeper();var v=p.home();k.teleportTo(v.x,v.y,v.z);k.setNoAi(false);k.setNoGravity(false);k.setYRot(p.yaw());}
        posed.clear();
    }
}
