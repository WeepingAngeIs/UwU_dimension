package dev.uwu.throwcheck.mixin;

import dev.uwu.throwcheck.ThrowProbe;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.network.Packets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.bettercombat.network.ServerNetwork", remap = false)
abstract class AttackRequestObserver {
    @Inject(method = "lambda$handleAttackRequest$4", at = @At("HEAD"))
    private static void throwCheck$recordAttackRequest(ServerPlayer player, Packets.C2S_AttackRequest request,
            AttackHand hand, WeaponAttributes attributes, WeaponAttributes.Attack attack, ServerLevel level,
            boolean sneaking, ServerGamePacketListenerImpl connection, CallbackInfo ci) {
        ThrowProbe.receivedAttack(player, request.entityIds(), request.cursorTarget(), request.selectedSlot());
    }
}
