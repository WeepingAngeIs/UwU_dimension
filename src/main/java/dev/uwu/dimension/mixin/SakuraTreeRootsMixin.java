package dev.uwu.dimension.mixin;

import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.SakuraTreeRoots;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.trunkplacers.GiantTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.DarkOakTrunkPlacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TreeFeature.class)
public abstract class SakuraTreeRootsMixin {
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void uwu$dryBeforeTrunkOrDecorator(FeaturePlaceContext<TreeConfiguration> ctx,CallbackInfoReturnable<Boolean> ci){
        if(!ctx.level().getLevel().dimension().equals(ReefLife.REACH))return;
        var trunk=ctx.config().trunkPlacer;
        int width=trunk instanceof GiantTrunkPlacer || trunk instanceof DarkOakTrunkPlacer?2:1;
        if(!SakuraTreeRoots.dry(ctx.level(),ctx.origin(),width))ci.setReturnValue(false);
    }
}
