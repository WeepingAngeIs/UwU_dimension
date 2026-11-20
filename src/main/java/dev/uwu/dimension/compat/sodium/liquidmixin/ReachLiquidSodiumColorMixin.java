package dev.uwu.dimension.compat.sodium.liquidmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uwu.dimension.client.ReachLiquidArt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer", remap = false)
public abstract class ReachLiquidSodiumColorMixin {
    @WrapOperation(method = "updateQuad", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/model/color/ColorProvider;getColors("
                    + "Lnet/caffeinemc/mods/sodium/client/world/LevelSlice;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/core/BlockPos$MutableBlockPos;Ljava/lang/Object;"
                    + "Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadView;[IZ)V"))
    private void uwu$reachWaterColor(@Coerce Object provider, @Coerce Object region, BlockPos pos,
            BlockPos.MutableBlockPos scratch, Object state, @Coerce Object face, int[] colors,
            boolean blend, Operation<Void> original) {
        original.call(provider, region, pos, scratch, state, face, colors, blend);
        if (!(state instanceof FluidState fluid) || !ReachLiquidArt.water(fluid)
                || !(region instanceof BlockAndTintGetter view)
                || !(face instanceof ReachLiquidSodiumQuadAccessor quad)
                || !ReachLiquidArt.waterFace(view, quad.uwu$reachFluidFace())) return;

        // The sprite already carries its blue and translucency. Keep each provider-supplied alpha.
        for (int i = 0; i < colors.length; i++) {
            colors[i] |= 0x00FFFFFF;
        }
    }
}
