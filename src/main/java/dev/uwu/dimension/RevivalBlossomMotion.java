package dev.uwu.dimension;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Client-tick companion motion, independent of the camera and render frame rate. */
final class RevivalBlossomMotion {
    private static final double SPRING = .14;
    private static final double DAMPING = .68;
    private static final double HORIZONTAL_LAG = .85;
    private static final double VERTICAL_LAG = .16;
    static final double SNAP_DISTANCE_SQUARED = 16;

    private Vec3 previous;
    private Vec3 position;
    private Vec3 target;
    private Vec3 velocity;
    private int sampledTick;
    private int relocationTicks;

    RevivalBlossomMotion(Vec3 at, int tick) {
        reset(at, tick, 0);
    }

    void reset(Vec3 at, int tick, int interpolationTicks) {
        previous = position = target = at;
        velocity = Vec3.ZERO;
        sampledTick = tick;
        relocationTicks = interpolationTicks;
    }

    void follow(Vec3 at, int tick, float scale) {
        int elapsed = tick - sampledTick;
        if (elapsed < 0 || elapsed > 1 || at.distanceToSqr(target) >= SNAP_DISTANCE_SQUARED) {
            reset(at, tick, 0);
            return;
        }
        if (elapsed == 0) return;
        sampledTick = tick;
        target = at;
        previous = position;
        if (relocationTicks > 0) {
            // Vanilla interpolates remote relocations. Follow that existing interpolation without adding lag.
            relocationTicks--;
            position = at;
            velocity = Vec3.ZERO;
            return;
        }

        Vec3 proposed = position.add(velocity.add(at.subtract(position).scale(SPRING)).scale(DAMPING));
        Vec3 lag = proposed.subtract(at);
        double maxLag = HORIZONTAL_LAG * scale;
        double distance = lag.horizontalDistance();
        double horizontalScale = distance > maxLag ? maxLag / distance : 1;
        double verticalLimit = VERTICAL_LAG * scale;
        position = at.add(lag.x * horizontalScale, Mth.clamp(lag.y, -verticalLimit, verticalLimit),
                lag.z * horizontalScale);
        velocity = position.subtract(previous);
        if (position.distanceToSqr(at) < 1e-6 && velocity.lengthSqr() < 1e-6) {
            position = at;
            velocity = Vec3.ZERO;
        }
    }

    Vec3 sample(float partialTick) {
        return previous.lerp(position, Mth.clamp(partialTick, 0, 1));
    }
}
