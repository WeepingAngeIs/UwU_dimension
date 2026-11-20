package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class RevivalPetalsClient {
    private static final int INTERVAL = 40;
    private static final int LIFETIME = 34;
    private static final int FADE_TIME = 8;
    private static final RenderType[] PETAL_TEXTURES = {
            petalTexture(0), petalTexture(1), petalTexture(2)
    };

    // The interval exceeds the lifetime, so each nearby player has at most one petal.
    private static final Map<Player, Petal> PETALS = new HashMap<>();
    private static ClientLevel level;

    private record Petal(Vec3 origin, long startTick, float scale, int sprite, double direction) {}

    private static RenderType petalTexture(int sprite) {
        return RenderType.entityTranslucent(UwUDimension.id("textures/effect/petal-" + sprite + ".png"));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (level != client.level) {
            PETALS.clear();
            level = client.level;
        }
        if (level == null) return;

        long now = level.getGameTime();
        var camera = client.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        boolean firstPerson = client.options.getCameraType().isFirstPerson();
        PETALS.entrySet().removeIf(entry -> entry.getKey().isRemoved()
                || !RevivalBlossomClient.nearby(entry.getKey(), eye)
                || now - entry.getValue().startTick() >= LIFETIME);

        var random = ThreadLocalRandom.current();
        for (Player player : level.players()) {
            if (Math.floorMod(now + player.getId(), INTERVAL) != 0
                    || !RevivalBlossomClient.visibleFromCamera(player, camera, firstPerson)
                    || !RevivalBlossomClient.nearby(player, eye)) continue;
            float scale = player.getScale();
            Vec3 origin = RevivalBlossomClient.anchor(player, 1).add(0, -.12 * scale, 0);
            PETALS.put(player, new Petal(origin, now, scale,
                    random.nextInt(PETAL_TEXTURES.length), random.nextDouble(Math.TAU)));
        }
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || PETALS.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (level == null || level != client.level) return;

        var camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        boolean firstPerson = client.options.getCameraType().isFirstPerson();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = level.getGameTime();
        var buffers = client.renderBuffers().bufferSource();
        var poses = event.getPoseStack();
        for (int sprite = 0; sprite < PETAL_TEXTURES.length; sprite++) {
            var layer = PETAL_TEXTURES[sprite];
            VertexConsumer vertices = null;
            for (var entry : PETALS.entrySet()) {
                Player player = entry.getKey();
                Petal petal = entry.getValue();
                if (petal.sprite() != sprite
                        || !RevivalBlossomClient.visibleFromCamera(player, camera, firstPerson)
                        || !RevivalBlossomClient.nearby(player, eye)) continue;

                float age = (now - petal.startTick()) + partialTick;
                if (age < 0 || age >= LIFETIME) continue;
                int opacity = Math.round(255 * Mth.clamp((LIFETIME - age) / FADE_TIME, 0, 1));
                float scale = petal.scale();
                double direction = petal.direction();
                double drift = age * .008 * scale;
                double flutter = Math.sin(age * .24) * .065 * scale;
                Vec3 at = petal.origin().subtract(eye).add(
                        Math.cos(direction) * drift - Math.sin(direction) * flutter,
                        -age * .020 * scale,
                        Math.sin(direction) * drift + Math.cos(direction) * flutter);
                float half = .067F * scale;
                if (vertices == null) vertices = buffers.getBuffer(layer);
                poses.pushPose();
                poses.translate(at.x, at.y, at.z);
                poses.mulPose(camera.rotation());
                poses.mulPose(Axis.ZP.rotationDegrees((float) Math.toDegrees(direction) + age * 8));
                poses.mulPose(Axis.XP.rotationDegrees((float) Math.sin(age * .22) * 68));
                var pose = poses.last();
                vertex(vertices, pose, -half, -half, 0, 1, opacity);
                vertex(vertices, pose,  half, -half, 1, 1, opacity);
                vertex(vertices, pose,  half,  half, 1, 0, opacity);
                vertex(vertices, pose, -half,  half, 0, 0, opacity);
                poses.popPose();
            }
            if (vertices != null) buffers.endBatch(layer);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose,
                               float x, float y, float u, float v, int opacity) {
        out.addVertex(pose, x, y, 0).setColor(255, 255, 255, opacity).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    private RevivalPetalsClient() {}
}
