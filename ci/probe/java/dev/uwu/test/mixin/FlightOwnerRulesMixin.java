package dev.uwu.test.mixin;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Test only: exercise dedicated-style movement validation without the integrated-owner exemption. */
@Mixin(IntegratedServer.class)
public abstract class FlightOwnerRulesMixin {
    @Inject(method="isSingleplayerOwner",at=@At("HEAD"),cancellable=true)
    private void dedicatedRules(GameProfile profile,CallbackInfoReturnable<Boolean> cir){
        if("flight".equals(System.getenv("UWU_VISIT")))cir.setReturnValue(false);
    }
}
