package dev.uwu.dimension.lush.client;

import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/** Client meshing context. The owner is the level attached to this particular render job. */
public interface LushRenderView {
    ResourceKey<Biome> LANTERN_GROTTO = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("uwudimension", "lantern_grotto"));

    @Nullable ClientLevel uwu$lushOwner();
    Holder<Biome> uwu$lushBiome(BlockPos pos);

    static boolean isCaveBiome(Holder<Biome> biome) {
        return biome.is(Biomes.LUSH_CAVES) || biome.is(LANTERN_GROTTO);
    }

    static @Nullable ClientLevel owner(BlockAndTintGetter view) {
        if (view instanceof ClientLevel clientLevel) return clientLevel;
        return view instanceof LushRenderView region ? region.uwu$lushOwner() : null;
    }

    static Holder<Biome> biome(BlockAndTintGetter view, ClientLevel owner, BlockPos pos) {
        return view instanceof LushRenderView region ? region.uwu$lushBiome(pos) : owner.getBiome(pos);
    }
}
