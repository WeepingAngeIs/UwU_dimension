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

@EventBusSubscriber(modid="uwudimension", bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class CupidParticleClient {
    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(CupidParticles.PASTEL_HEART.get(), sprites ->
                (type, level, x,y,z, dx,dy,dz) -> new Heart(level,x,y,z,sprites,.15F,18));
        event.registerSpriteSet(CupidParticles.DRAW_HEART.get(), sprites ->
                (type, level, x,y,z, quality,dy,dz) -> new Ripple(level,x,y,z,sprites,(int)Math.round(quality)));
        event.registerSpriteSet(CupidParticles.IMPACT_OUTLINE.get(), sprites ->
                (type, level, x,y,z, quality,dy,dz) -> new Ripple(level,x,y,z,sprites,(int)Math.round(quality)));
    }

    private static class Heart extends TextureSheetParticle {
        Heart(ClientLevel level, double x, double y, double z, SpriteSet sprites, float size, int duration) {
            super(level,x,y,z);
            pickSprite(sprites);
            yd = .012;
            friction = .92F;
            lifetime = duration;
            quadSize = size;
            alpha = .94F;
            hasPhysics = false;
        }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        @Override public int getLightColor(float partial) { return 0xf000f0; }
        @Override public void tick() {
            super.tick();
            alpha = .94F * Mth.clamp((lifetime-age)/7F, 0F, 1F);
        }
        @Override public void render(VertexConsumer out, Camera camera, float partial) {
            if (camera.getPosition().distanceToSqr(x,y,z) > .12)
                super.render(out,camera,partial);
        }
    }

    /** One textured quad with hollow lobes and two transparent sides of the heart contour. */
    private static final class Ripple extends TextureSheetParticle {
        private final float expansion,opacity;
        Ripple(ClientLevel level,double x,double y,double z,SpriteSet sprites,int quality) {
            super(level,x,y,z);
            quality=Mth.clamp(quality,0,2);
            pickSprite(sprites);
            lifetime=quality==0?12:quality==1?16:20;
            expansion=quality==0?.84F:quality==1?1.14F:1.44F;
            opacity=quality==0?.57F:quality==1?.74F:.86F;
            hasPhysics=false;
        }
        @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
        @Override public int getLightColor(float partial){return 0xf000f0;}
        @Override public AABB getRenderBoundingBox(float partial){return super.getRenderBoundingBox(partial).inflate(2.8);}
        @Override public float getQuadSize(float partial){
            float t=Mth.clamp((age+partial)/lifetime,0,1);
            return .22F+expansion*t*(2-t);
        }
        @Override public void render(VertexConsumer out,Camera camera,float partial){
            Vec3 eye=camera.getPosition();
            Vec3 offset=new Vec3(x,y,z).subtract(eye);
            if(offset.lengthSqr()<1.2*1.2){
                // Someone inside the ripple would see its back or clip through the near plane.
                // Render that viewer's copy ahead of their eyes with the crosshair completely open.
                offset=new Vec3(camera.getLookVector()).scale(2.8);
            }
            float t=Mth.clamp((age+partial)/lifetime,0,1);
            alpha=opacity*(1-t*t);
            renderRotatedQuad(out,camera.rotation(),(float)offset.x,(float)offset.y,(float)offset.z,partial);
        }
    }
}
