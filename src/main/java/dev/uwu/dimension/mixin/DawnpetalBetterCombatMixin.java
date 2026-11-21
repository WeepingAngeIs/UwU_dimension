package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.uwu.dimension.DawnpetalBetterCombat;
import dev.uwu.dimension.DawnpetalReach;
import dev.uwu.dimension.ReturnbloomTurnBack;
import net.bettercombat.api.AttackHand;
import net.bettercombat.network.Packets;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.bettercombat.network.ServerNetwork", remap = false)
abstract class DawnpetalBetterCombatMixin {
    // The native 2.4.0 server request body runs once per swing, including an empty target list.
    @Unique private static final String HIT = "lambda$handleAttackRequest$3";

    @Inject(method = "handleAttackAnimation", at = @At("TAIL"))
    private static void uwu$dawnpetalWindup(Packets.AttackAnimation packet, MinecraftServer server,
                                            ServerPlayer player, CallbackInfo ci) {
        DawnpetalBetterCombat.animation(player, packet);
    }

    @WrapOperation(method = "lambda$handleAttackRequest$4", at = @At(value = "INVOKE",
        target = "Lnet/bettercombat/logic/PlayerAttackHelper;swapHandAttributes(Lnet/minecraft/world/entity/player/Player;ZLjava/lang/Runnable;)V"))
    private static void uwu$dawnpetalStrike(Player actor, boolean offhand, Runnable swing, Operation<Void> original,
            @Local(argsOnly = true) ServerPlayer player, @Local(argsOnly = true) AttackHand hand,
            @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) Packets.C2S_AttackRequest request,
            @Local(argsOnly = true) ServerGamePacketListenerImpl connection) {
        if (actor == player && player.connection == connection && player.serverLevel() == level
                && DawnpetalBetterCombat.strike(player, hand, request)) {
            DawnpetalReach.nativeStrike(player, request, hand.itemStack(), () -> original.call(actor, offhand, swing));
        } else if (actor == player && player.connection == connection && player.serverLevel() == level && hand != null) {
            ReturnbloomTurnBack.betterCombat(player, hand.itemStack(),
                    !offhand && !hand.isOffHand() && request.selectedSlot() == player.getInventory().selected,
                    () -> original.call(actor, offhand, swing));
        } else {
            original.call(actor, offhand, swing);
        }
    }

    @ModifyExpressionValue(method = HIT, at = @At(value = "INVOKE",
        target = "Lnet/bettercombat/logic/PlayerAttackHelper;getRangeForItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)D"))
    private static double uwu$dawnpetalDirectRange(double range,
            @Local(argsOnly = true) ServerPlayer player, @Local(argsOnly = true) AttackHand hand,
            @Local(argsOnly = true) Packets.C2S_AttackRequest request) {
        return DawnpetalReach.applies(player, request, hand.itemStack()) ? range + DawnpetalReach.BONUS : range;
    }

    @WrapOperation(method = HIT, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;handleInteract(Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;)V"))
    private static void uwu$dawnpetalForwardedAttack(ServerGamePacketListenerImpl connection, ServerboundInteractPacket packet,
            Operation<Void> original, @Local(argsOnly = true) AttackHand hand, @Local(argsOnly = true) Packets.C2S_AttackRequest request) {
        if (DawnpetalReach.applies(connection.player, request, hand.itemStack())) {
            DawnpetalReach.forward(connection, packet, hand.itemStack(), () -> original.call(connection, packet));
        } else {
            original.call(connection, packet);
        }
    }
}
