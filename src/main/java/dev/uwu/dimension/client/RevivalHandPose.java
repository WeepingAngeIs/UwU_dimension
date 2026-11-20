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
    private static double returnStarted = Double.NaN;

    private record AnimatedHands(WeakReference<Player> player, int tick, int slot,
            ItemStack main, ItemStack off) {
        boolean recent(Player owner) {
            return player.get() == owner && owner.tickCount >= tick && owner.tickCount - tick <= 10;
        }

        boolean stillHeld(Player owner) {
            return slot == owner.getInventory().selected
                    && ItemStack.isSameItemSameComponents(main, owner.getMainHandItem())
                    && ItemStack.isSameItemSameComponents(off, owner.getOffhandItem());
        }
    }

    private RevivalHandPose() {}

    public static void observeAnimatedHands(Player player, float partial) {
        Minecraft client = Minecraft.getInstance();
        if (!localFirstPerson(client, player) || player.isUsingItem() || player.isAutoSpinAttack()
                || !DawnpetalSpinClient.animatorFirstPersonPass(player)
                || DawnpetalSpinClient.renderFrame(player, partial) != null
                || !ModList.get().isLoaded("bettercombat")) return;
        // Player Animator can finish after Better Combat's attack clock. Remember the actual
        // first-person attack pass until the ordinary hand renderer takes over.
        boolean continuing = animatedHands != null && animatedHands.recent(player)
                && player.tickCount - animatedHands.tick() <= 1 && animatedHands.stillHeld(player);
        if (continuing && player.tickCount == animatedHands.tick() && Double.isNaN(returnStarted)) return;
        if (BetterCombatAttack.active(client) || continuing) {
            animatedHands = new AnimatedHands(new WeakReference<>(player), player.tickCount,
                    player.getInventory().selected, player.getMainHandItem(), player.getOffhandItem());
            returnStarted = Double.NaN;
        }
    }

    private static boolean animatorRecovery(Player player) {
        return animatedHands != null && animatedHands.recent(player) && animatedHands.stillHeld(player);
    }

    // The animator can give the camera back after the native equip rise has already finished.
    public static double recoveryDrop(LivingEntity owner, float partial, float equip) {
        if (!(owner instanceof Player player) || !animatorRecovery(player)) return 0;
        double time = player.tickCount + (double)partial;
        if (Double.isNaN(returnStarted)) returnStarted = time;
        double progress = Math.clamp((time - returnStarted) / 4, 0, 1);
        double remain = 1 - progress * progress * (3 - 2 * progress);
        return Math.max(0, .30 * remain - .6 * equip);
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
        if (animatorRecovery(player)) {
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
