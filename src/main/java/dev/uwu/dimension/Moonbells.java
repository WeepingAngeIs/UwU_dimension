package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Dry two-block flower. Only its upper bells emit steady, server-side block light. */
public final class Moonbells {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    public static final net.neoforged.neoforge.registries.DeferredBlock<Block> PETAL = BLOCKS.register("petal_moonbell", MoonbellBlock::new);
    public static final net.neoforged.neoforge.registries.DeferredBlock<Block> DUSK = BLOCKS.register("dusk_moonbell", MoonbellBlock::new);
    public static final java.util.function.Supplier<Item> PETAL_ITEM = ITEMS.register("petal_moonbell", () -> new DoubleHighBlockItem(PETAL.get(),new Item.Properties()));
    public static final java.util.function.Supplier<Item> DUSK_ITEM = ITEMS.register("dusk_moonbell", () -> new DoubleHighBlockItem(DUSK.get(),new Item.Properties()));
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
                event.accept(PETAL_ITEM.get());event.accept(DUSK_ITEM.get());
            }
        });
    }
    private static boolean soil(BlockState state, BlockGetter level, BlockPos at) {
        return state.is(BlockTags.DIRT) && state.getFluidState().isEmpty() && state.isFaceSturdy(level, at, Direction.UP);
    }
    private static final class MoonbellBlock extends DoublePlantBlock {
        MoonbellBlock() {
            super(BlockBehaviour.Properties.ofFullCopy(Blocks.LARGE_FERN).noOcclusion()
                    .lightLevel(state -> state.getValue(HALF) == DoubleBlockHalf.UPPER ? 12 : 0));
        }
        @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos at) { return soil(state,level,at); }
        @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos at, CollisionContext context) {
            return state.getValue(HALF) == DoubleBlockHalf.UPPER ? Block.box(2,0,2,14,12,14) : Block.box(2,0,2,14,16,14);
        }
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            return context.getLevel().getFluidState(context.getClickedPos()).isEmpty()
                    && context.getLevel().getFluidState(context.getClickedPos().above()).isEmpty() ? super.getStateForPlacement(context) : null;
        }
    }

    /** Called only by fresh forest decoration, clipped to the single owning chunk before lighting. */
    static void decorateTree(net.minecraft.server.level.ServerLevel level, ChunkAccess chunk, SakuraForest.Tree tree) {
        var random = net.minecraft.util.RandomSource.create(HeartwaterFeature.Valley.mix(tree.x(),tree.z(),level.getSeed() ^ 0x167bea2dL));
        int clumps = 3 + random.nextInt(3);
        for (int i = 0; i < clumps; i++) {
            double angle = (i + random.nextDouble() * .4) * Math.PI * 2 / clumps;
            int distance = 5 + random.nextInt(8);
            int x = tree.x() + (int)Math.round(Math.cos(angle) * distance);
            int z = tree.z() + (int)Math.round(Math.sin(angle) * distance);
            if ((x >> 4) != chunk.getPos().x || (z >> 4) != chunk.getPos().z) continue;
            int y = ForestHeights.height(level,x,z) + 1;
            BlockPos lower = new BlockPos(x,y,z), upper = lower.above(), ground = lower.below();
            if (y + 1 >= level.getMaxBuildHeight() || !chunk.getBlockState(lower).isAir()
                    || !chunk.getBlockState(upper).isAir() || !soil(chunk.getBlockState(ground),chunk,ground)) continue;
            Block plant = PETAL.get();
            chunk.setBlockState(lower, plant.defaultBlockState().setValue(DoublePlantBlock.HALF,DoubleBlockHalf.LOWER),false);
            chunk.setBlockState(upper, plant.defaultBlockState().setValue(DoublePlantBlock.HALF,DoubleBlockHalf.UPPER),false);
        }
    }
    private Moonbells() {}
}
