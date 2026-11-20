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
    static final Supplier<SimpleParticleType> IMPACT = TYPES.register(
            "heartbriar_impact", () -> new SimpleParticleType(false));
    static final Supplier<SimpleParticleType> CASCADE = TYPES.register(
            "fallsong_cascade", () -> new SimpleParticleType(false));
    static final Supplier<ParticleType<PetalfoldParticleOptions>> PETALFOLD_FLOWER = TYPES.register(
            "petalfold_view_flower", () -> PetalfoldParticleOptions.type(false));
    static final Supplier<ParticleType<PetalfoldParticleOptions>> PETALFOLD_HEART = TYPES.register(
            "petalfold_view_heart", () -> PetalfoldParticleOptions.type(true));
    static final int PULSE_TICKS = 12;

    private HeartShieldEffects() {}

    static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    static void aura(ServerLevel level, LivingEntity holder, long activeTicks) {
        if (Math.floorMod(activeTicks, PULSE_TICKS) != 0) return;
        Vec3 at = holder.position().add(0, HeartbriarBoundaryShape.centerY(0, holder.getBbHeight()), 0);
        double yRadius = HeartbriarBoundaryShape.verticalRadius(holder.getBbHeight());
        double phase = (activeTicks / PULSE_TICKS) * 1.2;
        for (var viewer : level.players()) {
            if (!CupidEffects.inRange(viewer, viewer.distanceToSqr(at))) continue;
            int quality = Mth.clamp(ReachLife.preference(viewer, "sakura_effects", 2), 0, 2);
            // Count zero carries the tracked holder ID and quality to one client animation.
            level.sendParticles(viewer, CIRCUIT.get(), false, at.x, at.y, at.z,
                    0, holder.getId(), quality, 0, 1);
            for (int i = 0; i <= quality; i++) {
                double angle = phase + i * 2 * Math.PI / (quality + 1);
                double latitude = .72 * Math.sin(angle + phase * .3);
                double horizontal = HeartbriarBoundaryShape.HORIZONTAL_RADIUS * Math.cos(latitude);
                level.sendParticles(viewer, MagicGlowParticles.FLOWER.get(), false,
                        at.x + horizontal * Math.cos(angle), at.y + yRadius * Math.sin(latitude),
                        at.z + horizontal * Math.sin(angle),
                        1, 0, 0, 0, 0);
            }
        }
    }

    static void bloom(ServerLevel level, LivingEntity blocker, PetalfoldImpact impact) {
        PetalfoldBloom.play(level, blocker, impact);
    }
}
