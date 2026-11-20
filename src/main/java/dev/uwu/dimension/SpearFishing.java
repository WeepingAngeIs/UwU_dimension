package dev.uwu.dimension;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Sneaking uses the spear as a fishing rod with vanilla waiting and loot rules. */
final class SpearFishing {
    private SpearFishing() {}

    static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return Items.FISHING_ROD.use(level, player, hand);
    }
}
