package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Quaternionf;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HeartShieldEffectsClient {
    private HeartShieldEffectsClient() {}

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(HeartShieldEffects.PETALFOLD_FLOWER.get(), sprites ->
                (options, level, x, y, z, dx, dy, dz) -> new PetalfoldViewParticle(level, x, y, z, dx, dy, dz, sprites, .063F, options));
        event.registerSpriteSet(HeartShieldEffects.PETALFOLD_HEART.get(), sprites ->
                (options, level, x, y, z, dx, dy, dz) -> new PetalfoldViewParticle(level, x, y, z, dx, dy, dz, sprites, .057F, options));
        event.registerSpecial(HeartShieldEffects.IMPACT.get(), (type, level, x, y, z, holderId, yaw, elevation) -> {
            if (level.getEntity((int) holderId) instanceof LivingEntity holder)
                HeartbriarShellClient.impact(holder, yaw, elevation);
            return null;
        });
        event.registerSpriteSet(HeartShieldEffects.CIRCUIT.get(), sprites ->
                (type, level, x, y, z, entityId, quality, unused) ->
                        new Circuit(level, x, y, z, (int) entityId, (int) quality, sprites));
    }

    private static final class Circuit extends TextureSheetParticle {
        private final int holderId;
        private final int quality;
        private final SpriteSet sprites;

        Circuit(ClientLevel level, double x, double y, double z, int holderId, int quality, SpriteSet sprites) {
            super(level, x, y, z);
            this.holderId = holderId;
            this.quality = quality;
            this.sprites = sprites;
            setSprite(sprites.get(0, 1));
            lifetime = HeartShieldEffects.PULSE_TICKS;
            hasPhysics = false;
        }

        @Override public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override public int getLightColor(float partial) {
            return 0xf000f0;
        }

        @Override public AABB getRenderBoundingBox(float partial) {
            return super.getRenderBoundingBox(partial).inflate(2.7);
        }

        @Override public void tick() {
            super.tick();
            if (level.getEntity(holderId) instanceof LivingEntity holder && HeartbriarShellClient.guarding(holder)) {
                setPos(holder.getX(), holder.getY() + holder.getBbHeight() * .1, holder.getZ());
            } else {
                remove();
            }
        }

        @Override public void render(VertexConsumer out, Camera camera, float partial) {
            if (!(level.getEntity(holderId) instanceof LivingEntity holder) || !HeartbriarShellClient.guarding(holder)) return;
            Vec3 offset = holder.getPosition(partial).add(0, holder.getBbHeight() * .1, 0)
                    .subtract(camera.getPosition());
            // Choose the visible side without turning the annulus into a billboard.
            float pitch = offset.y < 0 ? (float) -Math.PI / 2 : (float) Math.PI / 2;
            Quaternionf plane = new Quaternionf().rotateX(pitch).rotateZ((holder.tickCount + partial) * .016f);
            float progress = Math.min(1, (age + partial) / lifetime);
            setSprite(sprites.get(0, 1));
            quadSize = 1.18f;
            alpha = .48f + quality * .1f;
            renderRotatedQuad(out, plane, (float)offset.x, (float)offset.y, (float)offset.z, partial);
            quadSize = 1.22f + progress * 1.05f;
            alpha = (.38f + quality * .1f) * (1 - progress);
            renderRotatedQuad(out, plane, (float)offset.x, (float)offset.y + .018f,
                    (float)offset.z, partial);
            // Upright, separated circuit panels sweep upward around the shell.
            // Their center stays below the holder's eye line even when crouching.
            setSprite(sprites.get(1, 1));
            quadSize = .30f;
            alpha = (.57f + quality * .09f) * (.6f + .4f * (float)Math.sin(Math.PI * progress));
            int panels = quality == 0 ? 4 : 8;
            double baseAngle = (holder.tickCount + partial) * .016;
            for (int i = 0; i < panels; i++) {
                double angle = baseAngle + 2 * Math.PI * i / panels;
                float nx = (float)Math.sin(angle);
                float nz = (float)Math.cos(angle);
                float px = (float)offset.x + nx * 1.13f;
                float pz = (float)offset.z + nz * 1.13f;
                float py = (float)offset.y + holder.getBbHeight() * (.16f + .17f * progress);
                float yaw = (float)angle + (px * nx + pz * nz > 0 ? (float)Math.PI : 0);
                renderRotatedQuad(out, new Quaternionf().rotateY(yaw), px, py, pz, partial);
            }
        }
    }
}
