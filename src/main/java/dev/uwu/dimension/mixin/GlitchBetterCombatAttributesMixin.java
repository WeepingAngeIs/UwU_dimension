package dev.uwu.dimension.mixin;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import dev.uwu.dimension.GlitchBow;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Better Combat normally reads the raw component, bypassing NeoForge's stack-aware event. */
@Pseudo
@Mixin(targets = "net.bettercombat.utils.AttributeModifierHelper", remap = false)
abstract class GlitchBetterCombatAttributesMixin {
    @Inject(method = "modifierMultimap(Lnet/minecraft/world/item/ItemStack;)Lcom/google/common/collect/Multimap;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void uwu$formModifiers(ItemStack stack,
            CallbackInfoReturnable<Multimap<Holder<Attribute>, AttributeModifier>> result) {
        if (!(stack.getItem() instanceof GlitchBow)) return;
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create();
        for (var entry : stack.getAttributeModifiers().modifiers())
            modifiers.put(entry.attribute(),entry.modifier());
        result.setReturnValue(modifiers);
    }
}
