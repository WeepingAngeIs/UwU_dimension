package dev.uwu.dimension.mixin;

import dev.uwu.dimension.lush.LushGeodes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ProtoChunk.class, LevelChunk.class})
abstract class LushGeodeChunkWriteMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void uwu$afterChunkWrite(BlockPos pos, BlockState state, boolean moving,
            CallbackInfoReturnable<BlockState> callback) {
        LushGeodes.afterWrite((ChunkAccess) (Object) this, pos, state, callback.getReturnValue());
    }
}
