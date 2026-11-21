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
    private static final ResourceLocation CUPID_ARROW = Arsenal.id("textures/entity/cupid_heart_arrow.png");
    private static final ResourceLocation HARE = Arsenal.id("textures/entity/ribbonhare.png");
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HeartLauncher.SHOT.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(Arsenal.ARROW.get(), ctx -> new ArrowRenderer<HeartArrow>(ctx) {
            @Override public ResourceLocation getTextureLocation(HeartArrow arrow) { return arrow.rippleBow() ? ARROW : CUPID_ARROW; }
        });
        event.registerEntityRenderer(Arsenal.HARE.get(), ctx -> new RabbitRenderer(ctx) {
            @Override public ResourceLocation getTextureLocation(Rabbit rabbit) { return HARE; }
        });
    }
    @SubscribeEvent public static void poses(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(HeartLauncher.LAUNCHER.get(), Arsenal.id("recoil"), (stack, level, holder, seed) ->
                holder instanceof net.minecraft.world.entity.player.Player player
                    ? player.getCooldowns().getCooldownPercent(stack.getItem(), 0) : 0);
            for(var entry:CounterArsenal.ROSTER)if(entry.shape().equals("scythe"))ItemProperties.register(CounterArsenal.ITEMS.get(entry.id()).get(),Arsenal.id("element"),(stack,level,holder,seed)->stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getInt("uwu_element"));
            ItemProperties.register(UwUDimension.GLITCH.get(),Arsenal.id("form"),(stack,level,holder,seed)-> {
                return GlitchBow.currentIndex(stack)+1;
            });
            ItemProperties.register(UwUDimension.GLITCH.get(),Arsenal.id("glitch_ripple_draw"),(stack,level,holder,seed) ->
                GlitchBow.relic(stack)==Arsenal.Relic.RIPPLE_BOW && holder!=null && holder.getUseItem()==stack ? Math.min(1,holder.getTicksUsingItem()/20F) : 0F);
            ItemProperties.register(UwUDimension.GLITCH.get(),Arsenal.id("glitch_ripple_drawing"),(stack,level,holder,seed) ->
                GlitchBow.relic(stack)==Arsenal.Relic.RIPPLE_BOW && holder!=null && holder.isUsingItem() && holder.getUseItem()==stack ? 1F : 0F);
            ItemProperties.register(UwUDimension.GLITCH.get(),Arsenal.id("glitch_flower_credit"),(stack,level,holder,seed) ->
                GlitchBow.relic(stack)==Arsenal.Relic.VESPER_SCYTHE ? flowerCredit(stack,level) : 0);
            ItemProperties.register(UwUDimension.GLITCH.get(),Arsenal.id("glitch_scythe"),(stack,level,holder,seed) -> {
                if(!GlitchBow.isScythe(stack))return 0;
                int element=Math.floorMod(stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getInt("uwu_element"),4);
                return (GlitchBow.currentIndex(stack)+1)*4+element;
            });
            ItemProperties.register(UwUDimension.GLITCH.get(),Arsenal.id("static"),(stack,level,holder,seed)-> {
                var n=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                return level!=null&&n.getLong("uwu_static_until")>level.getGameTime()?1:0;
            });
            ItemProperties.register(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get(), Arsenal.id("element"), (stack,level,holder,seed) ->
                stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getInt("uwu_element"));
            ItemProperties.register(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get(), Arsenal.id("flower_credit"),
                (stack,level,holder,seed)->flowerCredit(stack,level));
            ItemProperties.register(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get(), Arsenal.id("smooth_cupid"),
                    (stack, level, holder, seed) -> Arsenal.hasSmoothCupidStyle(stack) ? 1F : 0F);
            for (Arsenal.Relic relic : new Arsenal.Relic[]{Arsenal.Relic.CUPID_BOW, Arsenal.Relic.RIPPLE_BOW}) {
                var bow = Arsenal.WEAPONS.get(relic).get();
                ItemProperties.register(bow, Arsenal.id("drawing"), (stack, level, holder, seed) ->
                        holder != null && holder.isUsingItem() && holder.getUseItem() == stack ? 1F : 0F);
                ItemProperties.register(bow, Arsenal.id("draw"), (stack, level, holder, seed) ->
                    holder != null && holder.getUseItem() == stack ? Math.min(1, holder.getTicksUsingItem() / 20F) : 0);
            }
        });
    }

    private static float flowerCredit(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.Level level) {
        return level != null && stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getLong("uwu.flower_credit_until") > level.getGameTime() ? 1 : 0;
    }
}
