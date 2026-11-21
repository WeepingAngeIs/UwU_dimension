package dev.uwu.dimension.lush;

import dev.uwu.dimension.ReefLife;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/** The same local boundary is used by cave artwork and the server's vine harvest. */
public final class LushCaveScope {
    public static final ResourceKey<Biome> LANTERN_GROTTO = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("uwudimension", "lantern_grotto"));

    private LushCaveScope() {}

    public static boolean isCaveBiome(Holder<Biome> biome) {
        return biome.is(Biomes.LUSH_CAVES) || biome.is(LANTERN_GROTTO);
    }

    public static boolean contains(Level level, BlockPos pos) {
        return level.dimension().equals(ReefLife.REACH) && isCaveBiome(level.getBiome(pos));
    }
}
