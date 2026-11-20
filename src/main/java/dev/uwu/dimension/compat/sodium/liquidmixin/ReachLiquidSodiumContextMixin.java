package dev.uwu.dimension.compat.sodium.liquidmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uwu.dimension.client.ReachLiquidArt;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge Sodium 0.8.13 hands this sprite set to its default fluid mesher. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.neoforge.render.FluidRendererImpl$DefaultRenderContext", remap = false)
public abstract class ReachLiquidSodiumContextMixin {
    @WrapOperation(method = "render()V", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/textures/FluidSpriteCache;getFluidSprites("
                    + "Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/material/FluidState;)[Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
    private TextureAtlasSprite[] uwu$reachFluidSprites(BlockAndTintGetter view, BlockPos pos, FluidState state,
            Operation<TextureAtlasSprite[]> original) {
        return ReachLiquidArt.sprites(original.call(view, pos, state), view, state);
    }
}
