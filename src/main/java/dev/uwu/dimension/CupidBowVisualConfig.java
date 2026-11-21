package dev.uwu.dimension;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client preferences only; safe to register without loading any client renderer classes. */
public final class CupidBowVisualConfig {
    public static final double MIN_SCALE = .75;
    public static final double DEFAULT_SCALE = 1;
    public static final double MAX_SCALE = 1.5;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue HELD_SCALE;

    static {
        var builder = new ModConfigSpec.Builder();
        HELD_SCALE = builder.comment(
                "Visual held size of the detailed Cupid Bow only. The original pixel bow is unchanged.",
                "Scales around the grip in either hand. First person keeps its existing held position.",
                "Third person adds a small, bounded forward clearance as the bow grows.",
                "Does not change reach, projectiles, damage, inventory icons, or other weapons.")
                .defineInRange("heldScale", DEFAULT_SCALE, MIN_SCALE, MAX_SCALE);
        SPEC = builder.build();
    }

    private CupidBowVisualConfig() {}

    public static float heldScale() {
        double value = HELD_SCALE.get();
        // A malformed live config must never feed a non-finite matrix into the item renderer.
        if (!Double.isFinite(value)) value = DEFAULT_SCALE;
        return (float) Math.clamp(value, MIN_SCALE, MAX_SCALE);
    }
}
