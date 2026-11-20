package dev.uwu.dimension;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HeartShieldsClient {
    private HeartShieldsClient() {}

    @SubscribeEvent
    public static void registerBlockingPose(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            var blocking = ResourceLocation.fromNamespaceAndPath("uwudimension", "blocking");
            for (var shield : HeartShields.SHIELDS) {
                ItemProperties.register(shield.get(), blocking, (stack, level, holder, seed) ->
                    holder != null && holder.isUsingItem() && holder.getUseItem() == stack
                        && holder.getItemInHand(holder.getUsedItemHand()) == stack ? 1.0F : 0.0F);
            }
            ItemProperties.register(HeartShields.HEARTWATER_WINDOW_SHIELD.get(),
                ResourceLocation.fromNamespaceAndPath("uwudimension", "fallsong_charged"),
                (stack, level, holder, seed) -> Fallsong.isCharged(stack) ? 1.0F : 0.0F);
        });
    }
}
