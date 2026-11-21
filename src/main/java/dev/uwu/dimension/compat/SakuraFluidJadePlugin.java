package dev.uwu.dimension.compat;

import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.lush.client.LushRenderView;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.IBoxElement;

/** Applies Sakura Reach's local names to the block under Jade's crosshair. */
@WailaPlugin
public final class SakuraFluidJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addTooltipCollectedCallback(SakuraFluidJadePlugin::renameBlock);
    }

    private static void renameBlock(IBoxElement root, Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block) || block.isFakeBlock()
                || !ReefLife.REACH.equals(block.getLevel().dimension())) {
            return;
        }

        var state = block.getBlockState();
        Component name;
        if (state.is(Blocks.WATER)) {
            name = Component.translatable("tooltip.uwudimension.sakura_water");
        } else if (state.is(Blocks.LAVA)) {
            name = Component.translatable("tooltip.uwudimension.sakura_lava");
        } else if ((state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT))
                && state.getValue(CaveVines.BERRIES)
                && LushRenderView.isCaveBiome(block.getLevel().getBiome(block.getPosition()))) {
            name = Component.translatable("tooltip.uwudimension.uwu_berries");
        } else {
            return;
        }
        root.getTooltip().replace(JadeIds.CORE_OBJECT_NAME, IThemeHelper.get().title(name));
    }
}
