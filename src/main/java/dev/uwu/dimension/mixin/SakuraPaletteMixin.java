package dev.uwu.dimension.mixin;

import java.util.concurrent.CompletableFuture;
import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.SakuraMaterials;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatusTasks.class)
public abstract class SakuraPaletteMixin {
    @Inject(method="generateSpawn",at=@At("HEAD"))
    private static void uwu$colorFreshTerrain(WorldGenContext context,ChunkStep step,StaticCache2D<?> cache,ChunkAccess chunk,
                                             CallbackInfoReturnable<CompletableFuture<ChunkAccess>> ci) {
        if(context.level().dimension().equals(ReefLife.REACH)) SakuraMaterials.finish(chunk);
    }
}
