package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.uwu.dimension.SakuraAchievements;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.*;

/** Observe the final ordinary player break result, after protection handlers decide. */
@Mixin(ServerPlayerGameMode.class)
abstract class CropHarvestAdvancementMixin {
    @Shadow @Final protected ServerPlayer player;
    @WrapMethod(method="destroyBlock")
    private boolean uwu$harvested(BlockPos pos,Operation<Boolean> original){
        var level=player.serverLevel();
        var before=level.hasChunkAt(pos)?level.getBlockState(pos):null;
        boolean success=original.call(pos);
        if(success&&before!=null&&!level.getBlockState(pos).equals(before))SakuraAchievements.harvested(player,before);
        return success;
    }
}
