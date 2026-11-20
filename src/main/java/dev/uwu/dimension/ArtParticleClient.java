package dev.uwu.dimension;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid="uwudimension", bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class ArtParticleClient {
    private enum Shape { GRASS, ICE, WIND, WATER }

    private ArtParticleClient() {}

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ArtParticles.GRASS.get(), sprites -> provider(sprites, Shape.GRASS));
        event.registerSpriteSet(ArtParticles.ICE.get(), sprites -> provider(sprites, Shape.ICE));
        event.registerSpriteSet(ArtParticles.WIND.get(), sprites -> provider(sprites, Shape.WIND));
        event.registerSpriteSet(ArtParticles.WATER.get(), sprites -> provider(sprites, Shape.WATER));
    }

    private static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Shape shape) {
        return (type, level, x, y, z, dx, dy, dz) -> new Art(level, x, y, z, dx, dy, dz, sprites, shape);
    }

    private static final class Art extends TextureSheetParticle {
        private final Shape shape;
        private final double anchorY;

        Art(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                SpriteSet sprites, Shape shape) {
            super(level, x, y, z);
            this.shape = shape;
            this.anchorY = y;
            pickSprite(sprites);
            lifetime = shape == Shape.GRASS || shape == Shape.WIND ? 12 : 10;
            quadSize = switch(shape) {
                case GRASS -> .24F;
                case ICE -> .20F;
                case WIND -> .15F;
                case WATER -> .12F;
            };
            xd = dx * .15;
            yd = dy * .15;
            zd = dz * .15;
            gravity = 0;
            friction = .92F;
            hasPhysics = false;
            // Blade tufts are based at the emitted floor point instead of hovering at it.
            if(shape == Shape.GRASS) this.y = this.yo = y + getQuadSize(0);
            if(shape == Shape.WIND) this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        }

        @Override
        public FacingCameraMode getFacingCameraMode() {
            return shape == Shape.GRASS || shape == Shape.ICE ? FacingCameraMode.LOOKAT_Y : FacingCameraMode.LOOKAT_XYZ;
        }

        @Override
        public float getQuadSize(float partial) {
            float ageFraction = (age + partial) / lifetime;
            float unfold = Mth.clamp((age + partial + 1) / 4F, 0, 1);
            float fold = Mth.clamp((lifetime - age - partial) / 4F, 0, 1);
            return quadSize * (shape == Shape.GRASS ? Math.min(unfold, fold) : 1F - .15F * ageFraction);
        }

        @Override
        public void tick() {
            super.tick();
            float progress = (float)age / lifetime;
            alpha = Mth.clamp((lifetime - age) / 4F, 0, 1);
            switch(shape) {
                case GRASS -> y = anchorY + getQuadSize(0) + Math.sin(progress * Math.PI) * .05;
                case ICE -> yd = -.003;
                case WIND -> {
                    oRoll = roll;
                    roll += .10F;
                    yd = .008;
                }
                case WATER -> yd = .018 - .04 * progress;
            }
        }

        @Override
        public int getLightColor(float partialTick) { return 0xF000F0; }

        @Override
        public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    }
}
