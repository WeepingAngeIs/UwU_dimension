package dev.uwu.dimension.mixin;

import dev.uwu.dimension.SakuraGrassColors;
import dev.uwu.dimension.SakuraMaterials;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Grasses and ferns rooted on new Sakura turf inherit its local color. */
@Mixin(BiomeColors.class)
public abstract class SakuraGrassTintMixin {
    @Inject(method="getAverageGrassColor",at=@At("HEAD"),cancellable=true)
    private static void uwu$turfColor(BlockAndTintGetter level,BlockPos pos,CallbackInfoReturnable<Integer> ci) {
        var clientLevel=net.minecraft.client.Minecraft.getInstance().level;
        if(clientLevel==null || !clientLevel.dimension().equals(dev.uwu.dimension.ReefLife.REACH))return;
        if(level.getBlockState(pos.below()).is(SakuraMaterials.GRASS.get()))ci.setReturnValue(SakuraGrassColors.color(level,pos));
        else if(level.getBlockState(pos.below(2)).is(SakuraMaterials.GRASS.get()))ci.setReturnValue(SakuraGrassColors.color(level,pos));
    }
}
