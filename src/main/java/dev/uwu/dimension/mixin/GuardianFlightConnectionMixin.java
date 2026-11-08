package dev.uwu.dimension.mixin;
import dev.uwu.dimension.GuardianFlight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class GuardianFlightConnectionMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int receivedMovePacketCount;
    @Shadow private int knownMovePacketCount;
    @Redirect(method="handleMovePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;",ordinal=0))
    private net.minecraft.world.phys.Vec3 expectedLaunchDisplacement(ServerPlayer target){
        var velocity=target.getDeltaMovement();
        if(!target.getPersistentData().contains(GuardianFlight.STATE)||velocity.y<=0)return velocity;
        // Vanilla compares displacement since the first packet of this server tick. Multiple
        // legitimate client ticks therefore need the matching accumulated authorized impulse.
        // Preserve vanilla's >5-packet rejection rather than granting an unbounded allowance.
        int packets=receivedMovePacketCount-knownMovePacketCount+1;
        if(packets>5)packets=1;
        return new net.minecraft.world.phys.Vec3(1.2,48,1.2).scale(Math.max(1,packets));
    }
    @Inject(method="tick",at=@At("HEAD"))
    private void authorizedThrust(CallbackInfo ci){
        // Server-authored upward propulsion is not unsupported hovering. Keep every ordinary
        // movement-distance/collision validation intact; never grant the player flight abilities.
        if(player.getPersistentData().contains(GuardianFlight.STATE)&&player.getDeltaMovement().y>0)
            clientIsFloating=false;
    }
}
