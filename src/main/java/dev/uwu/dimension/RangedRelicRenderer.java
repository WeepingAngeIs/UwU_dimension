package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="uwudimension",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class RangedRelicRenderer {
    static final ResourceLocation LASH=Arsenal.id("textures/entity/ribbon_lash.png");
    static final ResourceLocation SLASH=Arsenal.id("textures/entity/element_slash.png");
    static int color(int element){return switch(element){case 1->0xff9deeff;case 2->0xff9961d4;case 3->0xffffd175;default->0xffff86c2;};}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(RangedRelics.LASH.get(),LashRenderer::new);
        event.registerEntityRenderer(RangedRelics.FLIGHT.get(),FlightRenderer::new);
    }
    private static void vertex(VertexConsumer b,PoseStack.Pose p,Vec3 v,float u,float texV,int color,int light){
        b.addVertex(p,(float)v.x,(float)v.y,(float)v.z).setColor(color).setUv(u,texV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,0,1,0);
    }
    static final class LashRenderer extends EntityRenderer<RibbonLash> {
        LashRenderer(EntityRendererProvider.Context context){super(context);}
        @Override public ResourceLocation getTextureLocation(RibbonLash e){return LASH;}
        @Override public void render(RibbonLash e,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
            var owner=e.owner();if(owner==null)return;
            Vec3 hand=owner.getEyePosition(partial).add(owner.getLookAngle().cross(new Vec3(0,1,0)).scale(.25)).add(0,-.28,0).subtract(e.position());
            float age=e.tickCount+partial;
            double progress=age<=5?age/5:Math.max(0,(12-age)/7);
            Vec3 end=e.tip().subtract(e.position());
            Vec3 dir=end.subtract(hand),side=dir.cross(new Vec3(0,1,0)).normalize();
            int col=switch(e.relic()){case FROST_WHIP->color(1);case RIFT_WHIP->color(2);case AXOLOTL_WHIP->0xffffb6c6;default->color(0);};
            var counter=CounterArsenal.entry(e.counter());
            if(counter!=null)col=counter.theme().equals("dusk")?0xffad83dc:0xffff8fc9;
            VertexConsumer b=buffers.getBuffer(RenderType.entityCutoutNoCull(LASH));
            for(int i=0;i<32;i++){
                double t=i/32.0,t2=(i+1)/32.0;
                Vec3 a=point(hand,dir,side,t,progress,age),z=point(hand,dir,side,t2,progress,age);
                Vec3 width=side.scale(.035);
                vertex(b,poses.last(),a.subtract(width),(float)t*8,0,col,light);
                vertex(b,poses.last(),a.add(width),(float)t*8,1,col,light);
                vertex(b,poses.last(),z.add(width),(float)t2*8,1,col,light);
                vertex(b,poses.last(),z.subtract(width),(float)t2*8,0,col,light);
            }
        }
        private Vec3 point(Vec3 hand,Vec3 dir,Vec3 side,double t,double progress,float age){
            return hand.add(dir.scale(t*progress)).add(side.scale(Math.sin(t*Math.PI*3-age*.8)*Math.sin(t*Math.PI)*(.2+(1-progress)*.8))).add(0,-Math.sin(t*Math.PI)*.2,0);
        }
    }
    static final class FlightRenderer extends EntityRenderer<RelicFlight>{
        private final net.minecraft.client.renderer.entity.ItemRenderer itemRenderer;
        FlightRenderer(EntityRendererProvider.Context context){super(context);itemRenderer=context.getItemRenderer();}
        @Override public ResourceLocation getTextureLocation(RelicFlight e){return SLASH;}
        @Override public void render(RelicFlight e,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
            poses.pushPose();
            poses.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));poses.mulPose(Axis.XP.rotationDegrees(e.getXRot()));
            if(e.wave()){
                poses.scale(2.3F,2.3F,2.3F);
                var b=buffers.getBuffer(RenderType.entityTranslucent(SLASH));
                vertex(b,poses.last(),new Vec3(-1,-.5,0),0,1,color(e.element()),15728880);
                vertex(b,poses.last(),new Vec3(1,-.5,0),1,1,color(e.element()),15728880);
                vertex(b,poses.last(),new Vec3(1,.5,0),1,0,color(e.element()),15728880);
                vertex(b,poses.last(),new Vec3(-1,.5,0),0,0,color(e.element()),15728880);
            }else{
                poses.mulPose(Axis.ZP.rotationDegrees((e.tickCount+partial)*42));poses.scale(1.8F,1.8F,1.8F);
                itemRenderer.renderStatic(e.getItem(),ItemDisplayContext.GROUND,light,OverlayTexture.NO_OVERLAY,poses,buffers,e.level(),e.getId());
            }
            poses.popPose();super.render(e,yaw,partial,poses,buffers,light);
        }
    }
}
