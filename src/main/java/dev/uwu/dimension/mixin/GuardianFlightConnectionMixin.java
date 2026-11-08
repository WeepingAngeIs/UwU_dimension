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
    @Inject(method="tick",at=@At("HEAD"))
    private void authorizedThrust(CallbackInfo ci){
        // Server-authored upward propulsion is not unsupported hovering. Keep every ordinary
        // movement-distance/collision validation intact; never grant the player flight abilities.
        if(player.getPersistentData().contains(GuardianFlight.STATE)&&player.getDeltaMovement().y>0)
            clientIsFloating=false;
    }
}
