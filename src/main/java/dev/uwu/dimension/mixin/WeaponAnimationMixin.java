package dev.uwu.dimension.mixin;

import dev.uwu.dimension.WeaponAnimationAdapter;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs only after Better Combat resolves an existing stack profile. */
@Pseudo
@Mixin(targets="net.bettercombat.logic.WeaponRegistry",remap=false)
abstract class WeaponAnimationMixin {
    @Inject(method="getAttributes(Lnet/minecraft/world/item/ItemStack;)Lnet/bettercombat/api/WeaponAttributes;",
            at=@At("RETURN"),cancellable=true,remap=false)
    private static void uwu$weaponAnimation(ItemStack stack,CallbackInfoReturnable<Object> result){
        Object before=result.getReturnValue();Object after=WeaponAnimationAdapter.adapt(stack,before);
        if(after!=before)result.setReturnValue(after);
    }
}
