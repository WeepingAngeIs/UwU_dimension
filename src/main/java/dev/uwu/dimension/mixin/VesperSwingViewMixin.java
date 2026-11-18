package dev.uwu.dimension.mixin;

import dev.uwu.dimension.Arsenal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Select a display clip after Better Combat has already chosen the attack and its timing. */
@Mixin(value = AbstractClientPlayer.class, priority = 900)
abstract class VesperSwingViewMixin {
    @Dynamic("Better Combat adds playAttackAnimation to the client player when installed.")
    @ModifyVariable(method = "playAttackAnimation(Ljava/lang/String;Lnet/bettercombat/logic/AnimatedHand;FF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0, remap = false)
    private String uwu$oneHandedVesperView(String animation) {
        var wielder = (AbstractClientPlayer)(Object)this;
        if (!wielder.getOffhandItem().isEmpty()
                || !wielder.getMainHandItem().is(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get())) {
            return animation;
        }
        var client = Minecraft.getInstance();
        if (client.getCameraEntity() == wielder && client.options.getCameraType().isFirstPerson()) return animation;
        return switch (animation) {
            case "bettercombat:two_handed_slash_horizontal_right" -> "bettercombat:one_handed_slash_horizontal_right";
            case "bettercombat:two_handed_slash_horizontal_left" -> "bettercombat:one_handed_slash_horizontal_left";
            default -> animation;
        };
    }
}
