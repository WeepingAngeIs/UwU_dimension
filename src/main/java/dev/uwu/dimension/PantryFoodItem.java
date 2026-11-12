package dev.uwu.dimension;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Food that shows its guaranteed post-meal buff in the item tooltip. */
final class PantryFoodItem extends Item {
    private final Holder<MobEffect> effect;
    private final int durationSeconds;
    private final int amplifier;

    PantryFoodItem(Item.Properties properties, Holder<MobEffect> effect, int durationTicks, int amplifier) {
        super(properties);
        this.effect = effect;
        this.durationSeconds = durationTicks / 20;
        this.amplifier = amplifier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        Component effectName = Component.translatable(effect.value().getDescriptionId());
        Component level = Component.translatable("enchantment.level." + (amplifier + 1));
        tooltip.add(Component.translatable("item.uwudimension.food_effect", effectName, level, durationSeconds)
                .withStyle(ChatFormatting.GRAY));
    }
}
