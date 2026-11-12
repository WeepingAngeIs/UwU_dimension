package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Three original fruit trees with harvestable crowns, custom wood, and dimension-only saplings. */
public final class PantryTrees {
    private static final String MOD = "uwudimension";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD);
    private static final List<Supplier<? extends Item>> BUILDING_BLOCKS = new ArrayList<>();
    private static final List<Supplier<? extends Item>> SAPLINGS = new ArrayList<>();

    private static final DeferredBlock<RotatedPillarBlock> HEARTBLOOM_LOG = log("heartbloom_log", MapColor.COLOR_PINK);
    private static final DeferredBlock<FruitingLeavesBlock> HEARTBLOOM_LEAVES = leaves(
            "heartbloom_leaves", MapColor.COLOR_PINK, PantryPlants.ingredient("heartberry"));
    private static final DeferredBlock<DimensionSaplingBlock> HEARTBLOOM_SAPLING = sapling(
            "heartbloom_sapling", "heartbloom_tree", MapColor.COLOR_PINK);

    private static final DeferredBlock<RotatedPillarBlock> STARPETAL_LOG = log("starpetal_log", MapColor.COLOR_ORANGE);
    private static final DeferredBlock<FruitingLeavesBlock> STARPETAL_LEAVES = leaves(
            "starpetal_leaves", MapColor.COLOR_PINK, PantryPlants.ingredient("starfruit"));
    private static final DeferredBlock<DimensionSaplingBlock> STARPETAL_SAPLING = sapling(
            "starpetal_sapling", "starpetal_tree", MapColor.COLOR_PINK);

    private static final DeferredBlock<RotatedPillarBlock> MOONBERRY_LOG = log("moonberry_log", MapColor.COLOR_PURPLE);
    private static final DeferredBlock<FruitingLeavesBlock> MOONBERRY_LEAVES = leaves(
            "moonberry_leaves", MapColor.COLOR_PURPLE, PantryPlants.ingredient("moonberry"));
    private static final DeferredBlock<DimensionSaplingBlock> MOONBERRY_SAPLING = sapling(
            "moonberry_sapling", "moonberry_tree", MapColor.COLOR_PURPLE);

    private PantryTrees() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(PantryTrees::addToCreativeTabs);
    }

    private static DeferredBlock<RotatedPillarBlock> log(String id, MapColor mapColor) {
        DeferredBlock<RotatedPillarBlock> block = BLOCKS.register(id, () -> new RotatedPillarBlock(
                BlockBehaviour.Properties.of().mapColor(mapColor).strength(2.2F).sound(SoundType.WOOD)));
        BUILDING_BLOCKS.add(ITEMS.registerSimpleBlockItem(id, block));
        return block;
    }

    private static DeferredBlock<FruitingLeavesBlock> leaves(String id, MapColor mapColor,
            Supplier<? extends Item> fruit) {
        DeferredBlock<FruitingLeavesBlock> block = BLOCKS.register(id, () -> new FruitingLeavesBlock(
                BlockBehaviour.Properties.of().mapColor(mapColor).strength(.25F).randomTicks()
                        .sound(SoundType.GRASS).noOcclusion(), fruit));
        BUILDING_BLOCKS.add(ITEMS.registerSimpleBlockItem(id, block));
        return block;
    }

    private static DeferredBlock<DimensionSaplingBlock> sapling(String id, String configuredTree, MapColor mapColor) {
        ResourceKey<ConfiguredFeature<?, ?>> tree = ResourceKey.create(Registries.CONFIGURED_FEATURE,
                UwUDimension.id(configuredTree));
        TreeGrower grower = new TreeGrower("uwudimension:" + configuredTree, Optional.empty(), Optional.of(tree),
                Optional.empty());
        DeferredBlock<DimensionSaplingBlock> block = BLOCKS.register(id, () -> new DimensionSaplingBlock(grower,
                BlockBehaviour.Properties.of().mapColor(mapColor).noCollission().instabreak().randomTicks()
                        .sound(SoundType.GRASS)));
        SAPLINGS.add(ITEMS.registerSimpleBlockItem(id, block));
        return block;
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) BUILDING_BLOCKS.forEach(item -> event.accept(item.get()));
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) SAPLINGS.forEach(item -> event.accept(item.get()));
    }
}
