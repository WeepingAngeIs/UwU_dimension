package dev.uwu.dimension.mixin;
import dev.uwu.dimension.GuardianVitality;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AttributeInstance.class)
public abstract class GuardianHealthMixin implements GuardianVitality {
 @Unique private boolean uwu$guardian;
 public void uwu$guardianHealth(){uwu$guardian=true;}
 @Inject(method="getValue",at=@At("RETURN"),cancellable=true)
 private void uwu$maximum(CallbackInfoReturnable<Double> cir){if(uwu$guardian)cir.setReturnValue(6666.0);}
}
