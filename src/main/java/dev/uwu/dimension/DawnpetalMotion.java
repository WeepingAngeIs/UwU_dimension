package dev.uwu.dimension;

/** Model-space constants are measured on Dawnpetal's compact, uncentered 16-unit mesh. */
public final class DawnpetalMotion {
    public static final double GRIP_X = 12.625, GRIP_Y = 2.875, GRIP_Z = 8;
    public static final double PEAK_BLADE_BLOCKS = 1.90;
    // Face extrema and compact display scale are generated in tools/art/dawnpetal_dimensions.json.
    public static final double COMPACT_BLADE_PIXELS = Math.hypot(3.75, 14) * .90;
    public static final double PEAK_XY = PEAK_BLADE_BLOCKS * 16 / COMPACT_BLADE_PIXELS;

    private static double smooth(double x) {
        x = Math.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    public static double expansion(double age, int strike, int duration) {
        if (age <= 0 || age >= duration || strike < 1 || strike >= duration) return 0;
        double begin = .12 * strike;
        if (age <= strike) return smooth((age - begin) / (strike - begin));
        double release = strike + Math.min(2.0, (duration - strike) * .30);
        return 1 - smooth((age - release) / (duration - release));
    }

    public static double planeScale(double expansion, boolean firstPerson) {
        return 1 + expansion * ((firstPerson ? Math.min(1.35, PEAK_XY) : PEAK_XY) - 1);
    }

    public static double depthScale(double expansion) {
        return 1 + .35 * expansion;
    }

    private DawnpetalMotion() {}
}
