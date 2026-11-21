package dev.uwu.dimension.compat;

import dev.uwu.dimension.ReefLife;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.IBoxElement;

/** Applies Sakura Reach's local names to Jade's existing liquid tooltip. */
@WailaPlugin
public final class SakuraFluidJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addTooltipCollectedCallback(SakuraFluidJadePlugin::renameFluid);
    }

    private static void renameFluid(IBoxElement root, Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block) || block.isFakeBlock()
                || !ReefLife.REACH.equals(block.getLevel().dimension())) {
            return;
        }

        Component name;
        if (block.getBlockState().is(Blocks.WATER)) {
            name = Component.translatable("tooltip.uwudimension.sakura_water");
        } else if (block.getBlockState().is(Blocks.LAVA)) {
            name = Component.translatable("tooltip.uwudimension.sakura_lava");
        } else {
            return;
        }
        root.getTooltip().replace(JadeIds.CORE_OBJECT_NAME, IThemeHelper.get().title(name));
    }
}
