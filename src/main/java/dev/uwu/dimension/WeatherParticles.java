package dev.uwu.dimension;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

final class WeatherParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, "uwudimension");
    static final java.util.function.Supplier<SimpleParticleType> SNOW_HEART =
            TYPES.register("snow_heart", () -> new SimpleParticleType(false));
    private WeatherParticles() {}
    static void register(IEventBus bus) { TYPES.register(bus); }
}

