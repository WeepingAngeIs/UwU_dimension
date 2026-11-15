package dev.uwu.dimension;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** The server sends one shape cue to each nearby viewer. The client draws its outline. */
final class CupidEffects {
    private static final Set<Player> FULL_DRAW = Collections.newSetFromMap(new WeakHashMap<>());
    static boolean inRange(net.minecraft.server.level.ServerPlayer viewer, double squaredDistance) {
        int radius = Mth.clamp(ReachLife.preference(viewer,"sakura_distance",12),2,24);
        return squaredDistance <= radius*radius;
    }

    static void resetDraw(Player player) {
        FULL_DRAW.remove(player);
    }

    static void fullDraw(ServerLevel level, Player player) {
        if (!FULL_DRAW.add(player)) return;
        int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (player.getUsedItemHand() == InteractionHand.OFF_HAND) side *= -1;
        Vec3 hand = Vec3.directionFromRotation(0, player.getYRot()+90).scale(side*.42);
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(.9)).add(hand).add(0,-.15,0);
        for (var viewer : level.players()) {
            if (inRange(viewer,viewer.getEyePosition().distanceToSqr(at)))
                level.sendParticles(viewer, CupidParticles.DRAW_HEART.get(), false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    static void trail(ServerLevel level, HeartArrow arrow) {
        Vec3 at = arrow.position();
        for (var viewer : level.players()) {
            double distance = viewer.getEyePosition().distanceToSqr(at);
            if (!inRange(viewer,distance)) continue;
            int mode = ReachLife.preference(viewer, "sakura_effects", 2);
            int interval = mode == 0 || distance > 20*20 ? 4 : mode == 1 ? 2 : 1;
            if (arrow.tickCount % interval == 0)
                level.sendParticles(viewer, CupidParticles.PASTEL_HEART.get(), false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    static void impact(ServerLevel level, Entity struck) {
        Vec3 at = struck.position().add(0, struck.getBbHeight()+.82, 0);
        for (var viewer : level.players()) {
            double distance = viewer.getEyePosition().distanceToSqr(at);
            if (!inRange(viewer,distance)) continue;
            int mode = Mth.clamp(ReachLife.preference(viewer,"sakura_effects",2), 0, 2);
            if (distance > 20*20) mode = 0;
            // With count zero, vanilla passes the X speed to the provider exactly once.
            level.sendParticles(viewer, CupidParticles.IMPACT_OUTLINE.get(), false, at.x, at.y, at.z, 0, mode, 0, 0, 1);
        }
    }
}
