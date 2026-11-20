package dev.uwu.dimension.mixin;

import dev.uwu.dimension.lush.LushGeodes;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockSnapshot.class, remap = false)
abstract class LushGeodeSnapshotMixin implements LushGeodes.RollbackSnapshot {
    @Unique private @Nullable LushGeodes.SavedMark uwu$priorMark;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void uwu$captureMark(CallbackInfo ci) {
        BlockSnapshot snapshot = (BlockSnapshot) (Object) this;
        uwu$priorMark = LushGeodes.captureMark(snapshot.getLevel(), snapshot.getPos(), snapshot.getState());
    }

    @Override
    public void uwu$restoreGeodeMark() {
        if (uwu$priorMark != null) uwu$priorMark.restore((BlockSnapshot) (Object) this);
    }
}
