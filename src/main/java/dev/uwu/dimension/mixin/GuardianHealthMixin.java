package dev.uwu.dimension.mixin;
import dev.uwu.dimension.GuardianVitality;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AttributeInstance.class)
public abstract class GuardianHealthMixin implements GuardianVitality {
 @Unique private double uwu$guardianMaximum;
 public void uwu$guardianHealth(boolean juvenile){uwu$guardianMaximum=juvenile?1999.8D:6666.0D;}
 @Inject(method="getValue",at=@At("RETURN"),cancellable=true)
 private void uwu$maximum(CallbackInfoReturnable<Double> cir){if(uwu$guardianMaximum>0)cir.setReturnValue(uwu$guardianMaximum);}
}
