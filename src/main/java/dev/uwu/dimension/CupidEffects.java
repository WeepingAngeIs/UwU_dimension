package dev.uwu.dimension;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** The server sends one shape cue to each nearby viewer. The client draws its outline. */
final class CupidEffects {
    private static final Set<Player> FULL_DRAW = Collections.newSetFromMap(new WeakHashMap<>());
    static boolean inRange(net.minecraft.server.level.ServerPlayer viewer, double squaredDistance) {
        int radius = Mth.clamp(ReachLife.preference(viewer,"sakura_distance",24),2,24);
        return squaredDistance <= radius*radius;
    }

    static void resetDraw(Player player) {
        FULL_DRAW.remove(player);
    }

    static void fullDraw(ServerLevel level, Player player) {
        if (!FULL_DRAW.add(player)) return;
        ripple(level,player.position().add(0,player.getBbHeight()*.27,0),true,player.getYRot(),0);
    }

    static void trail(ServerLevel level, HeartArrow arrow) {
        Vec3 at = arrow.position();
        for (var viewer : level.players()) {
            double distance = viewer.getEyePosition().distanceToSqr(at);
            if (!inRange(viewer,distance)) continue;
            int mode = ReachLife.preference(viewer, "sakura_effects", 2);
            int interval = mode == 0 || distance > 20*20 ? 4 : mode == 1 ? 2 : 1;
            if (arrow.tickCount % interval == 0)
                level.sendParticles(viewer, CupidParticles.PASTEL_HEART.get(), true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    static void impact(ServerLevel level, Entity struck) {
        // The world-space origin surrounds the target's body for every ordinary observer.
        ripple(level,struck.position().add(0,struck.getBbHeight()*.27,0),false,struck.getYRot(),0);
    }

    static void impact(ServerLevel level, Vec3 at,net.minecraft.core.Direction face,float yaw) {
        ripple(level,at,false,yaw,face.ordinal()+1);
    }

    private static void ripple(ServerLevel level,Vec3 at,boolean charge,float yaw,int surface) {
        long seed=level.getRandom().nextLong();
        var sound=net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(UwUDimension.CUPID_RIPPLE.get());
        for (var viewer:level.players()) {
            double distance=viewer.getEyePosition().distanceToSqr(at);
            if(!inRange(viewer,distance))continue;
            int quality=Mth.clamp(ReachLife.preference(viewer,"sakura_effects",2),0,2);
            if(distance>20*20)quality=0;
            // count=0 transmits one local animation, with its quality in the X-speed field.
            level.sendParticles(viewer,charge?CupidParticles.DRAW_HEART.get():CupidParticles.IMPACT_OUTLINE.get(),
                    true,at.x,at.y,at.z,0,quality,yaw,surface,1);
            float volume=(charge?.30F:.19F)*(quality==0?.55F:quality==1?.80F:1F);
            viewer.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(sound,
                    net.minecraft.sounds.SoundSource.PLAYERS,at.x,at.y,at.z,volume,charge?1F:1.07F,seed));
        }
    }
}
