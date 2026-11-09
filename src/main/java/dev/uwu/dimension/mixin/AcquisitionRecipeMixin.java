package dev.uwu.dimension.mixin;
import dev.uwu.dimension.AcquisitionPolicy;
import java.util.Optional;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(RecipeManager.class)
public abstract class AcquisitionRecipeMixin {
 @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.core.HolderLookup.Provider registries;
 @Inject(method="getRecipesFor",at=@At("RETURN"),cancellable=true)
 private <I extends RecipeInput,T extends Recipe<I>> void uwu$recipeChoices(RecipeType<T> type,I input,Level level,CallbackInfoReturnable<java.util.List<RecipeHolder<T>>> cir){
  cir.setReturnValue(cir.getReturnValue().stream().filter(r->AcquisitionPolicy.allowsCraft(r.value().assemble(input,level.registryAccess()))).toList());
 }
 @Inject(method="getAllRecipesFor",at=@At("RETURN"),cancellable=true)
 private <T extends Recipe<?>> void uwu$allChoices(RecipeType<T> type,CallbackInfoReturnable<java.util.List<RecipeHolder<T>>> cir){
  cir.setReturnValue(cir.getReturnValue().stream().filter(r->AcquisitionPolicy.allowsCraft(r.value().getResultItem(registries))).toList());
 }
 @Inject(method="getRecipes",at=@At("RETURN"),cancellable=true)
 private void uwu$recipeSync(CallbackInfoReturnable<java.util.Collection<RecipeHolder<?>>> cir){
  cir.setReturnValue(cir.getReturnValue().stream().filter(r->AcquisitionPolicy.allowsCraft(r.value().getResultItem(registries))).toList());
 }

 @Inject(method="getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",at=@At("RETURN"),cancellable=true)
 private <I extends RecipeInput,T extends Recipe<I>> void uwu$craft(RecipeType<T> type,I input,Level level,RecipeHolder<T> hint,CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir){
  var result=cir.getReturnValue();
  if(result.isPresent()&&!AcquisitionPolicy.allowsCraft(result.get().value().assemble(input,level.registryAccess())))cir.setReturnValue(Optional.empty());
 }
}
