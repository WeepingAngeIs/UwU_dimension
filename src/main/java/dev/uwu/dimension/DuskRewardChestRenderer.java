package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DuskRewardChestRenderer implements BlockEntityRenderer<NativeRewardChestBlockEntity> {
    private static final ModelResourceLocation LID = ModelResourceLocation.standalone(
            UwUDimension.id("block/dusk_reward_chest_lid"));
    private final BlockRenderDispatcher blocks;

    private DuskRewardChestRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.getBlockRenderDispatcher();
    }

    @SubscribeEvent
    public static void registerLid(ModelEvent.RegisterAdditional event) {
        event.register(LID);
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NativeRewardChests.BLOCK_ENTITY.get(), DuskRewardChestRenderer::new);
    }

    @Override
    public boolean shouldRender(NativeRewardChestBlockEntity chest, Vec3 camera) {
        // The lower half is chunk geometry. Keep the lid visible across the same terrain view distance.
        return chest.getBlockState().is(NativeRewardChests.DUSK.get());
    }

    @Override
    public AABB getRenderBoundingBox(NativeRewardChestBlockEntity chest) {
        return new AABB(chest.getBlockPos()).inflate(0.3, 0, 0.3).expandTowards(0, 0.6, 0);
    }

    @Override
    public void render(NativeRewardChestBlockEntity chest, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        var state = chest.getBlockState();
        if (!state.is(NativeRewardChests.DUSK.get())) return;
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        float remaining = 1.0F - chest.lidOpenness(partialTick);
        float angle = 90.0F * (1.0F - remaining * remaining * remaining);
        var lid = blocks.getBlockModelShaper().getModelManager().getModel(LID);

        poses.pushPose();
        poses.translate(0.5, 0, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        poses.translate(-0.5, 0, -0.5);
        // The north-facing model has its rear hinge along X at Y=9, Z=15 model pixels.
        poses.translate(0, 9.0 / 16, 15.0 / 16);
        poses.mulPose(Axis.XP.rotationDegrees(angle));
        poses.translate(0, -9.0 / 16, -15.0 / 16);
        blocks.getModelRenderer().renderModel(poses.last(), buffers.getBuffer(Sheets.solidBlockSheet()),
                state, lid, 1.0F, 1.0F, 1.0F, light, overlay, ModelData.EMPTY, RenderType.solid());
        poses.popPose();
    }
}
