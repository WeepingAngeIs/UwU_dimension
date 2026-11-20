package dev.uwu.dimension.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.uwu.dimension.DawnpetalMotion;
import dev.uwu.dimension.DawnpetalSwing;
import java.lang.ref.WeakReference;
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
    private static AnimatedHands animatedHands;

    private record AnimatedHands(WeakReference<Player> player, int tick, int slot,
            ItemStack main, ItemStack off) {
        boolean recent(Player owner) {
            return player.get() == owner && owner.tickCount >= tick && owner.tickCount - tick <= 20;
        }
    }

    private RevivalHandPose() {}

    public static void observeAnimatedHands(Player player, ItemDisplayContext view, float partial) {
        Minecraft client = Minecraft.getInstance();
        if (view.firstPerson() || !localFirstPerson(client, player)
                || !DawnpetalSpinClient.animatorFirstPersonPass(player)
                || DawnpetalSpinClient.renderFrame(player, partial) != null
                || !ModList.get().isLoaded("bettercombat")) return;
        // Player Animator can finish after Better Combat's attack clock. Remember the actual
        // first-person attack pass until the ordinary hand renderer takes over.
        boolean continuing = animatedHands != null && animatedHands.recent(player)
                && player.tickCount - animatedHands.tick() <= 1;
        if (continuing && player.tickCount == animatedHands.tick()
                && player.getInventory().selected == animatedHands.slot()
                && player.getMainHandItem() == animatedHands.main()
                && player.getOffhandItem() == animatedHands.off()) return;
        if (BetterCombatAttack.active(client) || continuing) {
            animatedHands = new AnimatedHands(new WeakReference<>(player), player.tickCount,
                    player.getInventory().selected, player.getMainHandItem(), player.getOffhandItem());
        }
    }

    public static boolean animatorRecovery(LivingEntity owner, InteractionHand hand, ItemStack rendered) {
        if (!(owner instanceof Player player) || animatedHands == null || !animatedHands.recent(player)
                || animatedHands.slot() != player.getInventory().selected) return false;
        ItemStack animated = hand == InteractionHand.MAIN_HAND ? animatedHands.main() : animatedHands.off();
        return ItemStack.isSameItemSameComponents(animated, rendered);
    }

    private static boolean localFirstPerson(Minecraft client, Player player) {
        return player == client.player && player == client.getCameraEntity()
                && client.options.getCameraType().isFirstPerson() && !player.isSpectator();
    }

    public static boolean usesRestingGrip(LivingEntity owner, InteractionHand hand, ItemStack rendered,
            float partial, float swing) {
        Minecraft client = Minecraft.getInstance();
        if (!(owner instanceof Player player) || !localFirstPerson(client, player)
                || player.isUsingItem() || player.isAutoSpinAttack() || player.swinging
                || player.getAttackAnim(partial) != 0 || swing != 0
                || !ItemStack.isSameItemSameComponents(rendered, player.getItemInHand(hand))) return false;
        if (DawnpetalSpinClient.renderFrame(player, partial) != null) return false;
        if (animatorRecovery(player, hand, rendered)) {
            return !BetterCombatAttack.windingUp(client);
        }
        if (ModList.get().isLoaded("bettercombat") && BetterCombatAttack.active(client)) return false;
        return !DawnpetalSwing.hasLocalCue(player, partial);
    }

    private static final class BetterCombatAttack {
        static boolean active(Minecraft client) {
            return ((net.bettercombat.api.MinecraftClient_BetterCombat)(Object)client).getCurrentAttackHand() != null;
        }

        static boolean windingUp(Minecraft client) {
            return ((net.bettercombat.api.MinecraftClient_BetterCombat)(Object)client).getUpswingTicks() > 0;
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
