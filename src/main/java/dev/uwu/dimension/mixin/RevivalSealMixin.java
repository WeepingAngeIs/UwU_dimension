package dev.uwu.dimension.mixin;
import dev.uwu.dimension.CounterEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import net.sweenus.simplyswords.item.custom.WaxweaverSwordItem;
import net.sweenus.simplyswords.item.custom.WickpiercerSwordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Mark time is authoritative even when awakening reduces the weapon's own cooldown. */
@Mixin(value={WaxweaverSwordItem.class,WickpiercerSwordItem.class},remap=false)
public abstract class RevivalSealMixin {
    @Inject(method="canRevive",at=@At("HEAD"),cancellable=true,remap=false)
    private void sealRevival(LivingEntity owner,ItemStack stack,DamageSource source,CallbackInfoReturnable<Boolean> callback){
        var mark=CounterEffects.marked(owner);
        if(mark!=null&&mark.school().equals("REAP")&&net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(mark.target()))callback.setReturnValue(false);
    }
}
