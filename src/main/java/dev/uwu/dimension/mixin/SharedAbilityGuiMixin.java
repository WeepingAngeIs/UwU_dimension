package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uwu.dimension.SharedAbilityHotbar;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class SharedAbilityGuiMixin {
    @WrapOperation(method = "renderItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;renderSlot(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V"))
    private void uwu$skillCells(Gui hud, GuiGraphics gui, int x, int y, DeltaTracker tracker,
            Player player, ItemStack stack, int seed, Operation<Void> original) {
        if (SharedAbilityHotbar.drawSlot(gui, player, seed - 1, x, y)) return;
        // The left offhand item needs room for the separate held-weapon indicator.
        if (seed > 9 && x < gui.guiWidth() / 2 && SharedAbilityHotbar.showsWeaponIndicator(player)) x -= 22;
        original.call(hud, gui, x, y, tracker, player, stack, seed);
    }

    @WrapOperation(method = "renderItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V",
            ordinal = 1))
    private void uwu$inventorySelection(GuiGraphics gui, ResourceLocation sprite, int x, int y, int width,
            int height, Operation<Void> original) {
        if (!uwu$showsWeaponIndicator()) original.call(gui, sprite, x, y, width, height);
    }

    @WrapOperation(method = "renderItemHotbar", at = {
            @At(value = "INVOKE",
                target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V",
                ordinal = 2),
            @At(value = "INVOKE",
                target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V",
                ordinal = 4)
    })
    private void uwu$spaceForWeaponIndicator(GuiGraphics gui, ResourceLocation sprite, int x, int y,
            int width, int height, Operation<Void> original) {
        if (x < gui.guiWidth() / 2 && uwu$showsWeaponIndicator()) x -= 22;
        original.call(gui, sprite, x, y, width, height);
    }

    @WrapOperation(method = "renderItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V"))
    private void uwu$spaceForAttackProgress(GuiGraphics gui, ResourceLocation sprite, int fullWidth,
            int fullHeight, int u, int v, int x, int y, int width, int height, Operation<Void> original) {
        if (x < gui.guiWidth() / 2 && uwu$showsWeaponIndicator()) x -= 22;
        original.call(gui, sprite, fullWidth, fullHeight, u, v, x, y, width, height);
    }

    @Inject(method = "renderItemHotbar", at = @At("TAIL"))
    private void uwu$heldWeaponIndicator(GuiGraphics gui, DeltaTracker tracker, CallbackInfo callback) {
        if (Minecraft.getInstance().getCameraEntity() instanceof Player player) {
            SharedAbilityHotbar.renderWeaponIndicator(gui, player);
            SharedAbilityHotbar.renderSkillLabel(gui, player);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean uwu$showsWeaponIndicator() {
        return Minecraft.getInstance().getCameraEntity() instanceof Player player
                && SharedAbilityHotbar.showsWeaponIndicator(player);
    }
}
