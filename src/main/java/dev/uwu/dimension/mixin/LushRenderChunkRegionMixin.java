package dev.uwu.dimension.mixin;

import dev.uwu.dimension.lush.client.LushRenderView;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderChunkRegion.class)
public abstract class LushRenderChunkRegionMixin implements LushRenderView {
    @Shadow @Final protected Level level;

    @Override
    public @Nullable ClientLevel uwu$lushOwner() {
        return level instanceof ClientLevel clientLevel ? clientLevel : null;
    }

    @Override
    public Holder<Biome> uwu$lushBiome(BlockPos pos) {
        // The client uses its seeded biome zoom and a non-loading chunk lookup at this position.
        return level.getBiome(pos);
    }
}
