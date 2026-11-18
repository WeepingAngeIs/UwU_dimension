package dev.uwu.dimension;

import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

@EventBusSubscriber(modid = "uwudimension")
public final class HeartbriarImpact {
    private HeartbriarImpact() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlock(LivingShieldBlockEvent event) {
        var holder = event.getEntity();
        if (!(holder.level() instanceof ServerLevel level)
                || !holder.getUseItem().is(HeartShields.HEARTBRIAR_SHIELD.get())) return;

        // Sample the bearing immediately: the projectile may be reflected or
        // removed and the defender may move before the block is finalized.
        Vec3 center = holder.position().add(0, holder.getBbHeight() * .5 + .08, 0);
        Vec3 direction = bearing(event.getDamageSource(), center, holder);
        double yaw = Math.atan2(direction.x, direction.z);
        double elevation = Math.atan2(direction.y, Math.hypot(direction.x, direction.z));
        var damage = event.getDamageContainer();
        var server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (event.isCanceled() || damage.getBlockedDamage() <= 0
                    || !holder.isAlive() || holder.level() != level) return;
            for (var viewer : level.players()) {
                if (CupidEffects.inRange(viewer, viewer.distanceToSqr(center))) {
                    // Count zero packs the entity ID and two angles into one
                    // animation cue. X=sin(yaw)cos(el), Y=sin(el), Z=cos(yaw)cos(el).
                    level.sendParticles(viewer, HeartShieldEffects.IMPACT.get(), false,
                        center.x, center.y, center.z, 0, holder.getId(), yaw, elevation, 1);
                }
            }
        }));
    }

    private static Vec3 bearing(DamageSource source, Vec3 center, LivingEntity holder) {
        Vec3 origin = source.getSourcePosition();
        if (source.sourcePositionRaw() == null && source.getDirectEntity() instanceof LivingEntity attacker) {
            origin = attacker.getBoundingBox().getCenter();
        }
        Vec3 offset = origin == null ? Vec3.ZERO : origin.subtract(center);
        if (source.getDirectEntity() instanceof Projectile shot) {
            Vec3 travel = shot.getDeltaMovement();
            if (travel.lengthSqr() > 1.0e-6 && (offset.lengthSqr() < .01 || offset.dot(travel) > 0)) {
                return travel.scale(-1);
            }
        }
        return offset.lengthSqr() > 1.0e-6 ? offset : holder.getLookAngle();
    }
}
