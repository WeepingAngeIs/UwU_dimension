package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.uwu.dimension.DawnpetalAttackWindow;
import dev.uwu.dimension.DawnpetalAbilities;
import dev.uwu.dimension.DawnpetalReach;
import dev.uwu.dimension.ReturnbloomPrime;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class DawnpetalPacketRangeMixin {
    // A switch away and back can happen between server ticks. Observe the accepted hotbar change.
    @Inject(method = "handleSetCarriedItem", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
        target = "Lnet/minecraft/world/entity/player/Inventory;selected:I"))
    private void uwu$dawnpetalSlotChanged(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        var player = ((ServerGamePacketListenerImpl)(Object)this).player;
        if (player.getInventory().selected != packet.getSlot()) {
            DawnpetalAttackWindow.cancel(player);
            DawnpetalAbilities.slotChanged(player);
            ReturnbloomPrime.clear(player);
        }
    }

    @WrapOperation(method = "handleInteract", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z"))
    private boolean uwu$dawnpetalPacketRange(ServerPlayer player, AABB box, double allowance, Operation<Boolean> original,
                                              @Local(argsOnly = true) ServerboundInteractPacket packet) {
        double bonus = DawnpetalReach.allowance((ServerGamePacketListenerImpl)(Object)this, packet);
        return original.call(player, box, allowance + bonus);
    }
}
