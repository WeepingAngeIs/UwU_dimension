package dev.uwu.test;

import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.GroveSites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Use an unobstructed real lane or chamber. No blocks are placed for these photographs. */
final class ArchitectureCamera {
    static SeaWatch.View view(ServerPlayer p,String name){
        var level=p.serverLevel();
        if(name.endsWith("joinery")) {
            var v=BlossomVillage.first(level,name.contains("dusk"));
            for(var h:v.houses()){
                var target=Vec3.atCenterOf(v.doorway(h).above(4));
                for(int offset:new int[]{13,-13,10,-10,7,-7,4,-4,0}){
                    int x=h.x()+offset, i=x-v.x()+v.road().length/2;
                    if(i<0||i>=v.road().length)continue;
                    var pos=v.site().pos(x,v.road()[i]+3,v.z()+v.bends()[i]);
                    var eye=Vec3.atCenterOf(pos);
                    level.getChunkAt(pos);level.getChunkAt(v.doorway(h));
                    if(!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()) continue;
                    var ray=level.clip(new ClipContext(eye,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                    if(ray.getType()==HitResult.Type.MISS || ray.getLocation().distanceToSqr(target)<6)
                        return new SeaWatch.View(eye,target,name,false);
                }
            }
            throw new AssertionError("No open lane with view of "+name+" at "+v.plaza());
        }
        var s=GroveSites.first(level);
        int lip=s.lip();
        int[] scene=switch(name){
            case "architecture-entrance" -> new int[]{0,s.entryY()+3,lip+1, 0,s.entryY()-3,lip+11};
            case "architecture-bathhouse" -> new int[]{-6,55,87, 10,57,99};
            case "architecture-archive" -> new int[]{16,-16,142, 8,-14,147};
            case "architecture-court" -> new int[]{-9,-15,143, -4,-13,160};
            case "architecture-shrine" -> new int[]{47,13,108, 53,12,116};
            default -> throw new IllegalArgumentException(name);
        };
        var eye=s.pos(scene[0],scene[1],scene[2]);
        var subject=s.pos(scene[3],scene[4],scene[5]);
        level.getChunkAt(eye); level.getChunkAt(subject);
        if(!level.getBlockState(eye).getCollisionShape(level,eye).isEmpty())
            throw new AssertionError("Camera inside "+level.getBlockState(eye)+" at "+eye);
        return new SeaWatch.View(Vec3.atCenterOf(eye),Vec3.atCenterOf(subject),name,false);
    }
}
