package dev.uwu.dimension.mixin;
import net.minecraft.resources.ResourceKey;
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
}
