package dev.uwu.dimension;

import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.minecraft.client.Minecraft;

/** Reached only inside Better Combat's optional native client attack. */
public final class DawnpetalTargetSelection {
    private DawnpetalTargetSelection() {}

    public static double range(Minecraft minecraft, double range) {
        var combat = (MinecraftClient_BetterCombat)(Object)minecraft;
        var hand = combat.getCurrentAttackHand();
        if (minecraft.player == null || hand == null || hand.isOffHand() || combat.getUpswingTicks() != 0
                || !hand.itemStack().is(RevivalBlossoms.AXE.get())) return range;
        var held = minecraft.player.getMainHandItem();
        if (!held.is(RevivalBlossoms.AXE.get())) return range;
        if (held != hand.itemStack()) {
            // Inventory synchronization can replace a client stack when its revival timer updates.
            var identity = DawnpetalVanillaReach.syncedIdentity(held);
            if (identity == null || !identity.equals(DawnpetalVanillaReach.syncedIdentity(hand.itemStack()))) return range;
        }
        return range + DawnpetalReach.BONUS;
    }
}
