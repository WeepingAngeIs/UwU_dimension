package dev.uwu.test;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Views an actually generated landmark from its front, side, base and crown. */
final class GiantSakuraCamera {
    private static GiantSakuraNaturalProof.Tree tree;
    static SeaWatch.View frame(ServerPlayer player,String name){
        try {
            if(tree==null){
                GiantSakuraNaturalProof.run(player.serverLevel());
                tree=GiantSakuraNaturalProof.find(player.serverLevel());
            }
            int h=tree.height();
            BlockPos eye,aim;
            switch(name){
                case "giant-sakura-front" -> {eye=tree.pos(0,11,-31);aim=tree.pos(0,12,0);}
                case "giant-sakura-side" -> {eye=tree.pos(30,12,0);aim=tree.pos(0,12,0);}
                case "giant-sakura-base" -> {eye=tree.pos(8,3,-11);aim=tree.pos(0,3,0);}
                case "giant-sakura-canopy" -> {eye=tree.pos(27,h+11,-27);aim=tree.pos(0,h+3,0);}
                default -> throw new IllegalArgumentException(name);
            }
            player.serverLevel().getChunkAt(eye);player.serverLevel().getChunkAt(aim);
            System.out.println("GIANT SAKURA CAMERA "+name+" base="+tree.pos(0,0,0)+" eye="+eye);
            return new SeaWatch.View(Vec3.atCenterOf(eye),Vec3.atCenterOf(aim),name,false);
        }catch(Exception error){throw new IllegalStateException("Natural giant sakura camera",error);}
    }
}
