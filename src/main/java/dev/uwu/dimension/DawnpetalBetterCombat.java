package dev.uwu.dimension;

import net.bettercombat.api.AttackHand;
import net.bettercombat.api.CombatFlags;
import net.bettercombat.logic.AnimatedHand;
import net.bettercombat.logic.PlayerAttackHelper;
import net.bettercombat.logic.WeaponRegistry;
import net.bettercombat.network.Packets;
import net.minecraft.server.level.ServerPlayer;

/** Loaded from Better Combat's own packet handler, exclusively when the optional mod is installed. */
public final class DawnpetalBetterCombat {
    private DawnpetalBetterCombat() {}

    public static void animation(ServerPlayer player, Packets.AttackAnimation packet) {
        if (!player.serverLevel().getServer().isSameThread()) return;
        if (Packets.AttackAnimation.StopSymbol.equals(packet.animationName())) {
            DawnpetalAttackWindow.cancel(player);
            return;
        }
        if (packet.animatedHand() == null || packet.animatedHand().isOffHand()
                || packet.animationName() == null || CombatFlags.isAttackDisabled(player)) return;
        var weapon = player.getMainHandItem();
        if (!weapon.is(RevivalBlossoms.AXE.get()) || !player.isAlive() || player.isSpectator() || player.isUsingItem()) return;
        var profile = WeaponRegistry.getAttributes(weapon);
        if (profile == null || profile.attacks() == null) return;
        int selections = profile.attacks().length * (PlayerAttackHelper.isDualWielding(player) ? 2 : 1);
        for (int combo = 0; combo < selections; combo++) {
            var selected = PlayerAttackHelper.getCurrentAttack(player, combo);
            if (selected == null || selected.isOffHand() || selected.itemStack() != weapon || selected.attack() == null
                    || AnimatedHand.from(selected.isOffHand(), selected.attributes().isTwoHanded()) != packet.animatedHand()
                    || !packet.animationName().equals(selected.attack().animation())) continue;
            float length = PlayerAttackHelper.getAttackCooldownTicksCapped(player);
            float rate = (float)selected.upswingRate();
            if (!Float.isFinite(length) || !Float.isFinite(rate) || player.getAttackStrengthScale(0) < 1F - rate) return;
            int windup = Math.max(1, Math.round(length * rate));
            int duration = Math.max(windup, Math.round(length));
            // All timing is derived from the server's selected attack and attributes.
            DawnpetalAttackWindow.start(player, selected.attack().animation(), windup, duration);
            return;
        }
    }

    public static boolean strike(ServerPlayer player, AttackHand selected, Packets.C2S_AttackRequest request) {
        if (selected == null || selected.isOffHand() || selected.attack() == null || selected.itemStack() == null
                || !selected.itemStack().is(RevivalBlossoms.AXE.get()) || !player.serverLevel().getServer().isSameThread()
                || CombatFlags.isAttackDisabled(player) || player.getAttackStrengthScale(0) < 1 - selected.upswingRate()) return false;
        return DawnpetalAttackWindow.strike(player, selected.itemStack(), selected.attack().animation(), request.selectedSlot());
    }
}
