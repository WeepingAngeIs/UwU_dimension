package dev.uwu.dimension;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Keeps the normal zombie-villager profession/armor layers for the custom faction undead. */
@EventBusSubscriber(modid="uwudimension",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class KeeperZombieRenderer extends ZombieVillagerRenderer {
    public KeeperZombieRenderer(EntityRendererProvider.Context context) { super(context); }

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(People.PETAL_ZOMBIE.get(), KeeperZombieRenderer::new);
        event.registerEntityRenderer(People.DUSK_ZOMBIE.get(), KeeperZombieRenderer::new);
    }
}
