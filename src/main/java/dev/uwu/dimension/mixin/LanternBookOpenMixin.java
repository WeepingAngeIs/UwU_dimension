package dev.uwu.dimension.mixin;

import dev.uwu.dimension.LanternStorybookClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep vanilla written/writable books on their own reader/editor paths. */
@Mixin(LocalPlayer.class)
abstract class LanternBookOpenMixin {
    @Inject(method = "openItemGui", at = @At("HEAD"), cancellable = true)
    private void uwu$liftMarkedBook(ItemStack book, InteractionHand hand, CallbackInfo callback) {
        if (LanternStorybookClient.openHeld(book, hand)) callback.cancel();
    }
}
