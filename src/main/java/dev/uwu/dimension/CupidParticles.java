package dev.uwu.dimension;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
/** Cupid-only particle identity; vanilla hearts and other charm sources are unchanged. */
public final class CupidParticles {
 public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> TYPES=DeferredRegister.create(Registries.PARTICLE_TYPE,"uwudimension");
 public static final java.util.function.Supplier<SimpleParticleType> PASTEL_HEART=TYPES.register("pastel_heart",()->new SimpleParticleType(false));
}
