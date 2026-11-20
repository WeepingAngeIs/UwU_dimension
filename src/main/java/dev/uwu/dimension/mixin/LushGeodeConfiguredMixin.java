package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.uwu.dimension.lush.LushGeodes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ConfiguredFeature.class)
abstract class LushGeodeConfiguredMixin {
    @WrapMethod(method = "place")
    private boolean uwu$withGeodeOrigin(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
            BlockPos origin, Operation<Boolean> original) {
        return LushGeodes.configured((ConfiguredFeature<?, ?>) (Object) this, level, generator, random, origin, original);
    }
}
