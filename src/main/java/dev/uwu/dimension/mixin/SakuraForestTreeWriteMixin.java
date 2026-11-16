package dev.uwu.dimension.mixin;
import dev.uwu.dimension.SakuraForest;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Covers neighboring-biome branches and all tree decorators, without deleting old blocks. */
@Mixin({Level.class,WorldGenRegion.class})
abstract class SakuraForestTreeWriteMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void uwu$keepBorder(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> result){
        if(SakuraForest.foreign()&&(Object)this instanceof WorldGenLevel level&&SakuraForest.denyForeign(level,pos))result.setReturnValue(false);
    }
}
