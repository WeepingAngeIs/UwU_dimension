package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Optional hand display using integration.3's original animated follower sheet. */
public final class SeatedCupidFollowerFlower {
    private static final RenderType FLOWER =
            RenderType.entityTranslucent(UwUDimension.id("textures/effect/revival_blossom.png"));
    private static final int COLUMNS = 8;
    private static final int FRAMES = 64;
    private static final int TICKS_PER_FRAME = 2;

    private SeatedCupidFollowerFlower() {}

    /** Use the same unrotated base pose and yaw as the sculpture; the sheet faces the camera. */
    public static void draw(PoseStack atBase, MultiBufferSource buffers, float yawDegrees, Camera camera, long gameTime) {
        int frame = Math.floorMod(gameTime / TICKS_PER_FRAME, FRAMES);
        float left = (frame % COLUMNS) / (float) COLUMNS;
        float top = (frame / COLUMNS) / (float) COLUMNS;
        float cell = 1.0F / COLUMNS;
        float right = left + cell;
        float bottom = top + cell;
        float half = SeatedCupidStatueVisual.HAND_FLOWER_WIDTH / 2;
        var offset = SeatedCupidStatueVisual.handOffset(yawDegrees);

        atBase.pushPose();
        try {
            atBase.translate(offset.x, offset.y, offset.z);
            atBase.mulPose(camera.rotation());
            var pose = atBase.last();
            var vertices = buffers.getBuffer(FLOWER);
            vertex(vertices, pose, -half, -half, left, bottom);
            vertex(vertices, pose, half, -half, right, bottom);
            vertex(vertices, pose, half, half, right, top);
            vertex(vertices, pose, -half, half, left, top);
        } finally {
            atBase.popPose();
        }
    }

    private static void vertex(VertexConsumer sink, PoseStack.Pose pose, float x, float y, float u, float v) {
        sink.addVertex(pose, x, y, 0).setColor(0xffffffff).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }
}
