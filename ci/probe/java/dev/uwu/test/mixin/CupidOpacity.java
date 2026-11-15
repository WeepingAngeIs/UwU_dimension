package dev.uwu.test.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface CupidOpacity {
    @Accessor("alpha") float uwuProbeOpacity();
}
