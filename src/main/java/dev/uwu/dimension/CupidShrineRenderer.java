package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Draws the two approved components at each visible native shrine. */
@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CupidShrineRenderer implements BlockEntityRenderer<CupidShrine.Marker> {
    private CupidShrineRenderer(BlockEntityRendererProvider.Context context) {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CupidShrine.ENTITY.get(), CupidShrineRenderer::new);
    }

    @Override public AABB getRenderBoundingBox(CupidShrine.Marker shrine) {
        return new AABB(shrine.getBlockPos()).inflate(.06, 0, .06).expandTowards(0, .8, 0);
    }

    @Override public void render(CupidShrine.Marker shrine, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light, int overlay) {
        var level = shrine.getLevel();
        var state = shrine.getBlockState();
        if (level == null || !level.dimension().equals(ReefLife.REACH) || !state.is(CupidShrine.BLOCK.get())) return;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        // The model faces south at zero, with Minecraft's facing yaw turning the other way.
        float yaw = -state.getValue(CupidShrine.FACING).toYRot();
        poses.pushPose();
        try {
            poses.translate(.5, 0, .5);
            if (SeatedCupidStatueVisual.draw(poses, buffers, light, yaw)) {
                SeatedCupidFollowerFlower.draw(poses, buffers, yaw, camera, level.getGameTime());
            }
        } finally {
            poses.popPose();
        }
    }

    /** The ring is local geometry for the nearest loaded shrine, with no recurring server packets. */
    @EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
    public static final class GroundCircle {
        private static final int POINTS = 40;
        private static final int PASTEL_PINK = 0x8CFFB8D5;
        private static final double HEIGHT = .04;
        private static final float HALF_SIZE = .095F;
        private static final float[] X = new float[POINTS];
        private static final float[] Z = new float[POINTS];

        static {
            for (int point = 0; point < POINTS; point++) {
                double angle = 2 * Math.PI * point / POINTS;
                X[point] = (float) (CupidShrineVisits.RADIUS * Math.cos(angle));
                Z[point] = (float) (CupidShrineVisits.RADIUS * Math.sin(angle));
            }
        }

        private GroundCircle() {}

        @SubscribeEvent
        public static void render(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            var client = Minecraft.getInstance();
            if (client.level == null || client.player == null || client.player.isSpectator()) return;
            var nearest = CupidShrineVisits.closest(client.level, client.player.position(), 12, -4, 4);
            if (nearest == null) return;
            Vec3 base = Vec3.atBottomCenterOf(nearest).subtract(event.getCamera().getPosition());
            var buffers = client.renderBuffers().bufferSource();
            var layer = RenderType.debugQuads();
            var out = buffers.getBuffer(layer);
            var pose = event.getPoseStack().last();
            float y = (float) (base.y + HEIGHT);
            for (int point = 0; point < POINTS; point++) {
                float x = (float) (base.x + X[point]);
                float z = (float) (base.z + Z[point]);
                out.addVertex(pose, x, y, z - HALF_SIZE).setColor(PASTEL_PINK);
                out.addVertex(pose, x - HALF_SIZE, y, z).setColor(PASTEL_PINK);
                out.addVertex(pose, x, y, z + HALF_SIZE).setColor(PASTEL_PINK);
                out.addVertex(pose, x + HALF_SIZE, y, z).setColor(PASTEL_PINK);
            }
            buffers.endBatch(layer);
        }
    }
}
