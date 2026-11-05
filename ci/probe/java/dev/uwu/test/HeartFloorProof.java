package dev.uwu.test;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.HeartFloor;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class HeartFloorProof {
    public static void verify(ServerLevel level, BlockPos nearby) {
        BlockPos floor = new BlockPos(nearby.getX(), level.getMaxBuildHeight() - 8, nearby.getZ());
        BlockPos tile = floor.above(), ceiling = tile.above();
        for (var pos : List.of(floor, tile, ceiling))
            require(level.isEmptyBlock(pos), "Expected open sky above field station at " + pos);
        var builder = new FakePlayer(level, new GameProfile(UUID.fromString("1490f267-df3d-44cb-9600-97e1e46c0767"), "Tiler"));
        builder.moveTo(tile.getX()+2, tile.getY(), tile.getZ()+2);
        try {
            for (DyeColor color : DyeColor.values()) {
                Block full = HeartFloor.full(color), slab = HeartFloor.slab(color), rug = HeartFloor.carpet(color);
                ItemStack calcite = new ItemStack(Items.CALCITE), dye = new ItemStack(DyeItem.byColor(color));
                var wool = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(color.getName()+"_wool")));
                craft(level, full, 4, 3, 3, ItemStack.EMPTY,calcite,ItemStack.EMPTY,calcite,dye,calcite,ItemStack.EMPTY,calcite,ItemStack.EMPTY);
                craft(level, slab, 6, 3, 1, new ItemStack(full),new ItemStack(full),new ItemStack(full));
                craft(level, rug, 4, 2, 2, wool,wool,new ItemStack(full),ItemStack.EMPTY);
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(ceiling, Blocks.STONE.defaultBlockState());
                place(builder, floor, Direction.UP, full);
                checkPlaced(level, tile, full, 1, 1);
                level.removeBlock(tile, false);
                place(builder, floor, Direction.UP, slab);
                require(level.getBlockState(tile).getValue(SlabBlock.TYPE)==SlabType.BOTTOM, "Bottom slab");
                checkPlaced(level, tile, slab, .5, 1);
                place(builder, tile, Direction.UP, slab);
                require(level.getBlockState(tile).getValue(SlabBlock.TYPE)==SlabType.DOUBLE, "Merge slabs");
                checkPlaced(level, tile, slab, 1, 2);
                level.removeBlock(tile, false);
                place(builder, ceiling, Direction.DOWN, slab);
                require(level.getBlockState(tile).getValue(SlabBlock.TYPE)==SlabType.TOP, "Top slab");
                level.removeBlock(tile, false);
                level.setBlockAndUpdate(tile, Blocks.WATER.defaultBlockState());
                place(builder, floor, Direction.UP, slab);
                require(level.getBlockState(tile).getValue(SlabBlock.WATERLOGGED), "Waterlogged slab");
                level.setBlockAndUpdate(tile, Blocks.AIR.defaultBlockState());
                place(builder, floor, Direction.UP, rug);
                checkPlaced(level, tile, rug, 1/16D, 1);
                level.removeBlock(floor, false);
                require(!level.getBlockState(tile).is(rug), "Unsupported carpet stayed in the air");
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(tile).inflate(.25),
                        e -> e.getItem().is(rug.asItem()))) item.discard();
            }
        } finally {
            for (var pos : List.of(tile, floor, ceiling)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        System.out.println("HEARTSILL FLOOR PASS | 16 color families, 48 survival recipes, real item placement, slab halves/merge/waterlogging, carpet support and drops");
    }

    private static void craft(ServerLevel level, Block output, int count, int width, int height, ItemStack... stacks) {
        var grid = CraftingInput.of(width, height, Arrays.asList(stacks));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow();
        var crafted = recipe.value().assemble(grid, level.registryAccess());
        require(crafted.is(output.asItem()) && crafted.getCount()==count, "Recipe output " + crafted + " for " + output);
    }

    private static void place(FakePlayer player, BlockPos clicked, Direction face, Block block) {
        var stack = new ItemStack(block);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var contact = Vec3.atCenterOf(clicked).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
        var hit = new BlockHitResult(contact, face, clicked, false);
        var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        require(result.consumesAction(), "Placement " + block + " on " + face + ": " + result);
    }

    private static void checkPlaced(ServerLevel level, BlockPos pos, Block block, double height, int count) {
        BlockState placed = level.getBlockState(pos);
        require(placed.is(block), "Wrong block at " + pos + ": " + placed);
        double top = placed.getShape(level,pos).max(Direction.Axis.Y);
        require(Math.abs(top-height)<.0001, "Unexpected shape " + top);
        var drops = Block.getDrops(placed, level, pos, null);
        require(drops.size()==1 && drops.getFirst().is(block.asItem()) && drops.getFirst().getCount()==count, "Drops for " + placed);
    }

    private static void require(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
