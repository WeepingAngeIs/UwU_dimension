package dev.uwu.dimension;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Bright, full-color particles used only for weapon and ability flashes. */
final class MagicGlowParticles {
    static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, "uwudimension");
    static final java.util.function.Supplier<SimpleParticleType> HEART =
            TYPES.register("neon_heart", () -> new SimpleParticleType(false));
    static final java.util.function.Supplier<SimpleParticleType> FLOWER =
            TYPES.register("neon_flower", () -> new SimpleParticleType(false));

    private MagicGlowParticles() {}
}
