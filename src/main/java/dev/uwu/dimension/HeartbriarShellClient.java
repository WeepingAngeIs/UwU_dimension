package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** A closed rose-glass membrane. Ambient particles are independent of its visibility. */
@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class HeartbriarShellClient {
    private static final int LATITUDES = 18, LONGITUDES = 40;
    private static final Vec3[][] NORMALS = normals();
    private static final int[][] COLORS = new int[LATITUDES + 1][LONGITUDES];
    private record Impact(Vec3 direction, int tick) {}
    private static final Map<LivingEntity, Impact> IMPACTS = new WeakHashMap<>();

    static void impact(LivingEntity holder, double yaw, double elevation) {
        double horizontal = Math.cos(elevation);
        Vec3 direction = new Vec3(Math.sin(yaw) * horizontal, Math.sin(elevation), Math.cos(yaw) * horizontal);
        IMPACTS.put(holder, new Impact(direction, holder.tickCount));
    }

    static boolean guarding(LivingEntity holder) {
        return holder.isAlive() && holder.isBlocking()
            && holder.getUseItem().is(HeartShields.HEARTBRIAR_SHIELD.get())
            && holder.getItemInHand(holder.getUsedItemHand()).is(HeartShields.HEARTBRIAR_SHIELD.get());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        IMPACTS.clear();
    }

    private static Vec3[][] normals() {
        Vec3[][] rows = new Vec3[LATITUDES + 1][LONGITUDES];
        for (int row = 0; row <= LATITUDES; row++) {
            double latitude = Math.PI * row / LATITUDES - Math.PI / 2;
            for (int col = 0; col < LONGITUDES; col++) {
                double a = 2 * Math.PI * col / LONGITUDES;
                rows[row][col] = new Vec3(Math.cos(latitude) * Math.cos(a), Math.sin(latitude),
                    Math.cos(latitude) * Math.sin(a));
            }
        }
        return rows;
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var buffers = mc.renderBuffers().bufferSource();
        var layer = RenderType.debugQuads();
        VertexConsumer out = null;
        for (var holder : mc.level.players()) {
            if (!guarding(holder)) {
                IMPACTS.remove(holder);
                continue;
            }
            if (holder.distanceToSqr(camera) > 48 * 48) continue;
            if (!event.getFrustum().isVisible(holder.getBoundingBox().inflate(1.6))) continue;
            if (out == null) out = buffers.getBuffer(layer);
            double height = holder.getBbHeight();
            Vec3 center = holder.getPosition(partial).add(0, HeartbriarBoundaryShape.centerY(0, height), 0).subtract(camera);
            boolean inside = mc.options.getCameraType().isFirstPerson() && event.getCamera().getEntity() == holder;
            Impact hit = IMPACTS.get(holder);
            // Rendering can skip a release and a new raise between frames.
            if (hit != null && (hit.tick() < holder.tickCount - holder.getTicksUsingItem()
                    || holder.tickCount - hit.tick() >= 15)) {
                IMPACTS.remove(holder);
                hit = null;
            }
            drawShell(out, event.getPoseStack().last(), center, HeartbriarBoundaryShape.verticalRadius(height), inside,
                hit, holder.tickCount + partial);
        }
        if (out != null) buffers.endBatch(layer);
    }

    private static void drawShell(VertexConsumer out, PoseStack.Pose pose, Vec3 center,
                                  double ry, boolean inside, Impact hit, float tick) {
        // Shared grid vertices keep every longitude joined, including the seam and poles.
        for (int r = 0; r <= LATITUDES; r++) for (int c = 0; c < LONGITUDES; c++) {
            Vec3 n = NORMALS[r][c];
            double x = center.x + n.x * HeartbriarBoundaryShape.HORIZONTAL_RADIUS;
            double y = center.y + n.y * ry;
            double z = center.z + n.z * HeartbriarBoundaryShape.HORIZONTAL_RADIUS;
            double grazing = 1 - Math.min(1, Math.abs(n.x*x+n.y*y+n.z*z) /
                Math.max(.001, Math.sqrt(x*x+y*y+z*z)));
            double rim = grazing * grazing;
            double shine = Math.max(0, n.x * -.48 + n.y * .76 + n.z * .44);
            double glint = Math.pow(shine, 18);
            double ripple = 0;
            if (hit != null) {
                double t = (tick - hit.tick()) / 15;
                if (t >= 0 && t < 1) {
                    double arc = Math.acos(Math.clamp(n.dot(hit.direction()), -1, 1));
                    double band = (arc - .12 - 1.7*t) / .15;
                    ripple = Math.exp(-band * band) * (1-t);
                }
            }
            int alpha = (int)(inside ? 11 + 10*rim + 29*ripple : 29 + 106*rim + 36*glint + 66*ripple);
            int green = (int)(90 + 71*rim + 40*glint + 35*ripple);
            int blue = Math.min(255, (int)(182 + 42*rim + 27*ripple));
            COLORS[r][c] = alpha << 24 | 255 << 16 | green << 8 | blue;
        }
        for (int r = 0; r < LATITUDES; r++) for (int c = 0; c < LONGITUDES; c++) {
            int next = (c + 1) % LONGITUDES;
            vertex(out, pose, center, ry, r, c);
            vertex(out, pose, center, ry, r + 1, c);
            vertex(out, pose, center, ry, r + 1, next);
            vertex(out, pose, center, ry, r, next);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 center, double ry,
                               int row, int col) {
        Vec3 n = NORMALS[row][col];
        out.addVertex(pose, (float)(center.x+n.x*HeartbriarBoundaryShape.HORIZONTAL_RADIUS), (float)(center.y+n.y*ry),
            (float)(center.z+n.z*HeartbriarBoundaryShape.HORIZONTAL_RADIUS)).setColor(COLORS[row][col]);
    }

    private HeartbriarShellClient() {}
}
