package dev.uwu.dimension;

import java.util.UUID;

/** Brief eyelid movement on a stable, individual cadence. No network messages or world state. */
public final class OwlExpression {
    private OwlExpression() {}

    public static int frame(UUID id, int ticks) {
        int seed = id.hashCode();
        int period = 95 + Math.floorMod(seed, 39);
        int phase = Math.floorMod(ticks + Math.floorMod(Integer.rotateLeft(seed, 11), period), period);
        if (phase >= 10) return 0;
        if (phase >= 3 && phase < 6) return 2;
        return 1;
    }
}
