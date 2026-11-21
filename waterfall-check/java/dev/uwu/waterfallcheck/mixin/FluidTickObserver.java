package dev.uwu.waterfallcheck.mixin;

import dev.uwu.waterfallcheck.WaterfallProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
abstract class FluidTickObserver {
    @Inject(method = "tick", at = @At("HEAD"))
    private void observeNaturalFlow(Level level, BlockPos pos, FluidState fluid, CallbackInfo callback) {
        if (level instanceof ServerLevel server && fluid.is(FluidTags.WATER)) {
            WaterfallProbe.observeWaterTick(server, pos);
        }
    }
}
