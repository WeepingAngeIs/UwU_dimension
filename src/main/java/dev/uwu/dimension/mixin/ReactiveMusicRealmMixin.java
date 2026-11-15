package dev.uwu.dimension.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
/** Optional client-only context substitution, confined to Reactive Music's event sampling. */
@Pseudo
@Mixin(targets="circuitlord.reactivemusic.SongPicker",remap=false)
public abstract class ReactiveMusicRealmMixin {
 @Redirect(method="tickEventMap",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;"))
 private static ResourceKey<Level> uwu$musicRealm(Level level){
  return level.dimension().equals(dev.uwu.dimension.ReefLife.REACH)?Level.OVERWORLD:level.dimension();
 }
 @Unique private static final TagKey<Biome> UWU_MUSIC_GROVE = TagKey.create(Registries.BIOME,
         net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("uwudimension","sakura_music_land"));
 @Redirect(method="tickEventMap",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
 private static Holder<Biome> uwu$surfaceMusic(Level level, BlockPos at) {
     var actual = level.getBiome(at);
     if (level.dimension().equals(dev.uwu.dimension.ReefLife.REACH) && actual.is(UWU_MUSIC_GROVE))
         return level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.CHERRY_GROVE);
     return actual;
 }
}
