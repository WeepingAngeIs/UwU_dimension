package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Client-side sculpture for a caller that already owns the shrine's visible base. */
@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SeatedCupidStatueVisual {
    private static final ModelResourceLocation MODEL =
            ModelResourceLocation.standalone(UwUDimension.id("block/seated_cupid_twin_tails"));
    private static final Vec3 HAND_CENTER = new Vec3(0.060, 1.133, 0.372);
    public static final float HAND_FLOWER_WIDTH = 0.26F;

    private SeatedCupidStatueVisual() {}

    @SubscribeEvent
    public static void registerModel(ModelEvent.RegisterAdditional event) {
        event.register(MODEL);
    }

    /** Offset from the pedestal base, rotated about the same +Y axis as the statue. */
    public static Vec3 handOffset(float yawDegrees) {
        return HAND_CENTER.yRot((float) Math.toRadians(yawDegrees));
    }

    /**
     * Supply an unrotated pose translated to the pedestal base center, in block units.
     * Zero yaw faces +Z. The caller owns range checks, visibility and the buffer flush.
     */
    public static boolean draw(PoseStack atBase, MultiBufferSource buffers, int packedLight, float yawDegrees) {
        var client = Minecraft.getInstance();
        var models = client.getModelManager();
        var model = models.getModel(MODEL);
        if (model == models.getMissingModel()) return false;

        atBase.pushPose();
        try {
            atBase.mulPose(Axis.YP.rotationDegrees(yawDegrees));
            var stone = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
            client.getBlockRenderer().getModelRenderer().renderModel(atBase.last(), stone, null, model,
                    1.0F, 1.0F, 1.0F, packedLight, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        } finally {
            atBase.popPose();
        }
        return true;
    }
}
