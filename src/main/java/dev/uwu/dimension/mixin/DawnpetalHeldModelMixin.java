package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.uwu.dimension.DawnpetalMotion;
import dev.uwu.dimension.DawnpetalSwing;
import dev.uwu.dimension.RevivalBlossoms;
import dev.uwu.dimension.client.DawnpetalSpinClient;
import dev.uwu.dimension.client.DawnpetalSpinVisual;
import dev.uwu.dimension.client.RevivalHandPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Enlarge only a held primary axe, after resolving its revival flower stage and normal hand transform. */
@Mixin(ItemRenderer.class)
abstract class DawnpetalHeldModelMixin {
    @ModifyExpressionValue(method="renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/ItemRenderer;getModel(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)Lnet/minecraft/client/resources/model/BakedModel;"))
    private BakedModel uwu$dawnpetalBlade(BakedModel model, @Local(argsOnly=true) LivingEntity owner,
            @Local(argsOnly=true) ItemStack stack, @Local(argsOnly=true) ItemDisplayContext context) {
        if (!(owner instanceof Player player)) return model;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        if ((context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                && (stack.is(RevivalBlossoms.AXE.get()) || stack.is(RevivalBlossoms.DIRK.get()))) {
            RevivalHandPose.observeAnimatedHands(player, context, partial);
        }
        if (!stack.is(RevivalBlossoms.AXE.get())) return model;
        if (context.firstPerson() && RevivalHandPose.drawsAxe(stack)) return RevivalHandPose.restingAxe(model);
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) return model;
        HumanoidArm arm = switch (context) {
            case FIRST_PERSON_LEFT_HAND, THIRD_PERSON_LEFT_HAND -> HumanoidArm.LEFT;
            default -> HumanoidArm.RIGHT;
        };
        boolean main = owner.getMainArm() == arm;
        var frame = main && ItemStack.isSameItemSameComponents(stack, player.getMainHandItem())
                ? DawnpetalSpinClient.renderFrame(player, partial) : null;
        var spin = frame != null && frame.arm() == arm ? frame : null;
        boolean firstPerson = context.firstPerson()
                || spin != null && DawnpetalSpinClient.animatorFirstPersonPass(player);
        boolean worldSpin = spin != null && !firstPerson && DawnpetalSpinClient.hasWorldPose(player, spin);
        double growth = spin == null && main ? DawnpetalSwing.expansion(owner, stack, partial) : 0;
        double rest = 1 - growth;
        return new BakedModelWrapper<BakedModel>(model) {
            @Override public BakedModel applyTransform(ItemDisplayContext view, PoseStack pose, boolean left) {
                // AUTO can request a third-person display context for the actual first-person pass.
                ItemDisplayContext display = firstPerson && !view.firstPerson()
                        ? (left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                        : view;
                if (display.firstPerson()) {
                    pose.translate((left ? -.5 : .5) * rest / 16, 6 * rest / 16, -3 * rest / 16);
                }
                BakedModel selected = super.applyTransform(display, pose, left);
                // ItemRenderer centers the baked geometry immediately after this callback.
                double x=DawnpetalMotion.GRIP_X/16-.5, y=DawnpetalMotion.GRIP_Y/16-.5, z=DawnpetalMotion.GRIP_Z/16-.5;
                float plane = worldSpin ? DawnpetalSpinVisual.scaleXY(spin.age())
                        : (float) DawnpetalMotion.planeScale(growth, display.firstPerson());
                float depth = worldSpin ? DawnpetalSpinVisual.scaleZ(spin.age())
                        : (float) DawnpetalMotion.depthScale(growth);
                float roll = worldSpin ? DawnpetalSpinVisual.gripRollDegrees(spin.age()) : (float) (-110 * rest);
                pose.translate(x,y,z);
                // Both mirrored displays share these model-space axes. Stay around the
                // grip, and restore the original cutting-edge-leading arc at full growth.
                pose.mulPose(Axis.ZP.rotationDegrees(roll));
                if (display.firstPerson()) {
                    // Roll around the painted grip-to-collar line (-3, 4, 0).
                    float shaftRoll = (float) Math.toRadians(left ? -125 : 125);
                    pose.mulPose(new Quaternionf().rotationAxis(shaftRoll, -.6F, .8F, 0F));
                }
                pose.scale(plane,plane,depth);
                pose.translate(-x,-y,-z);
                return selected;
            }
        };
    }
}
