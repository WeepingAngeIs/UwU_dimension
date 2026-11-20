package dev.uwu.dimension.mixin;

import java.util.concurrent.CompletableFuture;
import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.SakuraMaterials;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatusTasks.class)
public abstract class SakuraPaletteMixin {
    @Inject(method = "generateSurface", at = @At("RETURN"))
    private static void uwu$finishBedrockFloor(WorldGenContext context, ChunkStep step, StaticCache2D<?> cache,
            ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        if (!context.level().dimension().equals(ReefLife.REACH) || chunk.isUpgrading()
                || chunk.getPersistedStatus().isOrAfter(ChunkStatus.SURFACE)) return;
        if (context.generator() instanceof NoiseBasedChunkGenerator noise && noise.stable(NoiseGeneratorSettings.OVERWORLD)) {
            SakuraMaterials.replaceGeneratedBedrock(chunk, noise.getMinY());
        }
    }

    @Inject(method="generateFeatures",at=@At("RETURN"))
    private static void uwu$authoredForest(WorldGenContext context,ChunkStep step,StaticCache2D<?> cache,ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> ci){
        dev.uwu.dimension.SakuraForest.decorate(context.level(),chunk);
    }
    @Inject(method="generateSpawn",at=@At("HEAD"))
    private static void uwu$colorFreshTerrain(WorldGenContext context,ChunkStep step,StaticCache2D<?> cache,ChunkAccess chunk,
                                             CallbackInfoReturnable<CompletableFuture<ChunkAccess>> ci) {
        if(context.level().dimension().equals(ReefLife.REACH)) SakuraMaterials.finish(chunk);
    }
}
