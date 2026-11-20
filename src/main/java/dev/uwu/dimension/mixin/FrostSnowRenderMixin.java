package dev.uwu.dimension.mixin;

import dev.uwu.dimension.ReefLife;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Soften only the visual precipitation alpha in the two Sakura snow biomes. */
@Mixin(LevelRenderer.class)
abstract class FrostSnowRenderMixin {
    @ModifyVariable(method="renderSnowAndRain", at=@At("STORE"), ordinal=1, require=0)
    private float uwu$gentleSnow(float intensity) {
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || !mc.level.dimension().equals(ReefLife.REACH)) return intensity;
        var biome=mc.level.getBiome(mc.player.blockPosition());
        if (biome.is(ResourceLocation.fromNamespaceAndPath("uwudimension","starlace_frostlands"))
                || biome.is(ResourceLocation.fromNamespaceAndPath("uwudimension","dusk_frostlands"))) return intensity*.32F;
        return intensity;
    }
}

