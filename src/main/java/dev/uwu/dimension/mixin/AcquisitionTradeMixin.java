package dev.uwu.dimension.mixin;
import dev.uwu.dimension.AcquisitionPolicy;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(MerchantContainer.class)
public abstract class AcquisitionTradeMixin {
 @ModifyVariable(method="setItem",at=@At("HEAD"),argsOnly=true)
 private ItemStack uwu$trade(ItemStack stack,int slot,ItemStack original){return slot==2&&!AcquisitionPolicy.allowsWeapon(stack)?ItemStack.EMPTY:stack;}
}
