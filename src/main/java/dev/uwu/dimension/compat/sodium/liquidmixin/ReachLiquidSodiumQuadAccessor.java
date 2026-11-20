package dev.uwu.dimension.compat.sodium.liquidmixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The default Sodium fluid mesher owns a ModelQuad and sets its sprite for each face. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.model.quad.ModelQuad", remap = false)
public interface ReachLiquidSodiumQuadAccessor {
    @Invoker("getSprite")
    TextureAtlasSprite uwu$reachFluidFace();
}
