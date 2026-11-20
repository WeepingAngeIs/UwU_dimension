package dev.uwu.dimension.mixin;
import dev.uwu.dimension.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SaplingBlock.class)
abstract class SakuraForestSaplingMixin {
    @Inject(method="advanceTree",at=@At("HEAD"),cancellable=true)
    private void uwu$onlyAuthoredSapling(ServerLevel level,BlockPos pos,BlockState state,RandomSource random,CallbackInfo callback){
        if(!state.is(GiantSakuraSapling.SAPLING.get())&&SakuraForest.target(level,pos))callback.cancel();
    }
}
