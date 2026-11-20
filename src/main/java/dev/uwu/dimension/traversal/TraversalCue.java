package dev.uwu.dimension.traversal;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Server-authored coordinates and game time for the presentation of a single action. */
public record TraversalCue(int actorId, Type type, long startTick, Vec3 origin, Vec3 anchor,
                           Direction face, int windupTicks, int lifetimeTicks) {
    public static final int PATCH_TICKS = 60;
    public static final double PATCH_RADIUS = 1.5;
    public enum Type { MOON_HOOK, SAKURA_CAST, STOP, MODE_ON, MODE_OFF, CLEAR }
}
