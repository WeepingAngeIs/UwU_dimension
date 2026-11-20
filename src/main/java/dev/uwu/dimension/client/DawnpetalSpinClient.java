package dev.uwu.dimension.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import dev.uwu.dimension.DawnpetalSpinCue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DawnpetalSpinClient {
    private static boolean animatorReady;

    private DawnpetalSpinClient() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!ModList.get().isLoaded("playeranimator")) return;
            try {
                DawnpetalSpinAnimator.register();
                animatorReady = true;
            } catch (LinkageError error) {
                LogUtils.getLogger().warn("Dawnpetal Spin could not initialize PlayerAnimator", error);
            }
        });
    }

    public static DawnpetalSpinCue.Frame renderFrame(Player player, float partialTick) {
        // Inventory player previews render with the GUI's orthographic projection.
        if (!RenderSystem.isOnRenderThread() || RenderSystem.getVertexSorting() == VertexSorting.ORTHOGRAPHIC_Z) {
            return null;
        }
        return DawnpetalSpinCue.frame(player, partialTick, player == Minecraft.getInstance().player);
    }

    public static boolean animatorFirstPersonPass(Player player) {
        return animatorReady && player == Minecraft.getInstance().getCameraEntity()
                && DawnpetalSpinAnimator.firstPersonPass();
    }

    public static boolean hasWorldPose(Player player, DawnpetalSpinCue.Frame frame) {
        return animatorReady && DawnpetalSpinAnimator.hasLayerAndClip(player, frame.arm());
    }
}
