package dev.uwu.dimension.mixin;

import dev.uwu.dimension.lush.client.LushRenderView;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** NeoForge Sodium 0.8.13. No Sodium type enters a class signature or the vanilla-only classpath. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class LushSodiumLevelSliceMixin implements LushRenderView {
    @Shadow @Final private ClientLevel level;
    @Shadow public abstract Holder<Biome> getBiomeFabric(BlockPos pos);

    @Override
    public ClientLevel uwu$lushOwner() {
        return level;
    }

    @Override
    public Holder<Biome> uwu$lushBiome(BlockPos pos) {
        return getBiomeFabric(pos);
    }
}
