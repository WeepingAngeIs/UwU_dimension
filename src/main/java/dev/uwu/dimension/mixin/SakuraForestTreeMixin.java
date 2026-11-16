package dev.uwu.dimension.mixin;
import dev.uwu.dimension.SakuraForest;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(TreeFeature.class)
abstract class SakuraForestTreeMixin {
    @WrapMethod(method="place")
    private boolean uwu$authoredOnly(FeaturePlaceContext<TreeConfiguration> context,Operation<Boolean> original){
        if(SakuraForest.target(context.level(),context.origin()))return false;
        boolean prior=SakuraForest.enterForeign();
        try{return original.call(context);}finally{SakuraForest.leaveForeign(prior);}
    }
}
