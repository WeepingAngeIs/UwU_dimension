package dev.uwu.dimension;

import net.minecraft.client.renderer.entity.DolphinRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Dolphin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="uwudimension",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ReefRenderer {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ReefLife.DOLPHIN.get(),ctx->new DolphinRenderer(ctx){
            @Override public ResourceLocation getTextureLocation(Dolphin dolphin){return UwUDimension.id("textures/entity/ribbon_dolphin.png");}
        });
    }
}
