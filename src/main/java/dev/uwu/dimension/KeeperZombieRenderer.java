package dev.uwu.dimension;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.model.ZombieVillagerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="uwudimension",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class KeeperZombieRenderer extends HumanoidMobRenderer<KeeperZombieVillager,KeeperZombieRenderer.Model> {
    private static final ModelLayerLocation LAYER=new ModelLayerLocation(Arsenal.id("zombie_keeper"),"dress");

    public KeeperZombieRenderer(EntityRendererProvider.Context context) {
        super(context,new Model(context.bakeLayer(LAYER)),.38F);
        addLayer(new HumanoidArmorLayer<>(this,
                new ZombieVillagerModel<>(context.bakeLayer(ModelLayers.ZOMBIE_VILLAGER_INNER_ARMOR)),
                new ZombieVillagerModel<>(context.bakeLayer(ModelLayers.ZOMBIE_VILLAGER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override public ResourceLocation getTextureLocation(KeeperZombieVillager zombie) {
        var outfit=KeeperOutfit.forProfession(zombie.isBaby(), zombie.getVillagerData().getProfession());
        return outfit.texture(zombie.getType()==People.DUSK_ZOMBIE.get()).withPath(path->path.replace("textures/entity/","textures/entity/undead/"));
    }

    @Override protected boolean isShaking(KeeperZombieVillager zombie) {
        return super.isShaking(zombie) || zombie.isConverting();
    }

    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER,()->{
            var mesh=KeeperRenderer.DressModel.mesh();
            var hat=mesh.getRoot().addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
            hat.addOrReplaceChild("hat_rim",CubeListBuilder.create(),PartPose.ZERO);
            return LayerDefinition.create(mesh,128,128);
        });
    }

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(People.PETAL_ZOMBIE.get(),KeeperZombieRenderer::new);
        event.registerEntityRenderer(People.DUSK_ZOMBIE.get(),KeeperZombieRenderer::new);
    }

    static final class Model extends ZombieVillagerModel<KeeperZombieVillager> {
        private final EnumMap<KeeperOutfit,List<ModelPart>> outfits=new EnumMap<>(KeeperOutfit.class);
        private final List<ModelPart> extra=new ArrayList<>();

        Model(ModelPart root) {
            super(root);
            KeeperRenderer.DressModel.shapeFace(head);
            extra.add(root.getChild("ribbon_-1"));
            extra.add(root.getChild("ribbon_1"));
            for(var outfit:KeeperOutfit.values()) {
                var pieces=new ArrayList<ModelPart>();
                for(var parent:List.of(root,head,leftArm,rightArm)) {
                    if(parent.hasChild(outfit.key)) {
                        var part=parent.getChild(outfit.key);
                        pieces.add(part);
                        if(parent==root) extra.add(part);
                    }
                }
                outfits.put(outfit,pieces);
            }
        }

        @Override protected Iterable<ModelPart> bodyParts() {
            return Iterables.concat(super.bodyParts(),extra);
        }

        @Override public void setupAnim(KeeperZombieVillager entity,float walk,float speed,float age,float yaw,float pitch) {
            super.setupAnim(entity,walk,speed,age,yaw,pitch);
            var selected=KeeperOutfit.forProfession(entity.isBaby(),entity.getVillagerData().getProfession());
            outfits.forEach((role,parts)->parts.forEach(part->part.visible=role==selected));
        }
    }
}
