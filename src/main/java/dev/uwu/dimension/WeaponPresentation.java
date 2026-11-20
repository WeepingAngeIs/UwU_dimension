package dev.uwu.dimension;

import java.lang.reflect.Method;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.minecraft.util.Mth;

/** Carry and swing presentation for historical scythes that have no combat profile. */
public final class WeaponPresentation {
    private static Method combatLookup;
    private static boolean lookupAttempted;

    private WeaponPresentation() {}

    public static void idleScythe(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        if (entity.isUsingItem() || entity.swinging || model.attackTime > 0
                || entity.isFallFlying() || entity.isVisuallySwimming()) return;
        InteractionHand hand = entity.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = entity.getItemInHand(hand);
        if (!scythe(stack) || hasCombatProfile(stack)) return;
        var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        part.xRot = -.82F;
        part.yRot = side * .35F;
        part.zRot = side * .14F;
    }

    private static boolean scythe(ItemStack stack) {
        return GlitchBow.isRelic(stack, Arsenal.Relic.VESPER_SCYTHE) || GlitchBow.isScythe(stack)
                || stack.getItem() instanceof CounterArsenal.CounterItem item && item.entry.shape().equals("scythe");
    }

    public static boolean unprofiledScythe(ItemStack stack) {
        return scythe(stack) && !hasCombatProfile(stack);
    }

    public static void swingScythe(HumanoidModel<?> model, LivingEntity entity) {
        if (model.attackTime <= 0 || entity.isUsingItem() || entity.isFallFlying() || entity.isVisuallySwimming()) return;
        InteractionHand hand = entity.swingingArm == null ? InteractionHand.MAIN_HAND : entity.swingingArm;
        if (!unprofiledScythe(entity.getItemInHand(hand))) return;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
        var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float sweep = Mth.sin(Mth.sqrt(model.attackTime) * Mth.PI);
        part.xRot = -.82F - .5F * sweep;
        part.yRot = side * (.35F - 1.15F * sweep);
        part.zRot = side * (.14F - .18F * sweep);
    }

    private static boolean hasCombatProfile(ItemStack stack) {
        if (!ModList.get().isLoaded("bettercombat")) return false;
        try {
            if (!lookupAttempted) {
                lookupAttempted = true;
                combatLookup = Class.forName("net.bettercombat.logic.WeaponRegistry").getMethod("getAttributes", ItemStack.class);
            }
            // A failed optional lookup keeps the other animation provider in charge.
            return combatLookup == null || combatLookup.invoke(null, stack) != null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return true;
        }
    }
}
