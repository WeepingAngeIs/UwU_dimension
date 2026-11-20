package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uwu.dimension.lush.LushGeodes;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CommonHooks.class, remap = false)
abstract class LushGeodeRollbackMixin {
    @WrapOperation(method = "onPlaceItemIntoWorld", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/util/BlockSnapshot;restore(I)Z"))
    private static boolean uwu$restoreCanceledMark(BlockSnapshot snapshot, int flags, Operation<Boolean> original) {
        boolean restored = original.call(snapshot, flags);
        // A no-op restore can still leave the right block state with the wrong geode mark.
        ((LushGeodes.RollbackSnapshot) snapshot).uwu$restoreGeodeMark();
        return restored;
    }
}
