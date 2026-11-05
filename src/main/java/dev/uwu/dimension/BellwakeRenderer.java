package dev.uwu.dimension;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="uwudimension",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BellwakeRenderer extends MobRenderer<Bellwake,BellwakeRenderer.Model> {
    private static final ModelLayerLocation LAYER=new ModelLayerLocation(id("bellwake"),"body");
    private static final ResourceLocation SKIN=id("textures/entity/bellwake.png");
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("uwudimension",name);}
    public BellwakeRenderer(EntityRendererProvider.Context ctx){super(ctx,new Model(ctx.bakeLayer(LAYER)),.27F);}
    @Override public ResourceLocation getTextureLocation(Bellwake entity){return SKIN;}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(Bellwake.TYPE.get(),BellwakeRenderer::new);}
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){event.registerLayerDefinition(LAYER,Model::layer);}
    public static final class Model extends HierarchicalModel<Bellwake> {
        final ModelPart root,tail;
        Model(ModelPart root){this.root=root;tail=root.getChild("tail");}
        @Override public ModelPart root(){return root;}
        static LayerDefinition layer(){
            MeshDefinition mesh=new MeshDefinition();PartDefinition body=mesh.getRoot();
            body.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,18,-7,8,4,5)
                    .texOffs(42,0).addBox(-1,16,-5,2,2,2),PartPose.ZERO);
            body.addOrReplaceChild("back",CubeListBuilder.create().texOffs(0,11).addBox(-3,19,-2,6,4,8),PartPose.ZERO);
            for(int side:new int[]{-1,1}){
                body.addOrReplaceChild("crest"+side,CubeListBuilder.create().texOffs(30,9).addBox(-1,-3,-1,2,5,2)
                    .texOffs(40,9).addBox(-1.5F,-3.5F,-1.5F,3,2,3),
                    PartPose.offsetAndRotation(side*4.1F,19,-4,0,0,side*.68F));
                for(int z:new int[]{0,4})body.addOrReplaceChild("foot"+side+"_"+z,
                        CubeListBuilder.create().texOffs(30,19).addBox(-1,0,-1.5F,2,1,3),PartPose.offset(side*3,23,z));
            }
            body.addOrReplaceChild("tail",CubeListBuilder.create().texOffs(0,24).addBox(-1.5F,-1,0,3,2,5)
                    .texOffs(21,24).addBox(-.5F,-2.5F,2,1,4,5),PartPose.offset(0,20.5F,6));
            return LayerDefinition.create(mesh,64,40);
        }
        @Override public void setupAnim(Bellwake entity,float walk,float speed,float age,float yaw,float pitch){
            tail.yRot=Mth.sin(age*.18F)*(.21F+Math.min(.4F,speed));
            root.xRot=entity.isInWater()?Mth.sin(age*.09F)*.055F:0;
        }
    }
}
