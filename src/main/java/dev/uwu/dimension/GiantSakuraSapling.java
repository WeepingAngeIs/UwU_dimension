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

/** One sapling grows the same authored sakura as the village landmark. */
public final class GiantSakuraSapling extends Feature<NoneFeatureConfiguration> {
    private static final ThreadLocal<net.minecraft.server.level.ServerPlayer> GARDENER=new ThreadLocal<>();
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,"uwudimension");
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("uwudimension");
    private static final ResourceKey<ConfiguredFeature<?,?>> TREE=ResourceKey.create(Registries.CONFIGURED_FEATURE,UwUDimension.id("giant_sakura_tree"));
    private static final TreeGrower GROWER=new TreeGrower("uwudimension:giant_sakura",Optional.empty(),Optional.of(TREE),Optional.empty());
    public static final net.neoforged.neoforge.registries.DeferredBlock<SaplingBlock> SAPLING=BLOCKS.register("giant_sakura_sapling",GuardedSapling::new);
    static { FEATURES.register("giant_sakura_tree",GiantSakuraSapling::new); }
    private static final java.util.function.Supplier<Item> ITEM=ITEMS.register("giant_sakura_sapling",()->new BlockItem(SAPLING.get(),new Item.Properties()) {
        @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> tips,TooltipFlag flag) {
            tips.add(Component.literal("One sapling grows Anya's finished giant sakura.").withStyle(ChatFormatting.GRAY));
            tips.add(Component.literal("Needs 5x5 flat soil, open space, and loaded chunks.").withStyle(ChatFormatting.GRAY));
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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SakuraForest::bonemeal);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SakuraForest::growth);
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.NATURAL_BLOCKS)e.accept(ITEM.get());});
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.bus.api.EventPriority.LOWEST,false,GiantSakuraSapling::bonemeal);
    }

    private static void bonemeal(net.neoforged.neoforge.event.entity.player.BonemealEvent event) {
        if(!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || !event.getState().is(SAPLING.get()) || !event.isValidBonemealTarget())return;
        // Scope the actual gardener to this synchronous growth only, never retain an owner/player.
        var prior=GARDENER.get();
        try {
            if(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)GARDENER.set(player);
            else GARDENER.remove();
            var block=(net.minecraft.world.level.block.BonemealableBlock)event.getState().getBlock();
            if(block.isBonemealSuccess(level,level.random,event.getPos(),event.getState()))
                block.performBonemeal(level,level.random,event.getPos(),event.getState());
            event.getStack().shrink(1);
            event.setSuccessful(true);
        } finally {
            if(prior==null)GARDENER.remove();else GARDENER.set(prior);
        }
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
        // Check the complete crown as well as the trunk before touching any block.
        for(var delta:plan.keySet())if(!SakuraAccess.mayGrow(server,GARDENER.get(),origin.offset(delta)))return false;
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
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
        boolean complete=true;
        for(var entry:plan.entrySet()){
            var pos=origin.offset(entry.getKey());
            if(!level.setBlock(pos,entry.getValue(),3)&&!level.getBlockState(pos).equals(entry.getValue()))complete=false;
        }
        if(complete)SakuraAchievements.award(GARDENER.get(),"garden/giant");
        return true;
    }
}
