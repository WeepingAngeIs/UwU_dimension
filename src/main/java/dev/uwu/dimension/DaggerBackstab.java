package dev.uwu.dimension;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

/** Approximate the target's view with the forward half-plane of its horizontal head yaw. */
@EventBusSubscriber(modid = "uwudimension")
public final class DaggerBackstab {
    private DaggerBackstab() {}

    public static boolean isBehind(LivingEntity target, Vec3 attackPosition) {
        Vec3 displacement = attackPosition.subtract(target.position());
        double distanceSquared = displacement.x * displacement.x + displacement.z * displacement.z;
        if (!Double.isFinite(distanceSquared) || distanceSquared < 1.0e-8
                || !Float.isFinite(target.getYHeadRot())) return false;
        // Horizontal head yaw remains meaningful when a player looks straight up or down.
        double yaw = Math.toRadians(target.getYHeadRot());
        double dot = displacement.x * -Math.sin(yaw) + displacement.z * Math.cos(yaw);
        // Exactly sideways is not behind. The small normalized tolerance keeps floating-point
        // rounding at the 90-degree boundary from intermittently awarding a backstab.
        return dot < -1.0e-6 * Math.sqrt(distanceSquared);
    }

    public static boolean isDagger(Player player) {
        var item = player.getMainHandItem().getItem();
        return item instanceof Arsenal.RelicItem relic && relic.relic == Arsenal.Relic.ROSE_DAGGER
                || item instanceof CounterArsenal.CounterItem counter && counter.entry.shape().equals("dagger");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void critical(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (!(event.getTarget() instanceof LivingEntity target) || !isDagger(player)
                || !isBehind(target, player.position())) return;
        // Preserve normal critical feedback while the attack hook doubles the entire
        // cooldown-scaled damage, including enchantments, once before armor.
        event.setCriticalHit(true);
        event.setDamageMultiplier(1.0F);
    }
}

