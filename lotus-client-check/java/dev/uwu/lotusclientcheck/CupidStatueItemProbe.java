package dev.uwu.lotusclientcheck;

import dev.uwu.dimension.CupidShrine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class CupidStatueItemProbe {
    private static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath("uwudimension", "cupid_shrine");

    static void verifyServer(ServerPlayer player, BlockPos position) {
        var level = player.serverLevel();
        var item = BuiltInRegistries.ITEM.get(KEY);
        require(item == CupidShrine.BLOCK.get().asItem() && item != Items.AIR, "Missing shrine item");
        var stack = new ItemStack(item);
        var oldMode = player.gameMode.getGameModeForPlayer();
        float oldYaw = player.getYRot();
        BlockPos base = position.below();
        var hit = new BlockHitResult(Vec3.atCenterOf(base).add(0, .5, 0), Direction.UP, base, false);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.ADVENTURE}) {
                player.setGameMode(mode);
                var outcome = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                require(!outcome.consumesAction() && level.getBlockState(position).isAir()
                                && stack.getCount() == 1,
                        "Transferred stack allowed placement in " + mode);
            }

            player.setGameMode(GameType.CREATIVE);
            player.setYRot(0);
            var outcome = player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
            var placed = level.getBlockState(position);
            require(outcome.consumesAction() && placed.is(CupidShrine.BLOCK.get())
                            && placed.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH
                            && level.getBlockEntity(position) != null,
                    "Creative did not place a correctly facing native shrine");
            require(Block.getDrops(placed, level, position, level.getBlockEntity(position)).isEmpty(),
                    "Shrine has a Survival drop");
            require(level.getRecipeManager().getRecipes().stream().noneMatch(r ->
                            r.value().getResultItem(level.registryAccess()).is(item)),
                    "A recipe grants the Creative statue");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
            for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.ADVENTURE}) {
                player.setGameMode(mode);
                require(placed.getDestroyProgress(player, level, position) == 0F, "Shrine accepts mining progress");
                mine(player, position, Action.START_DESTROY_BLOCK);
                mine(player, position, Action.STOP_DESTROY_BLOCK);
                require(level.getBlockState(position).is(CupidShrine.BLOCK.get()), mode + " removed the shrine");
            }

            require(level.removeBlock(base, false) && level.getBlockState(position).is(CupidShrine.BLOCK.get()),
                    "Breaking the support removed the protected shrine");
            player.setGameMode(GameType.CREATIVE);
            mine(player, position, Action.START_DESTROY_BLOCK);
            require(level.getBlockState(position).isAir(), "Creative mining did not remove the shrine");
            System.out.println("CUPID STATUE SERVER PASS creative placement facing and removal, survival/adventure refused, no drop or recipe");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setYRot(oldYaw);
            player.setGameMode(oldMode);
        }
    }

    static void verifyCreativeListing(Minecraft client) {
        var level = client.level;
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
        var item = BuiltInRegistries.ITEM.get(KEY);
        var search = CreativeModeTabs.searchTab();
        require(search.getDisplayItems().stream().filter(stack -> stack.is(item)).count() == 1,
                "Creative search did not list the statue once");
        for (var tab : CreativeModeTabs.allTabs()) {
            if (tab == search) continue;
            boolean uwu = ResourceLocation.fromNamespaceAndPath("uwudimension", "main")
                    .equals(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab));
            require(tab.getDisplayItems().stream().filter(stack -> stack.is(item)).count() == (uwu ? 1 : 0),
                    "Unexpected Creative tab listing");
        }
        var portrait = client.getItemRenderer().getModel(new ItemStack(item), level, null, 0);
        require(portrait.getParticleIcon().contents().name().equals(
                        ResourceLocation.fromNamespaceAndPath("uwudimension", "item/cupid_shrine")),
                "The Creative statue portrait was missing from the item atlas");
        System.out.println("CUPID STATUE CREATIVE LISTING PASS UwU tab, search and baked portrait");
    }

    private static void mine(ServerPlayer player, BlockPos pos, Action action) {
        player.gameMode.handleBlockBreakAction(pos, action, Direction.UP, player.level().getMaxBuildHeight(), 0);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
