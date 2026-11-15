package dev.uwu.dimension;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

/** Rear attacks are within 60 degrees of the target's horizontal backwards direction. */
@EventBusSubscriber(modid = "uwudimension")
public final class DaggerBackstab {
    private DaggerBackstab() {}

    static boolean isBehind(LivingEntity target, Vec3 attackPosition) {
        Vec3 displacement = attackPosition.subtract(target.position());
        double distanceSquared = displacement.x * displacement.x + displacement.z * displacement.z;
        if (distanceSquared < 1.0e-8) return false;
        Vec3 facing = target.getLookAngle();
        double facingSquared = facing.x * facing.x + facing.z * facing.z;
        // Looking vertically does not change the player's horizontal head yaw.
        if (facingSquared < 1.0e-8) {
            double yaw = Math.toRadians(target.getYHeadRot());
            facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            facingSquared = 1;
        }
        double dot = displacement.x * facing.x + displacement.z * facing.z;
        return dot <= -0.5 * Math.sqrt(distanceSquared * facingSquared);
    }

    static boolean isDagger(Player player) {
        var item = player.getMainHandItem().getItem();
        return item instanceof Arsenal.RelicItem relic && relic.relic == Arsenal.Relic.ROSE_DAGGER
                || item instanceof CounterArsenal.CounterItem counter && counter.entry.shape().equals("dagger");
    }

    @SubscribeEvent
    public static void critical(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (!(event.getTarget() instanceof LivingEntity target) || !isDagger(player)
                || !isBehind(target, player.position())) return;
        // NeoForge applies this once to the ordinary cooldown-scaled attack damage,
        // replacing vanilla's 1.5x airborne critical rather than multiplying it.
        event.setCriticalHit(true);
        event.setDamageMultiplier(2.0F);
    }
}

