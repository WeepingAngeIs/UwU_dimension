package dev.uwu.dimension.mixin;
import dev.uwu.dimension.AcquisitionPolicy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LootTable.class)
public abstract class AcquisitionLootMixin {
 @Inject(method="getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",at=@At("RETURN"))
 private void uwu$loot(LootContext context,CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir){
  if(!AcquisitionPolicy.weaponAcquisitionEnabled())cir.getReturnValue().removeIf(AcquisitionPolicy::isWeapon);
 }
 @ModifyVariable(method="getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",at=@At("HEAD"),argsOnly=true)
 private java.util.function.Consumer<ItemStack> uwu$raw(java.util.function.Consumer<ItemStack> sink){return stack->{if(AcquisitionPolicy.allowsWeapon(stack))sink.accept(stack);};}
}
