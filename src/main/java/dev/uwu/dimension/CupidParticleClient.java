package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid="uwudimension", bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class CupidParticleClient {
    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(CupidParticles.PASTEL_HEART.get(), sprites ->
                (type, level, x,y,z, dx,dy,dz) -> new Heart(level,x,y,z,sprites,.15F,14));
        event.registerSpriteSet(CupidParticles.DRAW_HEART.get(), sprites ->
                (type, level, x,y,z, dx,dy,dz) -> new Heart(level,x,y,z,sprites,.13F,12));
        event.registerSpriteSet(CupidParticles.IMPACT_OUTLINE.get(), sprites ->
                (type, level, x,y,z, quality,dy,dz) -> new Outline(level,x,y,z,sprites,(int)Math.round(quality)));
    }

    private static class Heart extends TextureSheetParticle {
        Heart(ClientLevel level, double x, double y, double z, SpriteSet sprites, float size, int duration) {
            super(level,x,y,z);
            pickSprite(sprites);
            yd = .012;
            friction = .92F;
            lifetime = duration;
            quadSize = size;
            alpha = .82F;
            hasPhysics = false;
        }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        @Override public int getLightColor(float partial) { return 0xf000f0; }
        @Override public void tick() {
            super.tick();
            alpha = .82F * Mth.clamp((lifetime-age)/7F, 0F, 1F);
        }
        @Override public void render(VertexConsumer out, Camera camera, float partial) {
            if (camera.getPosition().distanceToSqr(x,y,z) > .12)
                super.render(out,camera,partial);
        }
    }

    /** One particle engine entry draws a heart perimeter facing this viewer's camera. */
    private static final class Outline extends Heart {
        private static final float[][] SHAPE = {
                {0,.23F},{-.17F,.43F},{-.37F,.52F},{-.55F,.47F},{-.67F,.30F},{-.67F,.12F},
                {-.60F,-.04F},{-.40F,-.26F},{0,-.61F},{.40F,-.26F},{.60F,-.04F},{.67F,.12F},
                {.67F,.30F},{.55F,.47F},{.37F,.52F},{.17F,.43F},{0,.23F}};
        private final int stride;

        Outline(ClientLevel level,double x,double y,double z,SpriteSet sprites,int quality) {
            super(level,x,y,z,sprites,quality<=0 ? .065F : .072F,quality<=0 ? 10 : 14);
            stride = quality<=0 ? 2 : 1;
            midpoint = quality >= 2;
        }
        private final boolean midpoint;
        @Override public AABB getRenderBoundingBox(float partial) {
            return super.getRenderBoundingBox(partial).inflate(.8);
        }
        @Override public void render(VertexConsumer out, Camera camera, float partial) {
            Vec3 eye = camera.getPosition();
            if (eye.distanceToSqr(x,y,z) < .64) return;
            Quaternionf face = camera.rotation();
            float cx=(float)(Mth.lerp(partial,xo,x)-eye.x);
            float cy=(float)(Mth.lerp(partial,yo,y)-eye.y);
            float cz=(float)(Mth.lerp(partial,zo,z)-eye.z);
            var point = new Vector3f();
            for (int i=0; i<SHAPE.length-1; i+=stride) {
                float[] anchor=SHAPE[i];
                point.set(anchor[0],anchor[1],0).rotate(face);
                renderRotatedQuad(out,face,cx+point.x,cy+point.y,cz+point.z,partial);
                if (midpoint) {
                    float[] next=SHAPE[i+1];
                    point.set((anchor[0]+next[0])/2,(anchor[1]+next[1])/2,0).rotate(face);
                    renderRotatedQuad(out,face,cx+point.x,cy+point.y,cz+point.z,partial);
                }
            }
        }
    }
}
