package dev.uwu.dimension;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/** Apotheosis 8.9 exposes its loot rarity as a data component. Resolve it by id
 * after registration so servers without Apotheosis need no extra classes. */
final class CupidRarity {
    private static final ResourceLocation RARITY = ResourceLocation.fromNamespaceAndPath("apotheosis", "rarity");

    static boolean hasMythicPresentation(net.minecraft.world.item.ItemStack stack) {
        var rarity = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(RARITY);
        return rarity.isPresent() && hasMythicValue(stack, rarity.get());
    }

    private static <T> boolean hasMythicValue(net.minecraft.world.item.ItemStack stack, DataComponentType<T> type) {
        var value = stack.get(type);
        return value != null && type.codec() != null && type.codec().encodeStart(NbtOps.INSTANCE, value)
                .result().map(tag -> tag.getAsString().equals("apotheosis:mythic")).orElse(false);
    }

    static void apply(ModifyDefaultComponentsEvent event) {
        BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(RARITY).ifPresent(type -> mythic(event, type));
    }

    private static <T> void mythic(ModifyDefaultComponentsEvent event, DataComponentType<T> type) {
        var codec = type.codec();
        if (codec == null) return;
        codec.parse(NbtOps.INSTANCE, StringTag.valueOf("apotheosis:mythic")).result().ifPresent(rarity ->
                event.modify(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get(), patch -> patch.set(type, rarity)));
    }
}
