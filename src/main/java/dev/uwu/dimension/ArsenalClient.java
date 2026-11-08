package dev.uwu.dimension;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.RabbitRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Rabbit;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArsenalClient {
    private static final ResourceLocation ARROW = Arsenal.id("textures/entity/votive_arrow.png");
    private static final ResourceLocation HARE = Arsenal.id("textures/entity/ribbonhare.png");
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Arsenal.ARROW.get(), ctx -> new ArrowRenderer<HeartArrow>(ctx) {
            @Override public ResourceLocation getTextureLocation(HeartArrow arrow) { return ARROW; }
        });
        event.registerEntityRenderer(Arsenal.HARE.get(), ctx -> new RabbitRenderer(ctx) {
            @Override public ResourceLocation getTextureLocation(Rabbit rabbit) { return HARE; }
        });
    }
    @SubscribeEvent public static void poses(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(UwUDimension.GLITCH.get(),Arsenal.id("form"),(stack,level,holder,seed)-> {
                var n=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                return n.contains("uwu_form")?n.getInt("uwu_form")+1:0;
            });
            ItemProperties.register(UwUDimension.GLITCH.get(),Arsenal.id("static"),(stack,level,holder,seed)-> {
                var n=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                return level!=null&&n.getLong("uwu_static_until")>level.getGameTime()?1:0;
            });
            ItemProperties.register(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get(), Arsenal.id("element"), (stack,level,holder,seed) ->
                stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getInt("uwu_element"));
            for (Arsenal.Relic relic : new Arsenal.Relic[]{Arsenal.Relic.CUPID_BOW, Arsenal.Relic.RIPPLE_BOW}) {
                var bow = Arsenal.WEAPONS.get(relic).get();
                ItemProperties.register(bow, Arsenal.id("draw"), (stack, level, holder, seed) ->
                    holder != null && holder.getUseItem() == stack ? Math.min(1, holder.getTicksUsingItem() / 20F) : 0);
            }
        });
    }
}
