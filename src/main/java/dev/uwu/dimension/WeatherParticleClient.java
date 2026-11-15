package dev.uwu.dimension;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WeatherParticleClient {
    private WeatherParticleClient() {}
    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(WeatherParticles.SNOW_HEART.get(), sprites ->
                (type, level, x, y, z, dx, dy, dz) -> new HeartFlake(level, x, y, z, sprites));
    }
    private static final class HeartFlake extends TextureSheetParticle {
        HeartFlake(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            pickSprite(sprites);
            yd = -.025;
            lifetime = 70;
            quadSize = .055F;
            alpha = .65F;
            hasPhysics = false;
        }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    }
}

