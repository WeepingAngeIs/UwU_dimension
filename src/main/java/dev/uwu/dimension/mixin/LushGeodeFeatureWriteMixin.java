package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uwu.dimension.lush.LushGeodes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Feature.class)
abstract class LushGeodeFeatureWriteMixin {
    @WrapOperation(method = "safeSetBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean uwu$observeGeodeWrite(WorldGenLevel level, BlockPos pos, BlockState state, int flags,
            Operation<Boolean> original) {
        return LushGeodes.writeFeature((Feature<?>) (Object) this, level, pos, state, flags, original);
    }
}
