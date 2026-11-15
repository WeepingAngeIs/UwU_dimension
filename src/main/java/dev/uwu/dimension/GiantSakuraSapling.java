package dev.uwu.dimension;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One sapling grows the same giant 2x2-trunk sakura as the village landmark. */
public final class GiantSakuraSapling extends Feature<NoneFeatureConfiguration> {
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,"uwudimension");
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("uwudimension");
    private static final ResourceKey<ConfiguredFeature<?,?>> TREE=ResourceKey.create(Registries.CONFIGURED_FEATURE,UwUDimension.id("giant_sakura_tree"));
    private static final TreeGrower GROWER=new TreeGrower("uwudimension:giant_sakura",Optional.empty(),Optional.of(TREE),Optional.empty());
    public static final net.neoforged.neoforge.registries.DeferredBlock<SaplingBlock> SAPLING=BLOCKS.register("giant_sakura_sapling",GuardedSapling::new);
    static { FEATURES.register("giant_sakura_tree",GiantSakuraSapling::new); }
    private static final java.util.function.Supplier<Item> ITEM=ITEMS.register("giant_sakura_sapling",()->new BlockItem(SAPLING.get(),new Item.Properties()) {
        @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> tips,TooltipFlag flag) {
            tips.add(Component.literal("One sapling grows a giant 2×2-trunk sakura.").withStyle(ChatFormatting.GRAY));
            tips.add(Component.literal("Needs flat soil, open space, and loaded chunks.").withStyle(ChatFormatting.GRAY));
        }
    });
    public GiantSakuraSapling(){super(NoneFeatureConfiguration.CODEC);}
    private static final class GuardedSapling extends SaplingBlock {
        GuardedSapling(){super(GROWER,BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_SAPLING));}
        @Override public void advanceTree(net.minecraft.server.level.ServerLevel level,BlockPos pos,
                                          net.minecraft.world.level.block.state.BlockState state,net.minecraft.util.RandomSource random) {
            // Vanilla TreeGrower inspects nearby flowers before calling our feature.
            // Guard its reads too, including bonemeal, without forcing any chunk loads.
            int minX=Math.floorDiv(pos.getX()-17,16),maxX=Math.floorDiv(pos.getX()+17,16);
            int minZ=Math.floorDiv(pos.getZ()-17,16),maxZ=Math.floorDiv(pos.getZ()+17,16);
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)
                if(!level.hasChunkAt(new BlockPos(x<<4,pos.getY(),z<<4)))return;
            super.advanceTree(level,pos,state,random);
        }
    }
    public static void register(IEventBus bus){
        FEATURES.register(bus);BLOCKS.register(bus);ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.NATURAL_BLOCKS)e.accept(ITEM.get());});
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level=context.level();var origin=context.origin();var server=level.getLevel();
        var plan=GiantSakuraTree.plan(20+context.random().nextInt(6),context.random().nextLong());
        // Validate all required chunks before any state reads, so growth never loads unexplored terrain.
        var checkedChunks=new java.util.HashSet<Long>();
        for(var delta:plan.keySet()){
            BlockPos at=origin.offset(delta);
            if(level.isOutsideBuildHeight(at) || !server.getWorldBorder().isWithinBounds(at))return false;
            if(checkedChunks.add(net.minecraft.world.level.ChunkPos.asLong(at)) && !server.hasChunkAt(at))return false;
        }
        for(int x=0;x<2;x++)for(int z=0;z<2;z++){
            var soil=origin.offset(x,-1,z);var state=level.getBlockState(soil);
            if(!state.is(BlockTags.DIRT) || !state.isFaceSturdy(level,soil,Direction.UP))return false;
        }
        // Never overwrite liquids, block entities, logs, leaves, blocks or player structures.
        for(var delta:plan.keySet()){
            var at=origin.offset(delta);var existing=level.getBlockState(at);
            if(existing.hasBlockEntity() || !existing.getFluidState().isEmpty())return false;
            if(!existing.isAir() && !existing.is(Blocks.SHORT_GRASS) && !existing.is(Blocks.TALL_GRASS)
                    && !existing.is(Blocks.FERN) && !existing.is(Blocks.LARGE_FERN) && !existing.is(Blocks.PINK_PETALS))return false;
        }
        for(var entry:plan.entrySet())level.setBlock(origin.offset(entry.getKey()),entry.getValue(),3);
        return true;
    }
}
