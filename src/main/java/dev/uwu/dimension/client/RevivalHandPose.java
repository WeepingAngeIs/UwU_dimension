package dev.uwu.dimension.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.uwu.dimension.DawnpetalMotion;
import dev.uwu.dimension.DawnpetalSwing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.joml.Quaternionf;

public final class RevivalHandPose {
    // Set only around the normal first-person draw, on the render thread.
    private static ItemStack idleAxe;

    private RevivalHandPose() {}

    public static boolean atRest(LivingEntity owner, InteractionHand hand, ItemStack rendered,
            float partial, float swing, float equip) {
        Minecraft client = Minecraft.getInstance();
        if (!(owner instanceof Player player) || player != client.player || player != client.getCameraEntity()
                || !client.options.getCameraType().isFirstPerson() || player.isSpectator()
                || player.isUsingItem() || player.isAutoSpinAttack() || player.swinging
                || player.getAttackAnim(partial) != 0 || swing != 0 || equip > .0001F
                || !ItemStack.isSameItemSameComponents(rendered, player.getItemInHand(hand))) return false;
        if (ModList.get().isLoaded("bettercombat") && BetterCombatAttack.active(client)) return false;
        return !DawnpetalSwing.hasLocalCue(player, partial) && DawnpetalSpinClient.renderFrame(player, partial) == null;
    }

    private static final class BetterCombatAttack {
        static boolean active(Minecraft client) {
            return ((net.bettercombat.api.MinecraftClient_BetterCombat)(Object)client).getCurrentAttackHand() != null;
        }
    }

    public static void drawAxe(ItemStack stack, Runnable draw) {
        ItemStack previous = idleAxe;
        idleAxe = stack;
        try {
            draw.run();
        } finally {
            idleAxe = previous;
        }
    }

    public static boolean drawsAxe(ItemStack stack) {
        return idleAxe == stack;
    }

    public static BakedModel restingAxe(BakedModel model) {
        return new BakedModelWrapper<BakedModel>(model) {
            @Override
            public BakedModel applyTransform(ItemDisplayContext view, PoseStack pose, boolean left) {
                pose.translate(0, -.6 / 16, -1.9 / 16);
                BakedModel selected = super.applyTransform(view, pose, left);
                double x = DawnpetalMotion.GRIP_X / 16 - .5;
                double y = DawnpetalMotion.GRIP_Y / 16 - .5;
                double z = DawnpetalMotion.GRIP_Z / 16 - .5;
                pose.translate(x, y, z);
                pose.mulPose(Axis.ZP.rotationDegrees(34F));
                // Turn the crescent around the painted shaft, keeping the wrap in place.
                float angle = (float) Math.toRadians(left ? 145 : -145);
                pose.mulPose(new Quaternionf().rotationAxis(angle, -.6F, .8F, 0F));
                pose.translate(-x, -y, -z);
                return selected;
            }
        };
    }
}
