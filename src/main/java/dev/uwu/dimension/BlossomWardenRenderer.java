package dev.uwu.dimension;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="uwudimension",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BlossomWardenRenderer extends MobRenderer<BlossomWarden,BlossomWardenRenderer.Model> {
    private static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("uwudimension","blossom_warden"),"petals");
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("uwudimension","textures/entity/blossom_warden.png");
    private static final ResourceLocation PAD=ResourceLocation.fromNamespaceAndPath("uwudimension","textures/block/drift_blossom_pad.png");
    public BlossomWardenRenderer(EntityRendererProvider.Context context) {
        super(context,new Model(context.bakeLayer(LAYER)),.78F);
        addLayer(new LilyPadCrownLayer(this));
    }
    @Override public ResourceLocation getTextureLocation(BlossomWarden entity) { return TEXTURE; }
    @SubscribeEvent public static void model(EntityRenderersEvent.RegisterLayerDefinitions e) { e.registerLayerDefinition(LAYER,Model::layer); }
    @SubscribeEvent public static void renderer(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(People.BLOSSOM_WARDEN.get(),BlossomWardenRenderer::new);
    }

    private static final class LilyPadCrownLayer extends RenderLayer<BlossomWarden,Model> {
        LilyPadCrownLayer(BlossomWardenRenderer renderer){super(renderer);}
        @Override public void render(PoseStack poses,MultiBufferSource buffers,int light,BlossomWarden entity,
                float limbSwing,float limbSwingAmount,float partialTick,float age,float netHeadYaw,float headPitch){
            poses.pushPose();
            getParentModel().root().getChild("head").translateAndRotate(poses);
            VertexConsumer consumer=buffers.getBuffer(RenderType.entityCutoutNoCull(PAD));
            for(int i=0;i<10;i++){
                float angle=(float)(Math.PI*2*i/10.0);
                poses.pushPose();
                poses.translate(Math.cos(angle)*.48,-.48,Math.sin(angle)*.48);
                poses.mulPose(Axis.YP.rotationDegrees(-angle*Mth.RAD_TO_DEG));
                poses.mulPose(Axis.XP.rotationDegrees(-42));
                float half=.245F;
                RangedRelicRenderer.vertex(consumer,poses.last(),new Vec3(-half,-half,0),0,1,0xffffffff,light);
                RangedRelicRenderer.vertex(consumer,poses.last(),new Vec3(half,-half,0),1,1,0xffffffff,light);
                RangedRelicRenderer.vertex(consumer,poses.last(),new Vec3(half,half,0),1,0,0xffffffff,light);
                RangedRelicRenderer.vertex(consumer,poses.last(),new Vec3(-half,half,0),0,0,0xffffffff,light);
                poses.popPose();
            }
            poses.popPose();
        }
    }

    static final class Model extends HierarchicalModel<BlossomWarden> {
        private final ModelPart root,head,left,right,footL,footR;
        Model(ModelPart root) {
            this.root=root;head=root.getChild("head");left=root.getChild("left");right=root.getChild("right");
            footL=root.getChild("left_root");footR=root.getChild("right_root");
        }
        @Override public ModelPart root(){return root;}
        static LayerDefinition layer() {
            var mesh=new MeshDefinition();
            var body=mesh.getRoot();
            body.addOrReplaceChild("basket",CubeListBuilder.create().texOffs(0,36).addBox(-8,-5,-5,16,16,10)
                    .texOffs(57,38).addBox(-9,-4,-6,18,5,12)
                    .texOffs(0,70).addBox(-7,10,-5,14,3,10),PartPose.ZERO);
            var head=body.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-8,-10,-6,16,11,12)
                    .texOffs(58,0).addBox(-9,-10.5F,-7,18,4,14),PartPose.offset(0,-4,0));
            flower(body,"brooch",-4,1,-6.6F,-.2F);
            for(int sign:new int[]{-1,1}) {
                var arm=body.addOrReplaceChild(sign<0?"right":"left",CubeListBuilder.create()
                        .texOffs(65,62).addBox(-3.5F,-1,-3.5F,7,17,7)
                        .texOffs(0,89).addBox(-4.5F,12.5F,-4,9,6,8),PartPose.offset(sign*10,-2,0));
                flower(arm,"bloom",sign*2.5F,1,-3.9F,sign*.35F);
                body.addOrReplaceChild(sign<0?"right_root":"left_root",CubeListBuilder.create()
                        .texOffs(40,90).addBox(-3.4F,0,-3.5F,7,9,7)
                        .texOffs(75,89).addBox(-4,7,-5F,8,4,9),PartPose.offset(sign*4.5F,13,0));
            }
            return LayerDefinition.create(mesh,128,128);
        }
        static void flower(PartDefinition parent,String name,float x,float y,float z,float rot) {
            var flower=parent.addOrReplaceChild(name,CubeListBuilder.create().texOffs(102,65).addBox(-.9F,-.9F,-.9F,2,2,2),PartPose.offsetAndRotation(x,y,z,-.12F,0,rot));
            for(int i=0;i<5;i++) flower.addOrReplaceChild("petal"+i,CubeListBuilder.create().texOffs(113,65).addBox(-1,-3,-.5F,2,3,1),
                    PartPose.rotation(0,0,i*Mth.TWO_PI/5));
        }
        @Override public void setupAnim(BlossomWarden w,float walk,float speed,float age,float yaw,float pitch) {
            head.yRot=yaw*Mth.DEG_TO_RAD*.85F;head.xRot=pitch*Mth.DEG_TO_RAD*.55F;
            float step=Mth.cos(walk*.68F)*speed;
            footL.xRot=step;footR.xRot=-step;
            float grab=w.gripping()?-1.25F:w.throwing()?-2.7F:0;
            left.xRot=grab+step*.45F;right.xRot=grab-step*.45F;
            left.zRot=.08F+Mth.sin(age*.06F)*.03F;right.zRot=-left.zRot;
        }
    }
}
