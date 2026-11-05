package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KeeperRenderer extends MobRenderer<Keeper, KeeperRenderer.DressModel> {
    private static final ModelLayerLocation LAYER = new ModelLayerLocation(id("keeper"), "dress");
    // From the soles (y=24) to the crown of the hair (y=-5.5), in model pixels.
    private static final float ADULT_MODEL_SCALE = 16F * 1.78F / 29.5F;

    public KeeperRenderer(EntityRendererProvider.Context context) {
        super(context, new DressModel(context.bakeLayer(LAYER)), 0.36F);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("uwudimension", path); }
    @Override public ResourceLocation getTextureLocation(Keeper keeper) { return KeeperOutfit.forKeeper(keeper).texture(keeper.dusk()); }

    public static float modelScale(Keeper keeper) { return ADULT_MODEL_SCALE * keeper.getAgeScale(); }

    @Override protected void scale(Keeper keeper, PoseStack pose, float partialTick) {
        float scale = modelScale(keeper);
        pose.scale(scale, scale, scale);
    }

    @Override protected float getShadowRadius(Keeper keeper) {
        return super.getShadowRadius(keeper) * keeper.getAgeScale();
    }

    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, DressModel::layer);
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(People.PETAL_KEEPER.get(), KeeperRenderer::new);
        event.registerEntityRenderer(People.DUSK_KEEPER.get(), KeeperRenderer::new);
    }

    public static final class DressModel extends HierarchicalModel<Keeper> {
        private final ModelPart root, head, rightArm, leftArm, rightLeg, leftLeg;
        private final EnumMap<KeeperOutfit, List<ModelPart>> accessories = new EnumMap<>(KeeperOutfit.class);
        DressModel(ModelPart root) {
            this.root = root;
            head = root.getChild("head");
            rightArm = root.getChild("right_arm");
            leftArm = root.getChild("left_arm");
            rightLeg = root.getChild("right_leg");
            leftLeg = root.getChild("left_leg");
            for (KeeperOutfit outfit : KeeperOutfit.values()) {
                List<ModelPart> pieces = new ArrayList<>();
                for (ModelPart parent : List.of(root, head, leftArm, rightArm)) {
                    if (parent.hasChild(outfit.key)) pieces.add(parent.getChild(outfit.key));
                }
                accessories.put(outfit, pieces);
            }
        }

        @Override public ModelPart root() { return root; }

        public static LayerDefinition layer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition body = mesh.getRoot();
            PartDefinition face = body.addOrReplaceChild("head", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8)
                    .texOffs(34, 0).addBox(-4.5F, -8.5F, -4.5F, 9, 3, 9)
                    .texOffs(72, 0).addBox(-4, -6, 3, 8, 10, 2)
                    .texOffs(94, 0).addBox(-4.5F, -6, -4.15F, 2, 8, 3)
                    .texOffs(94, 0).addBox(2.5F, -6, -4.15F, 2, 8, 3),
                    PartPose.offset(0, 3, 0));
            for (int s : new int[]{-1, 1}) {
                face.addOrReplaceChild("rosette_" + s, CubeListBuilder.create().texOffs(108, 0)
                        .addBox(-2, -2, -1, 4, 4, 2), PartPose.offsetAndRotation(s * 4.5F, -6F, -1.5F, 0, s * 0.25F, s * 0.55F));
            }
            body.addOrReplaceChild("bodice", CubeListBuilder.create()
                    .texOffs(0, 20).addBox(-3.5F, 3, -2, 7, 9, 4)
                    .texOffs(24, 20).addBox(-4, 10, -2.5F, 8, 3, 5)
                    .texOffs(52, 20).addBox(-4.5F, 12, -3, 9, 3, 6)
                    .texOffs(84, 20).addBox(-5, 14, -3.5F, 10, 3, 7)
                    .texOffs(0, 39).addBox(-4.5F, 16, -3, 9, 2, 6), PartPose.ZERO);
            for (int s : new int[]{-1, 1}) {
                body.addOrReplaceChild(s < 0 ? "right_arm" : "left_arm", CubeListBuilder.create()
                        .texOffs(33, 38).addBox(-1.5F, 0, -1.5F, 3, 9, 3)
                        .texOffs(50, 38).addBox(-2, -0.5F, -2, 4, 3, 4),
                        PartPose.offset(s * 5.1F, 4, 0));
                body.addOrReplaceChild(s < 0 ? "right_leg" : "left_leg", CubeListBuilder.create()
                        .texOffs(69, 38).addBox(-1.5F, 0, -1.5F, 3, 8, 3)
                        .texOffs(85, 38).addBox(-2F, 6, -2.5F, 4, 3, 5),
                        PartPose.offset(s * 2.0F, 15, 0));
                body.addOrReplaceChild("ribbon_" + s, CubeListBuilder.create().texOffs(108, 38)
                        .addBox(-1, 0, 0, 2, 6, 1), PartPose.offsetAndRotation(s * 1.5F, 11, 3.35F, 0.2F, 0, s * -0.25F));
            }
            KeeperOutfit.addAccessories(body, face, body.getChild("left_arm"), body.getChild("right_arm"));
            return LayerDefinition.create(mesh, 128, 128);
        }

        @Override public void setupAnim(Keeper entity, float walk, float speed, float age, float yaw, float pitch) {
            KeeperOutfit outfit = KeeperOutfit.forKeeper(entity);
            accessories.forEach((role, parts) -> parts.forEach(part -> part.visible = role == outfit));
            float stride = Mth.cos(walk * 0.6662F) * speed;
            head.yRot = yaw * Mth.DEG_TO_RAD;
            head.xRot = pitch * Mth.DEG_TO_RAD;
            rightLeg.xRot = stride;
            leftLeg.xRot = -stride;
            rightArm.xRot = -stride * 0.75F - Mth.sin(attackTime * Mth.PI) * 1.45F;
            leftArm.xRot = stride * 0.75F;
            rightArm.zRot = 0.045F + Mth.cos(age * 0.065F) * 0.035F;
            leftArm.zRot = -rightArm.zRot;
        }
    }
}
