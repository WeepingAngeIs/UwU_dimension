package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class RevivalBlossomClient {
    private static final RenderType BLOSSOM = RenderType.entityTranslucent(
            UwUDimension.id("textures/effect/revival_blossom.png"));
    private static final int FRAMES = 64;
    private static final int FRAMES_PER_ROW = 8;
    private static final int FRAME_PIXELS = 96;
    private static final int ATLAS_PIXELS = FRAMES_PER_ROW * FRAME_PIXELS;
    private static final int TICKS_PER_FRAME = 2;
    // The widest nontransparent silhouette spans 92 of the 96 frame pixels.
    private static final float QUAD_SIZE = .40F * FRAME_PIXELS / 92F;

    @EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ItemModels {
        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                for (var item : java.util.List.of(RevivalBlossoms.DIRK.get(), RevivalBlossoms.AXE.get())) {
                    ItemProperties.register(item, UwUDimension.id("revival_phase"),
                            (stack, level, entity, seed) -> RevivalBlossoms.visualPhase(stack));
                }
            });
        }
    }

    static Vec3 anchor(Player player, float partialTick) {
        double yaw = Math.toRadians(Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot));
        double back = .55 * player.getScale();
        double right = -1.05 * player.getScale();
        return player.getPosition(partialTick).add(
                Math.cos(yaw) * right + Math.sin(yaw) * back,
                player.getBbHeight() * 1.15,
                Math.sin(yaw) * right - Math.cos(yaw) * back);
    }

    static boolean nearby(Player player, Vec3 camera) {
        return player.distanceToSqr(camera) < 64 * 64 && RevivalReady.visible(player);
    }

    static boolean visibleFromCamera(Player player, Camera camera, boolean firstPerson) {
        return !firstPerson || (camera.getEntity() != player && Minecraft.getInstance().player != player);
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        var buffers = client.renderBuffers().bufferSource();
        var poses = event.getPoseStack();
        var firstPerson = client.options.getCameraType().isFirstPerson();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        VertexConsumer vertices = null;
        for (Player player : client.level.players()) {
            if (!visibleFromCamera(player, camera, firstPerson) || !nearby(player, eye)) continue;
            if (vertices == null) vertices = buffers.getBuffer(BLOSSOM);
            Vec3 center = anchor(player, partialTick).subtract(eye);
            float half = QUAD_SIZE * player.getScale() * .5F;
            int frame = (int) Math.floorMod(client.level.getGameTime() / TICKS_PER_FRAME, FRAMES);

            poses.pushPose();
            poses.translate(center.x, center.y, center.z);
            poses.mulPose(camera.rotation());
            drawFrame(vertices, poses.last(), frame, half);
            poses.popPose();
        }
        if (vertices != null) buffers.endBatch(BLOSSOM);
    }

    private static void drawFrame(VertexConsumer out, PoseStack.Pose pose, int frame, float half) {
        int x = (frame % FRAMES_PER_ROW) * FRAME_PIXELS;
        int y = (frame / FRAMES_PER_ROW) * FRAME_PIXELS;
        float left = x / (float) ATLAS_PIXELS;
        float right = (x + FRAME_PIXELS) / (float) ATLAS_PIXELS;
        float top = y / (float) ATLAS_PIXELS;
        float bottom = (y + FRAME_PIXELS) / (float) ATLAS_PIXELS;
        vertex(out, pose, -half, -half, left, bottom);
        vertex(out, pose,  half, -half, right, bottom);
        vertex(out, pose,  half,  half, right, top);
        vertex(out, pose, -half,  half, left, top);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v) {
        out.addVertex(pose, x, y, 0).setColor(0xffffffff).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    private RevivalBlossomClient() {}
}
