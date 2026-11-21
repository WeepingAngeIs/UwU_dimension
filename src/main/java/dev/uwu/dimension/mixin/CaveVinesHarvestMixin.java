package dev.uwu.dimension.mixin;

import dev.uwu.dimension.lush.UwUCaveBerries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CaveVines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replace only the ripe harvest item; vanilla still owns sound, state, events and regrowth. */
@Mixin(CaveVines.class)
public interface CaveVinesHarvestMixin {
    @Redirect(method = "use", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"))
    private static void uwu$harvestPinkBerry(Level level, BlockPos pos, ItemStack vanilla) {
        Block.popResource(level, pos, UwUCaveBerries.harvest(level, pos, vanilla));
    }
}
