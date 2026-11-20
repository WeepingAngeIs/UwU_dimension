package dev.uwu.dimension.mixin;

import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseBasedChunkGenerator.class)
public interface ForestFluidPickerAccessor {
    @Accessor("globalFluidPicker")
    Supplier<Aquifer.FluidPicker> uwu$forestFluidPicker();
}
