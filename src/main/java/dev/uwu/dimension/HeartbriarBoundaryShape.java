package dev.uwu.dimension;

/** Geometric definition used by the rendered membrane and the server's soft contact response. */
final class HeartbriarBoundaryShape {
    static final double HORIZONTAL_RADIUS = 1.28;
    static final double MAX_STEP = .18;
    private static final Push NONE = new Push(0, 0);
    record Push(double x, double z) {}

    static double centerY(double feetY, double height) {
        return feetY + (height * .5 + .08);
    }

    static double verticalRadius(double height) {
        return height * .62 + .16;
    }

    static Push outward(double holderX, double feetY, double holderZ, double height,
                        double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ, double fallbackX, double fallbackZ) {
        // The largest horizontal slice touched by the target is the one nearest the equator.
        double cy = centerY(feetY, height);
        double y = gap(minY - cy, maxY - cy) / verticalRadius(height);
        if (y >= 1) return NONE;
        double radiusSquared = HORIZONTAL_RADIUS * HORIZONTAL_RADIUS * (1 - y * y);
        minX -= holderX;
        maxX -= holderX;
        minZ -= holderZ;
        maxZ -= holderZ;
        if (distanceSquared(minX, maxX, minZ, maxZ) >= radiusSquared) return NONE;

        double x = (minX + maxX) * .5, z = (minZ + maxZ) * .5;
        if (Math.hypot(x, z) < 1e-6) {
            x = fallbackX;
            z = fallbackZ;
        }
        double norm = Math.hypot(x, z);
        if (norm > 0) {
            x /= norm;
            z /= norm;
        } else {
            x = 1;
            z = 0;
        }

        double step = MAX_STEP;
        if (distanceSquared(minX + x * step, maxX + x * step, minZ + z * step, maxZ + z * step) >= radiusSquared) {
            // Find the first contact-free position to avoid kicking a grazing entity a full step.
            double lo = 0;
            for (int i = 0; i < 10; i++) {
                double mid = (lo + step) * .5;
                if (distanceSquared(minX + x * mid, maxX + x * mid, minZ + z * mid, maxZ + z * mid) < radiusSquared) {
                    lo = mid;
                } else {
                    step = mid;
                }
            }
        }
        return new Push(x * step, z * step);
    }

    private static double gap(double min, double max) {
        return Math.max(0, Math.max(min, -max));
    }

    private static double distanceSquared(double minX, double maxX, double minZ, double maxZ) {
        double x = gap(minX, maxX), z = gap(minZ, maxZ);
        return x * x + z * z;
    }

    private HeartbriarBoundaryShape() {}
}
