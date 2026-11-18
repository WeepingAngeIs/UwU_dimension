package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.WeaponPresentation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** The server also resolves this pose for observers watching the player. */
@Pseudo
@Mixin(targets = "net.bettercombat.logic.PlayerAttackHelper", remap = false)
abstract class VesperRestPoseMixin {
    // The main-hand pose() is read once to test for null, then again for its value.
    @ModifyExpressionValue(method = "poseForPlayer", at = @At(value = "INVOKE",
            target = "Lnet/bettercombat/api/WeaponAttributes;pose()Ljava/lang/String;", ordinal = 1), remap = false)
    private static String uwu$relaxEmptyHand(String original, Player player) {
        if (!"simplyswords:pose_soulrender".equals(original) || !player.getOffhandItem().isEmpty()
                || !player.getMainHandItem().is(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get())) {
            return original;
        }
        if (player.level().isClientSide() && WeaponPresentation.isFirstPersonViewer(player)) return original;
        return "uwudimension:vesper_single_hand_rest";
    }
}
