package dev.uwu.test;
import dev.uwu.dimension.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
final class ForestCamera {
    private static ForestProof.Cluster forest;
    static SeaWatch.View frame(ServerPlayer player,String name){
        if(forest==null){try{forest=ForestProof.view(player.serverLevel());}catch(Exception e){throw new IllegalStateException(e);}}
        double x=forest.x()+.5,z=forest.z()+.5;
        var tree=forest.trees().stream().filter(t->t.x()==forest.x()&&t.z()==forest.z()).findFirst().orElseThrow();double y=tree.y();
        Vec3 eye,aim;
        switch(name){
            case "forest-canopy"->{eye=new Vec3(x+42,y+52,z+42);aim=new Vec3(x,y+28,z);}
            case "forest-understory"->{int ground=player.serverLevel().getChunkSource().getGenerator().getBaseHeight((int)x+8,(int)z+10,net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,player.serverLevel(),player.serverLevel().getChunkSource().randomState());eye=new Vec3(x+8,ground+2,z+10);aim=new Vec3(x+8,ground+11,z-25);}
            case "forest-edge"->{eye=new Vec3(x-50,y+24,z+35);aim=new Vec3(x,y+24,z);}
            default->throw new IllegalArgumentException(name);
        }
        player.serverLevel().getChunkAt(net.minecraft.core.BlockPos.containing(eye));
        return new SeaWatch.View(eye,aim,name,false);
    }
}
