package dev.uwu.dimension;

import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid="uwudimension", bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class RevivalBlossomClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (var item : java.util.List.of(RevivalBlossoms.DIRK.get(), RevivalBlossoms.AXE.get()))
                ItemProperties.register(item, UwUDimension.id("revival_phase"), (stack,level,entity,seed) -> RevivalBlossoms.visualPhase(stack));
        });
    }
    private RevivalBlossomClient() {}
}
