package dev.uwu.hotbarcheck.mixin;

import dev.uwu.dimension.SharedAbilityHotbar;
import dev.uwu.hotbarcheck.HotbarProbe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SharedAbilityHotbar.class, remap = false)
abstract class HotbarIconObserver {
    @Inject(method = "drawSlot", at = @At("RETURN"))
    private static void hotbarCheck$observeCell(GuiGraphics gui, Player player, int index, int x, int y,
            CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue()) HotbarProbe.drawnCell(index);
    }

    @ModifyArg(method = "renderWeaponIndicator", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;II)V"))
    private static ItemStack hotbarCheck$observe(ItemStack stack) {
        HotbarProbe.drawnIcon(stack);
        return stack;
    }
}
