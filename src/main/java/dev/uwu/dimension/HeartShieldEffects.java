package dev.uwu.dimension;

import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

final class HeartShieldEffects {
    private static final DeferredRegister<ParticleType<?>> TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, "uwudimension");
    static final Supplier<SimpleParticleType> CIRCUIT = TYPES.register(
            "heartbriar_circuit", () -> new SimpleParticleType(false));
    static final int PULSE_TICKS = 12;

    private HeartShieldEffects() {}

    static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    static void aura(ServerLevel level, LivingEntity holder, long activeTicks) {
        if (Math.floorMod(activeTicks, PULSE_TICKS) != 0) return;
        Vec3 at = holder.position().add(0, holder.getBbHeight() * .1, 0);
        double phase = (activeTicks / PULSE_TICKS) * 1.2;
        for (var viewer : level.players()) {
            if (!CupidEffects.inRange(viewer, viewer.distanceToSqr(at))) continue;
            int quality = Mth.clamp(ReachLife.preference(viewer, "sakura_effects", 2), 0, 2);
            // Count zero carries the tracked holder ID and quality to one client animation.
            level.sendParticles(viewer, CIRCUIT.get(), false, at.x, at.y, at.z,
                    0, holder.getId(), quality, 0, 1);
            for (int i = 0; i <= quality; i++) {
                double angle = phase + i * 2 * Math.PI / (quality + 1);
                level.sendParticles(viewer, MagicGlowParticles.FLOWER.get(), false,
                        at.x + 1.3 * Math.cos(angle), at.y + .09, at.z + 1.3 * Math.sin(angle),
                        1, 0, 0, 0, 0);
            }
        }
    }

    static void bloom(ServerLevel level, LivingEntity blocker, LivingEntity attacker) {
        Vec3 facing = blocker.getLookAngle();
        Vec3 direction = attacker == null ? facing : attacker.position().subtract(blocker.position());
        Vec3 horizontal = new Vec3(direction.x, 0, direction.z);
        if (horizontal.lengthSqr() < .001) {
            horizontal = Vec3.directionFromRotation(0, blocker.getYRot());
        }
        horizontal = horizontal.normalize();
        // Place both sprays off axis, below the eye line and beyond the shield.
        Vec3 side = new Vec3(horizontal.z, 0, -horizontal.x);
        Vec3 center = blocker.position().add(horizontal.scale(1.15))
                .add(0, blocker.getBbHeight() * .25, 0);
        for (var viewer : level.players()) {
            if (!CupidEffects.inRange(viewer, viewer.distanceToSqr(center))) continue;
            int quality = Mth.clamp(ReachLife.preference(viewer, "sakura_effects", 2), 0, 2);
            for (int sign : new int[]{-1, 1}) {
                Vec3 petal = center.add(side.scale(sign * .78));
                level.sendParticles(viewer, MagicGlowParticles.FLOWER.get(), false,
                        petal.x, petal.y, petal.z, quality + 1, .11, .06, .11, .045);
                Vec3 heart = petal.add(side.scale(sign * .24)).add(0, .16, 0);
                level.sendParticles(viewer, MagicGlowParticles.HEART.get(), false,
                        heart.x, heart.y, heart.z, quality == 2 ? 2 : 1, .09, .05, .09, .03);
            }
        }
    }
}
