package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.uwu.dimension.DawnpetalVanillaReach;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
abstract class DawnpetalVanillaClickMixin {
    @ModifyExpressionValue(method = "startAttack", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/client/ClientHooks;onClickInput(ILnet/minecraft/client/KeyMapping;Lnet/minecraft/world/InteractionHand;)Lnet/neoforged/neoforge/client/event/InputEvent$InteractionKeyMappingTriggered;"))
    private InputEvent.InteractionKeyMappingTriggered uwu$dawnpetalMiss(InputEvent.InteractionKeyMappingTriggered click) {
        var mc = (Minecraft)(Object)this;
        if (!click.isCanceled() && mc.player != null && mc.getCameraEntity() == mc.player && mc.hitResult != null
                && mc.hitResult.getType() == HitResult.Type.MISS && DawnpetalVanillaReach.click(mc.player)) {
            click.setCanceled(true);
        }
        return click;
    }
}
