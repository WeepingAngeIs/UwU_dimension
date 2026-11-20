package dev.uwu.dimension.mixin;
import dev.uwu.dimension.SakuraForest;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
/** Cobblemon's Apricorn/Saccharine trees do not subclass vanilla TreeFeature. */
@Mixin(ConfiguredFeature.class)
abstract class SakuraForestConfiguredMixin {
    @WrapMethod(method="place")
    private boolean uwu$allTreeGenerators(WorldGenLevel level,ChunkGenerator generator,RandomSource random,BlockPos pos,Operation<Boolean> original){
        var feature=((ConfiguredFeature<?,?>)(Object)this).feature();
        if(!SakuraForest.foreignFeature(feature))return original.call(level,generator,random,pos);
        if(SakuraForest.target(level,pos))return false;
        boolean prior=SakuraForest.enterForeign();
        try{return original.call(level,generator,random,pos);}finally{SakuraForest.leaveForeign(prior);}
    }
}
