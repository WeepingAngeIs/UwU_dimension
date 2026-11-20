package dev.uwu.dimension.client;

import dev.uwu.dimension.DawnpetalMotion;

/** Grip-space appearance of the main-hand axe in Dawnpetal Spin's world render pass. */
public final class DawnpetalSpinVisual {
    private static final double FULL_PLANE_SCALE = 2.65 * DawnpetalMotion.PEAK_XY;

    private DawnpetalSpinVisual() {}

    /** Age is the accepted cast age in game ticks, including the current fractional tick. */
    public static double growth(double age) {
        if (age > 4 && age < 8) return smooth((age - 4) / 4);
        if (age >= 8 && age <= 18) return 1;
        if (age > 18 && age < 22) return 1 - smooth((age - 18) / 4);
        return 0;
    }

    public static float scaleXY(double age) {
        return (float) (1 + growth(age) * (FULL_PLANE_SCALE - 1));
    }

    public static float scaleZ(double age) {
        return (float) DawnpetalMotion.depthScale(growth(age));
    }

    /** Applied after the baked display transform, around DawnpetalMotion's grip. */
    public static float gripRollDegrees(double age) {
        return (float) (-110 * (1 - growth(age)));
    }

    /**
     * World-model yaw offset added to the keyed root. The input is renderer body yaw minus
     * cast yaw in degrees. Use the nearest equivalent at the start of windup and again
     * at the start of recovery, then unwrap within each phase. A wrapped input that jumps
     * across 180 degrees mid-blend would also jump on screen.
     */
    public static float headingCorrectionDegrees(double age, double continuousBodyMinusCastDegrees) {
        if (age <= 0 || age >= 23) return 0;
        if (age < 8) return (float) (continuousBodyMinusCastDegrees * smooth(age / 8));
        if (age <= 18) return (float) continuousBodyMinusCastDegrees;
        return (float) (continuousBodyMinusCastDegrees * (1 - smooth((age - 18) / 5)));
    }

    private static double smooth(double unit) {
        return unit * unit * (3 - 2 * unit);
    }
}
