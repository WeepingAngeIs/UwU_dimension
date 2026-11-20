package dev.uwu.dimension;

import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Small, original botanical and elemental shapes emitted by the bounded spell fields. */
public final class ArtParticles {
    public static final DeferredRegister<ParticleType<?>> TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, "uwudimension");
    public static final Supplier<SimpleParticleType> GRASS =
            TYPES.register("rising_grass", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> ICE =
            TYPES.register("ice_shard", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> WIND =
            TYPES.register("wind_petal", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> WATER =
            TYPES.register("water_bead", () -> new SimpleParticleType(false));

    private ArtParticles() {}
}
