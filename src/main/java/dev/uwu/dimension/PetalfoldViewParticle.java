package dev.uwu.dimension;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** One heart or sakura, with a separate first-person path for the hand-camera model. */
final class PetalfoldViewParticle extends TextureSheetParticle {
    private final Matrix4f billboardToClip = new Matrix4f();
    private Vec3 previousViewOffset;
    private Vec3 viewOffset;
    private Vec3 viewVelocityOffset;

    PetalfoldViewParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                          SpriteSet sprites, float size, PetalfoldParticleOptions options) {
        super(level, x, y, z);
        pickSprite(sprites);
        xd = dx;
        yd = dy;
        zd = dz;
        options.firstPerson().ifPresent(view -> {
            previousViewOffset = viewOffset = view.offset();
            viewVelocityOffset = view.velocityOffset();
        });
        lifetime = 15 + random.nextInt(8);
        quadSize = size;
        gravity = 0;
        friction = .9F;
        hasPhysics = false;
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    @Override public int getLightColor(float partial) { return 0xF000F0; }

    @Override public void tick() {
        super.tick();
        if (viewOffset != null) {
            previousViewOffset = viewOffset;
            viewOffset = viewOffset.add(viewVelocityOffset);
            viewVelocityOffset = viewVelocityOffset.scale(friction);
        }
        alpha = Math.min(1F, (lifetime - age) / 5F);
    }

    private boolean usingFirstPersonPath() {
        if (viewOffset == null) return false;
        var mc = Minecraft.getInstance();
        return mc.options.getCameraType().isFirstPerson() && mc.player != null
                && !mc.player.isSpectator() && mc.getCameraEntity() == mc.player;
    }

    @Override
    public AABB getRenderBoundingBox(float partial) {
        double px = Mth.lerp(partial, xo, x);
        double py = Mth.lerp(partial, yo, y);
        double pz = Mth.lerp(partial, zo, z);
        double radius = getQuadSize(partial) * Math.sqrt(2);
        var box = new AABB(px - radius, py - radius, pz - radius, px + radius, py + radius, pz + radius);
        return usingFirstPersonPath() ? box.move(previousViewOffset.lerp(viewOffset, partial)) : box;
    }

    @Override
    protected void renderRotatedQuad(VertexConsumer out, Quaternionf rotation, float x, float y, float z, float partial) {
        if (!usingFirstPersonPath()) {
            super.renderRotatedQuad(out, rotation, x, y, z, partial);
            return;
        }
        Vec3 offset = previousViewOffset.lerp(viewOffset, partial);
        x += (float) offset.x;
        y += (float) offset.y;
        z += (float) offset.z;
        // Use the matrices which draw these vertices, including actual FOV and vanilla view bob.
        billboardToClip.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix())
            .translate(x, y, z).rotate(rotation);
        var window = Minecraft.getInstance().getWindow();
        float visibility = PetalfoldSightline.opacity(billboardToClip, getQuadSize(partial),
                window.getGuiScaledWidth(), window.getGuiScaledHeight());
        if (visibility <= 0) return;
        float ageAlpha = alpha;
        try {
            alpha = ageAlpha * visibility;
            super.renderRotatedQuad(out, rotation, x, y, z, partial);
        } finally {
            alpha = ageAlpha;
        }
    }
}
