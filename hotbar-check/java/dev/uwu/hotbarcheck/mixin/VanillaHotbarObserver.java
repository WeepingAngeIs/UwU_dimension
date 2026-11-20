package dev.uwu.hotbarcheck.mixin;

import dev.uwu.hotbarcheck.HotbarProbe;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class VanillaHotbarObserver {
    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void hotbarCheck$observeVanillaCell(GuiGraphics gui, int x, int y, DeltaTracker tracker,
            Player player, ItemStack stack, int seed, CallbackInfo callback) {
        if (seed == 5) HotbarProbe.drawnVanillaFifth(stack);
    }
}
