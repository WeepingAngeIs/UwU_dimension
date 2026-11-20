package dev.uwu.dimension;

/** Pure 2-D circle-sector vs. hurtbox geometry, relative to the player's axis. */
final class DawnpetalSector {
    static final double REVOLUTION = Math.PI * 2;
    private static final double EPS = 1e-9;

    static boolean hits(double r, double start, double sweep, double minX, double maxX, double minZ, double maxZ) {
        double x = Math.clamp(0.0, minX, maxX), z = Math.clamp(0.0, minZ, maxZ);
        if (x*x + z*z > r*r + EPS) return false;
        if (x*x + z*z <= EPS) return true;
        double angle = Math.atan2(z, x) - start;
        angle -= REVOLUTION * Math.floor(angle / REVOLUTION);
        if (angle <= sweep + EPS || angle >= REVOLUTION - EPS) return true;
        return ray(start, r, minX, maxX, minZ, maxZ) || ray(start + sweep, r, minX, maxX, minZ, maxZ);
    }

    private static boolean ray(double angle, double r, double minX, double maxX, double minZ, double maxZ) {
        double dx = Math.cos(angle), dz = Math.sin(angle);
        double from = 0, to = r;
        if (Math.abs(dx) < EPS) {
            if (minX > EPS || maxX < -EPS) return false;
        } else {
            from = Math.max(from, Math.min(minX / dx, maxX / dx));
            to = Math.min(to, Math.max(minX / dx, maxX / dx));
        }
        if (Math.abs(dz) < EPS) {
            if (minZ > EPS || maxZ < -EPS) return false;
        } else {
            from = Math.max(from, Math.min(minZ / dz, maxZ / dz));
            to = Math.min(to, Math.max(minZ / dz, maxZ / dz));
        }
        return from <= to + EPS;
    }

    private DawnpetalSector() {}
}
