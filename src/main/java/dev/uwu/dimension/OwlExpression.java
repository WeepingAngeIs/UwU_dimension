package dev.uwu.dimension;

import java.util.UUID;

/** Individual blink cadence and a quiet, occasional lip movement while standing idle. */
public final class OwlExpression {
    private OwlExpression() {}

    /** Original eyelid index for callers that don't select a mouth expression. */
    public static int frame(UUID id, int ticks) {
        return textureFrame(id, ticks, false);
    }

    /** Indices 0–2 are the original open-mouth textures. 3 and 4 share their original eyelids. */
    public static int textureFrame(UUID id, int ticks, boolean standingIdle) {
        int seed = id.hashCode();
        int period = 95 + Math.floorMod(seed, 39);
        int clock = ticks + Math.floorMod(Integer.rotateLeft(seed, 11), period);
        int phase = Math.floorMod(clock, period);
        if (phase >= 10) return 0;
        int eyelids = phase >= 3 && phase < 6 ? 2 : 1;
        if (!standingIdle) return eyelids;
        int cycle = Math.floorDiv(clock, period);
        boolean closeLips = Math.floorMod(cycle, 3) == Math.floorMod(seed, 3);
        return closeLips ? eyelids + 2 : eyelids;
    }
}
