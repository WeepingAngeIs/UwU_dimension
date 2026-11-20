package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.uwu.dimension.client.ReachLiquidArt;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LiquidBlockRenderer.class)
public abstract class ReachLiquidVanillaMixin {
    @WrapOperation(method = "tesselate", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/neoforged/neoforge/client/textures/FluidSpriteCache;getFluidSprites("
                    + "Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/material/FluidState;)[Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
    private TextureAtlasSprite[] uwu$reachLiquidSprites(BlockAndTintGetter view, BlockPos pos, FluidState state,
            Operation<TextureAtlasSprite[]> original) {
        return ReachLiquidArt.sprites(original.call(view, pos, state), view, state);
    }

    @ModifyExpressionValue(method = "tesselate", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/neoforged/neoforge/client/extensions/common/IClientFluidTypeExtensions;getTintColor("
                    + "Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/BlockAndTintGetter;"
                    + "Lnet/minecraft/core/BlockPos;)I"))
    private int uwu$reachWaterTint(int original, BlockAndTintGetter view, @Local(argsOnly = true) FluidState state,
            @Local TextureAtlasSprite[] actual) {
        return ReachLiquidArt.waterTint(original, view, state, actual);
    }
}
