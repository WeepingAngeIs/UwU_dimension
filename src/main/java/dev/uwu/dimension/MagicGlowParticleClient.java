package dev.uwu.dimension;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MagicGlowParticleClient {
    private MagicGlowParticleClient() {}

    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(MagicGlowParticles.HEART.get(), sprites -> provider(sprites, .22F));
        event.registerSpriteSet(MagicGlowParticles.FLOWER.get(), sprites -> provider(sprites, .25F));
    }

    private static net.minecraft.client.particle.ParticleProvider<SimpleParticleType> provider(SpriteSet sprites,
            float size) {
        return (type, level, x, y, z, dx, dy, dz) -> new Glow(level, x, y, z, dx, dy, dz, sprites, size);
    }

    private static final class Glow extends TextureSheetParticle {
        Glow(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites,
                float size) {
            super(level, x, y, z, dx, dy, dz);
            pickSprite(sprites);
            xd = dx * .35;
            yd = .035 + Math.abs(dy) * .25;
            zd = dz * .35;
            lifetime = 15 + random.nextInt(8);
            quadSize = size;
            gravity = 0;
            friction = .9F;
            hasPhysics = false;
        }

        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        @Override public int getLightColor(float partialTick) { return 0xF000F0; }
        @Override public void tick() {
            super.tick();
            alpha = Math.min(1.0F, (lifetime - age) / 5.0F);
        }
    }
}
