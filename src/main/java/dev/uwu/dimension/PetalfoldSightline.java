package dev.uwu.dimension;

import org.joml.Matrix4fc;

final class PetalfoldSightline {
    private PetalfoldSightline() {}

    /** Opacity of this billboard next to the vanilla crosshair, in scaled GUI pixels. */
    static float opacity(Matrix4fc m, float size, int guiWidth, int guiHeight) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            float x = (corner & 1) == 0 ? -size : size;
            float y = (corner & 2) == 0 ? -size : size;
            float w = m.m03() * x + m.m13() * y + m.m33();
            // A billboard crossing the eye plane has no finite screen-space bounds.
            if (!(w > .0001F)) return 0;
            float sx = (m.m00() * x + m.m10() * y + m.m30()) / w * guiWidth * .5F;
            float sy = (m.m01() * x + m.m11() * y + m.m31()) / w * guiHeight * .5F;
            minX = Math.min(minX, sx);
            maxX = Math.max(maxX, sx);
            minY = Math.min(minY, sy);
            maxY = Math.max(maxY, sy);
        }
        float gap = Math.max(Math.max(minX, -maxX), Math.max(minY, -maxY));
        return Math.clamp((gap - 10) / 7, 0F, 1F);
    }
}
