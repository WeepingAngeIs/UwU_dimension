package dev.uwu.dimension;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

final class FallsongEffects {
    private FallsongEffects() {}

    static void lift(ServerLevel level, LivingEntity lifted) {
        for (var viewer : level.players()) {
            if (!CupidEffects.inRange(viewer, viewer.distanceToSqr(lifted))) continue;
            int quality = Mth.clamp(ReachLife.preference(viewer, "sakura_effects", 2), 0, 2);
            // Send one cue. Each client lays down a small cascade along the real lift path.
            level.sendParticles(viewer, HeartShieldEffects.CASCADE.get(), false,
                    lifted.getX(), lifted.getY() + .04, lifted.getZ(),
                    0, lifted.getId(), quality, 0, 1);
        }
    }
}
