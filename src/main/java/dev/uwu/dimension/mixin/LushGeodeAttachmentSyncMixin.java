package dev.uwu.dimension.mixin;

import dev.uwu.dimension.lush.LushGeodes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
abstract class LushGeodeAttachmentSyncMixin {
    @Inject(method = "syncData(Lnet/neoforged/neoforge/attachment/AttachmentType;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void uwu$deferGeodeSync(AttachmentType<?> type, CallbackInfo callback) {
        if (LushGeodes.deferSync((LevelChunk) (Object) this, type)) callback.cancel();
    }
}
