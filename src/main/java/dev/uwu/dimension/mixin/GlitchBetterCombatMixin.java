package dev.uwu.dimension.mixin;

import dev.uwu.dimension.GlitchBow;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional Better Combat lookups follow the current form without modifying the saved item. */
@Pseudo
@Mixin(targets = "net.bettercombat.logic.WeaponRegistry", remap = false)
abstract class GlitchBetterCombatMixin {
    @Inject(method = "getAttributes(Lnet/minecraft/world/item/ItemStack;)Lnet/bettercombat/api/WeaponAttributes;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void uwu$formAttributes(ItemStack stack, CallbackInfoReturnable<Object> result) {
        if (stack == null || !(stack.getItem() instanceof GlitchBow)) return;
        var id = ResourceLocation.fromNamespaceAndPath("bettercombat", "preset_id");
        var raw = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id).orElse(null);
        // Respect any explicit Better Combat preset the player or another mod supplied.
        if (raw == null || stack.has(raw)) return;
        try {
            var method = Class.forName("net.bettercombat.logic.WeaponRegistry").getMethod("getAttributes", ItemStack.class);
            var copy = GlitchBow.mimicStack(stack);
            Object attributes = method.invoke(null, copy);
            if (attributes == null) {
                ResourceLocation fallback = GlitchBow.combatFallback(stack);
                if (fallback != null) {
                    @SuppressWarnings("unchecked") DataComponentType<ResourceLocation> type = (DataComponentType<ResourceLocation>) raw;
                    copy.set(type, fallback);
                    attributes = method.invoke(null, copy);
                }
            }
            result.setReturnValue(attributes);
        } catch (ReflectiveOperationException ignored) {
            // Better Combat remains optional; its own normal lookup is the fallback.
        }
    }
}
