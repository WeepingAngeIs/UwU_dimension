package dev.uwu.dimension.mixin;
import dev.uwu.dimension.CounterEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.sweenus.simplyswords.util.AbilityMethods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=AbilityMethods.class,remap=false)
public abstract class AxolotlAuraMixin {
    @Inject(method="applyAxolotlBuff",at=@At("HEAD"),cancellable=true,remap=false)
    private static void hushShoulderAura(ServerPlayer owner,CompoundTag left,CompoundTag right,CallbackInfo callback){
        var mark=CounterEffects.marked(owner);
        if(mark!=null&&mark.target().equals("simplyswords:chompolotl"))callback.cancel();
    }
}
