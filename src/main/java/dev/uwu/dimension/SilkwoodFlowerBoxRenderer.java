package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SilkwoodFlowerBoxRenderer implements BlockEntityRenderer<SilkwoodFlowerBoxBlockEntity> {
    private final BlockRenderDispatcher blocks;

    private SilkwoodFlowerBoxRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.getBlockRenderDispatcher();
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NativeFlowerBox.BLOCK_ENTITY.get(), SilkwoodFlowerBoxRenderer::new);
    }

    @Override
    public AABB getRenderBoundingBox(SilkwoodFlowerBoxBlockEntity planter) {
        return new AABB(planter.getBlockPos()).inflate(.125, 0, .125).expandTowards(0, 1.5, 0);
    }

    @Override
    public void render(SilkwoodFlowerBoxBlockEntity planter, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        var level = planter.getLevel();
        if (level == null || planter.isCovered()) return;
        var box = planter.getBlockState();
        var pos = planter.getBlockPos();
        var facing = box.getValue(SilkwoodFlowerBoxBlock.FACING);
        for (int slot = 0; slot < SilkwoodFlowerBoxBlockEntity.SLOTS; slot++) {
            if (!(planter.displayItem(slot).getItem() instanceof BlockItem item)) continue;
            var plant = item.getBlock().defaultBlockState();
            boolean tall = plant.getBlock() instanceof DoublePlantBlock;
            if (tall) plant = plant.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
            var root = SilkwoodFlowerBoxBlock.plantOffset(box, slot);
            // Full cubes such as leaves need enough room for a second plant in the same trough.
            float scale = plant.isCollisionShapeFullBlock(level, pos) ? .46F : .70F;
            poses.pushPose();
            poses.translate(root.x, root.y, root.z);
            poses.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
            poses.scale(scale, scale, scale);
            poses.translate(-.5, 0, -.5);
            blocks.renderSingleBlock(plant, poses, buffers, light, overlay);
            if (tall) {
                poses.translate(0, 1, 0);
                var upper = plant.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
                int upperLight = LevelRenderer.getLightColor(level, pos.above());
                blocks.renderSingleBlock(upper, poses, buffers, upperLight, overlay);
            }
            poses.popPose();
        }
    }
}
