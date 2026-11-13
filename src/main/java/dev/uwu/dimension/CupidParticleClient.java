package dev.uwu.dimension;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
@EventBusSubscriber(modid="uwudimension",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CupidParticleClient {
 @SubscribeEvent public static void register(RegisterParticleProvidersEvent event){event.registerSpriteSet(CupidParticles.PASTEL_HEART.get(),sprites->(type,level,x,y,z,dx,dy,dz)->new Heart(level,x,y,z,dx,dy,dz,sprites));}
 private static final class Heart extends TextureSheetParticle {
  Heart(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,SpriteSet sprites){
   super(level,x,y,z,dx,dy,dz);pickSprite(sprites);xd=dx*.15;yd=.035+Math.abs(dy)*.12;zd=dz*.15;
   lifetime=38+random.nextInt(12);quadSize=.43F;gravity=0;friction=.92F;hasPhysics=false;
  }
  @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
  @Override public int getLightColor(float partial){return 0xf000f0;}
  @Override public void tick(){super.tick();alpha=Math.min(1F,(lifetime-age)/8F);}
 }
}
