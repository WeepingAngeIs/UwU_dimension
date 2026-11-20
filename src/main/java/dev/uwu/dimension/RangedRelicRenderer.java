package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
    private static final ResourceLocation HARE_ORB=Arsenal.id("textures/particle/neon_heart.png");
    static int color(int element){return switch(element){case 1->0xff9deeff;case 2->0xff9961d4;case 3->0xffffd175;default->0xffff86c2;};}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(RangedRelics.LASH.get(),LashRenderer::new);
        event.registerEntityRenderer(RangedRelics.FLIGHT.get(),FlightRenderer::new);
        event.registerEntityRenderer(RangedRelics.PROJECTILE.get(),net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
    }
    static void vertex(VertexConsumer b,PoseStack.Pose p,Vec3 v,float u,float texV,int color,int light){
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
        private final java.util.Map<RelicFlight,FlightFrame> frames = new java.util.WeakHashMap<>();
        FlightRenderer(EntityRendererProvider.Context context){super(context);itemRenderer=context.getItemRenderer();}
        @Override public ResourceLocation getTextureLocation(RelicFlight e){return SLASH;}
        @Override public void render(RelicFlight e,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
            poses.pushPose();
            if(e.wave()){
                wave(e, poses, buffers, frame(e));
            }else if(e.hareOrb()){
                // The scepter itself returns to the player, but its travelling cast is a
                // bright pastel heart instead of a second copy of the item.
                poses.mulPose(entityRenderDispatcher.cameraOrientation());
                var glow=buffers.getBuffer(RenderType.entityTranslucent(HARE_ORB));
                heart(glow,poses,.56F,0x78ff83c7);
                heart(glow,poses,.34F,0xffffffff);
            }else{
                // Trident-style alignment: the long axis points along the projectile's
                // velocity, leaving the 2-D pixel weapon edge-on during forward flight.
                float flightYaw=e.flightYaw(partial);
                float flightPitch=e.flightPitch(partial);
                poses.mulPose(Axis.YP.rotationDegrees(flightYaw-90.0F));
                poses.mulPose(Axis.ZP.rotationDegrees(flightPitch-45.0F));
                // Axes and ring blades tumble in their vertical flight plane like a wheel.
                // Spears, tridents and daggers retain point-first alignment; embeds stay still.
                if (chakram(e)) {
                    // The aperture stays readable while the ring rolls around its own normal.
                    // It remains at the tracked world position through outbound, embed and return.
                    poses.mulPose(Axis.YP.rotationDegrees(14F));
                    if (!e.landed()) poses.mulPose(Axis.ZP.rotationDegrees((e.tickCount+partial)*36F));
                } else if(!e.landed() && tumbles(e)) {
                    poses.mulPose(Axis.ZP.rotationDegrees((e.tickCount+partial)*36F));
                }
                poses.scale(1.8F,1.8F,1.8F);
                itemRenderer.renderStatic(e.getItem(),ItemDisplayContext.NONE,15728880,OverlayTexture.NO_OVERLAY,poses,buffers,e.level(),e.getId());
            }
            poses.popPose();super.render(e,yaw,partial,poses,buffers,light);
        }
        private record FlightFrame(Vec3 forward,Vec3 side,Vec3 up) {}

        private FlightFrame frame(RelicFlight flight) {
            FlightFrame previous=frames.get(flight);
            Vec3 motion=flight.getDeltaMovement();
            if (motion.lengthSqr()<1.0E-8) {
                if(previous!=null)return previous;
                motion=Vec3.directionFromRotation(flight.getXRot(),flight.getYRot());
            }
            Vec3 forward=motion.normalize();
            Vec3 side=previous==null?forward.cross(new Vec3(0,1,0))
                    :previous.side().subtract(forward.scale(previous.side().dot(forward)));
            if(side.lengthSqr()<1.0E-6) {
                Vec3 axis=Math.abs(forward.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0);
                side=forward.cross(axis);
            }
            side=side.normalize();
            if(previous!=null&&side.dot(previous.side())<0)side=side.scale(-1);
            FlightFrame next=new FlightFrame(forward,side,side.cross(forward).normalize());
            frames.put(flight,next);
            return next;
        }

        private static void wave(RelicFlight flight,PoseStack poses,MultiBufferSource buffers,FlightFrame frame) {
            VertexConsumer beam=buffers.getBuffer(RenderType.entityTranslucent(Arsenal.id("textures/entity/petal_crest.png")));
            // A double-sided narrow crest encloses the actual .4 block collision width.
            // Two crossing strips retain the nose and trailing points in an edge-on view.
            crest(beam,poses,frame.forward(),frame.side(),color(flight.element()));
            crest(beam,poses,frame.forward(),frame.up(),(color(flight.element())&0xffffff)|0xb0000000);
        }

        private static void crest(VertexConsumer consumer,PoseStack poses,Vec3 forward,Vec3 across,int color) {
            Vec3 nose=forward.scale(.08),tail=forward.scale(-.3);
            Vec3 left=across.scale(-.2),right=across.scale(.2);
            Vec3 a=tail.add(left),b=tail.add(right),c=nose.add(right),d=nose.add(left);
            vertex(consumer,poses.last(),a,0,1,color,15728880);
            vertex(consumer,poses.last(),b,1,1,color,15728880);
            vertex(consumer,poses.last(),c,1,0,color,15728880);
            vertex(consumer,poses.last(),d,0,0,color,15728880);
            vertex(consumer,poses.last(),d,0,0,color,15728880);
            vertex(consumer,poses.last(),c,1,0,color,15728880);
            vertex(consumer,poses.last(),b,1,1,color,15728880);
            vertex(consumer,poses.last(),a,0,1,color,15728880);
        }

        private static boolean chakram(RelicFlight flight) {
            var entry=CounterArsenal.entry(flight.counter());
            return entry!=null&&entry.shape().equals("chakram");
        }
        private static boolean tumbles(RelicFlight e) {
            var counter=CounterArsenal.entry(e.counter());
            if(counter!=null)return java.util.Set.of("bow","chakram","axe","scythe").contains(counter.shape());
            return switch(e.relic()) {
                case BLOSSOM_GREATSWORD,BELLHEART_AXE,LOCKKEEPER_AXE,VESPER_SCYTHE -> true;
                default -> false;
            };
        }
        private static void heart(VertexConsumer consumer,PoseStack poses,float size,int tint){
            float half=size*.5F;
            vertex(consumer,poses.last(),new Vec3(-half,-half,0),0,1,tint,15728880);
            vertex(consumer,poses.last(),new Vec3(half,-half,0),1,1,tint,15728880);
            vertex(consumer,poses.last(),new Vec3(half,half,0),1,0,tint,15728880);
            vertex(consumer,poses.last(),new Vec3(-half,half,0),0,0,tint,15728880);
        }
    }
}
