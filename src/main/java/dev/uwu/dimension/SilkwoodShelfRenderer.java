package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SilkwoodShelfRenderer implements BlockEntityRenderer<SilkwoodShelfBlockEntity> {
    private final ItemRenderer items;

    private SilkwoodShelfRenderer(BlockEntityRendererProvider.Context context) {
        items = context.getItemRenderer();
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NativeShelf.BLOCK_ENTITY.get(), SilkwoodShelfRenderer::new);
    }

    @Override
    public AABB getRenderBoundingBox(SilkwoodShelfBlockEntity shelf) {
        return new AABB(shelf.getBlockPos()).expandTowards(0, 1, 0);
    }

    @Override
    public void render(SilkwoodShelfBlockEntity shelf, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        var level = shelf.getLevel();
        if (level == null || shelf.isCovered()) return;
        var facing = shelf.getBlockState().getValue(SilkwoodShelfBlock.FACING);
        int shelfLight = LevelRenderer.getLightColor(level, shelf.getBlockPos().above());
        for (int slot = 0; slot < SilkwoodShelfBlockEntity.SLOTS; slot++) {
            var stack = shelf.displayItem(slot);
            if (stack.isEmpty()) continue;
            var anchor = SilkwoodShelfBlock.displayOffset(facing, slot);
            poses.pushPose();
            poses.translate(anchor.x, anchor.y, anchor.z);
            poses.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
            int copies = stack.getCount() == 1 ? 1 : Math.min(5, 2 + (stack.getCount() - 1) / 16);
            for (int copy = 0; copy < copies; copy++) {
                poses.pushPose();
                // Fan duplicate sprites apart within this quadrant without hiding the front item.
                poses.translate((copy % 2 == 0 ? -1 : 1) * copy * .011, copy * .007, copy * .018);
                poses.scale(.375F, .375F, .375F);
                items.renderStatic(stack, ItemDisplayContext.FIXED, shelfLight, overlay, poses, buffers, level, 0);
                poses.popPose();
            }
            poses.popPose();
        }
    }
}
