package dev.uwu.dimension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FallsongEffectsClient {
    private FallsongEffectsClient() {}

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(HeartShieldEffects.CASCADE.get(), sprites ->
                (type, level, x, y, z, id, quality, unused) ->
                        new Emitter(level, x, y, z, (int)id, (int)quality, sprites));
    }

    private static final class Emitter extends NoRenderParticle {
        private final int entityId;
        private final int quality;
        private final SpriteSet sprites;

        Emitter(ClientLevel level, double x, double y, double z, int entityId, int quality, SpriteSet sprites) {
            super(level, x, y, z);
            this.entityId = entityId;
            this.quality = Mth.clamp(quality, 0, 2);
            this.sprites = sprites;
            lifetime = 14;
        }

        @Override public void tick() {
            if (age++ >= lifetime || !(level.getEntity(entityId) instanceof LivingEntity target)) {
                remove();
                return;
            }
            if ((age - 1) % (3 - quality) != 0) return;
            int strands = quality + 1;
            double radius = Math.min(.4, target.getBbWidth() * .38);
            for (int i = 0; i < strands; i++) {
                double theta = target.getId() * .7 + 2 * Math.PI * i / strands;
                double sx = target.getX() + radius * Math.cos(theta);
                double sz = target.getZ() + radius * Math.sin(theta);
                Minecraft.getInstance().particleEngine.add(new WaterfallStreak(
                        level, sx, target.getY() + .04, sz, sprites));
                if (quality == 2 && age % 4 == 1) {
                    level.addParticle(ParticleTypes.SPLASH, sx, target.getY(), sz, 0, .045, 0);
                }
            }
        }
    }

    private static final class WaterfallStreak extends TextureSheetParticle {
        WaterfallStreak(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            pickSprite(sprites);
            quadSize = .29f;
            yd = -.045;
            gravity = .22f;
            friction = .96f;
            lifetime = 10;
            hasPhysics = false;
        }

        @Override public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override public int getLightColor(float partial) {
            return 0xf000f0;
        }

        @Override public void tick() {
            super.tick();
            alpha = Math.max(0, 1f - age / (float)lifetime);
        }
    }
}
