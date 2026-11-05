package dev.uwu.dimension;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="uwudimension",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class VelvetVesperRenderer extends MobRenderer<VelvetVesper,VelvetVesperRenderer.SilkModel> {
    private static final ModelLayerLocation LAYER=new ModelLayerLocation(UwUDimension.id("velvet_vesper"),"silk");
    private static final ResourceLocation SKIN=UwUDimension.id("textures/entity/velvet_vesper.png");
    public VelvetVesperRenderer(EntityRendererProvider.Context ctx) {
        super(ctx,new SilkModel(ctx.bakeLayer(LAYER)),.72F);
    }
    @Override public ResourceLocation getTextureLocation(VelvetVesper moth){return SKIN;}
    @Override protected int getBlockLightLevel(VelvetVesper moth,BlockPos pos){return Math.max(11,super.getBlockLightLevel(moth,pos));}
    @Override protected void scale(VelvetVesper moth,com.mojang.blaze3d.vertex.PoseStack pose,float partial){pose.scale(1.34F,1.34F,1.34F);}
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e){e.registerLayerDefinition(LAYER,SilkModel::layer);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(MirrorCourt.VESPER.get(),VelvetVesperRenderer::new);}

    public static final class SilkModel extends HierarchicalModel<VelvetVesper> {
        private final ModelPart root, body, face, westWing, eastWing;
        SilkModel(ModelPart root) {
            this.root=root; body=root.getChild("body");face=body.getChild("face");
            westWing=body.getChild("wing_-1");eastWing=body.getChild("wing_1");
        }
        @Override public ModelPart root(){return root;}
        private static LayerDefinition layer() {
            var mesh=new MeshDefinition();
            var body=mesh.getRoot().addOrReplaceChild("body",CubeListBuilder.create()
                    .texOffs(32,0).addBox(-4,-3,-3,8,8,6)
                    .texOffs(62,0).addBox(-6,-4,-4,12,3,8)
                    .texOffs(0,17).addBox(-3.5F,5,-2.5F,7,9,5)
                    .texOffs(30,17).addBox(-2.5F,14,-2,5,5,4)
                    .texOffs(82,40).addBox(-2,2,-4,4,4,2),PartPose.offset(0,5,0));
            var face=body.addOrReplaceChild("face",CubeListBuilder.create().texOffs(0,0).addBox(-4,-7,-4,8,7,7),PartPose.offset(0,-3,-.5F));
            for(int side:new int[]{-1,1}) {
                face.addOrReplaceChild("feeler_"+side,CubeListBuilder.create()
                        .texOffs(88,16).addBox(-.5F,-9,-.5F,1,9,1)
                        .texOffs(72,17).addBox(-1.5F,-10.5F,-1.5F,3,3,3),
                        PartPose.offsetAndRotation(2.5F*side,-6,0,-.2F,0,side*.43F));
                var wing=body.addOrReplaceChild("wing_"+side,CubeListBuilder.create().texOffs(0,38).mirror(side<0)
                        .addBox(side<0?-17:0,-16,0,17,20,1),PartPose.offset(side*3,-1,2));
                wing.addOrReplaceChild("swallowtail",CubeListBuilder.create().texOffs(43,38).mirror(side<0)
                        .addBox(side<0?-13:0,-1,0,13,14,1),PartPose.offsetAndRotation(0,3,1,0,0,side*.14F));
                for(int row=0;row<3;row++) body.addOrReplaceChild("foot_"+side+"_"+row,
                        CubeListBuilder.create().texOffs(52,17).addBox(-1,0,-1,2,9,2),
                        PartPose.offsetAndRotation(side*3,1+row*2,-1+row,.62F-row*.24F,0,-side*(.62F-row*.12F)));
            }
            return LayerDefinition.create(mesh,128,64);
        }
        @Override public void setupAnim(VelvetVesper moth,float stride,float speed,float age,float yaw,float pitch) {
            body.y=5+Mth.sin(age*.09F)*.62F;
            face.yRot=yaw*Mth.DEG_TO_RAD*.58F;face.xRot=pitch*Mth.DEG_TO_RAD*.52F;
            float wing=moth.guarding()?1.05F+Mth.sin(age*.16F)*.04F:moth.folding()?.52F+Mth.sin(age*.28F)*.22F:
                    -.16F+Mth.sin(age*(moth.casting()?.32F:.17F))*.20F;
            eastWing.yRot=wing;westWing.yRot=-wing;
            float droop=moth.guarding()?.28F:0;
            eastWing.zRot=droop;westWing.zRot=-droop;
        }
    }
}
