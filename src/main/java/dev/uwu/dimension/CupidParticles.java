package dev.uwu.dimension;

import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CupidParticles {
    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, "uwudimension");
    public static final Supplier<SimpleParticleType> PASTEL_HEART = TYPES.register("pastel_heart", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> DRAW_HEART = TYPES.register("cupid_draw_heart", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> IMPACT_OUTLINE = TYPES.register("cupid_impact_outline", () -> new SimpleParticleType(false));
}
