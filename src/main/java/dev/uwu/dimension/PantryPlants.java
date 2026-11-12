package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Dimension-bound crops and the custom pantry ingredients harvested or processed from them. */
public final class PantryPlants {
    private static final String MOD = "uwudimension";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD);
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MOD);
    private static final List<Supplier<? extends Item>> CREATIVE_ITEMS = new ArrayList<>();
    private static final Map<String, Supplier<? extends CropBlock>> CROPS = Map.of(
            "heartberry_crop", crop("heartberry_crop", "heartberry_seeds"),
            "sakura_rice_crop", crop("sakura_rice_crop", "sakura_rice_seeds"),
            "starfruit_crop", crop("starfruit_crop", "starfruit_seeds"),
            "frostpetal_crop", crop("frostpetal_crop", "frostpetal_seeds"),
            "sweetbean_crop", crop("sweetbean_crop", "sweetbean_seeds"),
            "glowginger_crop", crop("glowginger_crop", "glowginger_seeds"),
            "vanilla_bean_crop", crop("vanilla_bean_crop", "vanilla_bean_seeds"));

    static {
        FEATURES.register("pantry_garden", PantryGarden::new);
        for (String id : List.of(
                "heartberry", "sakura_rice", "starfruit", "frostpetal", "sweetbean_pods", "glowginger",
                "vanilla_bean", "sakura_flour", "heartberry_jam", "shaved_ice", "pink_shaved_ice",
                "chocolate_syrup", "sweetbean_paste", "maple_syrup", "petal_sugar", "vanilla_extract")) {
            var item = ITEMS.registerSimpleItem(id, new Item.Properties());
            CREATIVE_ITEMS.add(item);
        }
    }

    private PantryPlants() {}

    private static DeferredBlock<DimensionCropBlock> crop(String blockId, String seedId) {
        DeferredBlock<DimensionCropBlock> block = BLOCKS.register(blockId, () -> new DimensionCropBlock(
                BlockBehaviour.Properties.of().noCollission().instabreak().randomTicks().sound(SoundType.CROP)));
        var seed = ITEMS.registerSimpleBlockItem(seedId, block);
        CREATIVE_ITEMS.add(seed);
        return block;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        FEATURES.register(bus);
        bus.addListener(PantryPlants::addToCreativeTab);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            CREATIVE_ITEMS.forEach(item -> event.accept(item.get()));
        }
    }

    private static final class PantryGarden extends Feature<NoneFeatureConfiguration> {
        private PantryGarden() {
            super(NoneFeatureConfiguration.CODEC);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            WorldGenLevel level = context.level();
            Level actualLevel = level.getLevel();
            if (!ReefLife.isReach(actualLevel)) return false;

            RandomSource random = context.random();
            BlockPos origin = context.origin();
            int minX = origin.getX() >> 4 << 4;
            int minZ = origin.getZ() >> 4 << 4;
            ResourceLocation biome = level.getBiome(origin).unwrapKey()
                    .map(key -> key.location()).orElse(UwUDimension.id("ribbon_bluffs"));
            int planted = 0;
            int clusters = 2 + random.nextInt(2);
            for (int cluster = 0; cluster < clusters; cluster++) {
                int centerX = minX + 3 + random.nextInt(10);
                int centerZ = minZ + 3 + random.nextInt(10);
                CropBlock crop = chooseCrop(biome, random);
                for (int attempt = 0; attempt < 7; attempt++) {
                    int x = Math.max(minX, Math.min(minX + 15, centerX + random.nextInt(5) - 2));
                    int z = Math.max(minZ, Math.min(minZ + 15, centerZ + random.nextInt(5) - 2));
                    int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                    BlockPos ground = new BlockPos(x, y - 1, z);
                    BlockPos plant = ground.above();
                    BlockState soil = level.getBlockState(ground);
                    if (!(soil.is(BlockTags.DIRT) || soil.is(Blocks.FARMLAND))) continue;
                    if (!level.getBlockState(plant).isAir() || level.getMaxLocalRawBrightness(plant) < 8) continue;
                    if (level.setBlock(plant, crop.defaultBlockState().setValue(CropBlock.AGE, random.nextInt(0, 5)), 2)) {
                        planted++;
                    }
                }
            }
            return planted > 0;
        }

        private static CropBlock chooseCrop(ResourceLocation biome, RandomSource random) {
            String path = biome.getPath();
            if (path.equals("lantern_fens")) {
                return choose(random, "sakura_rice_crop", "frostpetal_crop", "glowginger_crop", "heartberry_crop");
            }
            if (path.equals("velvet_highlands")) {
                return choose(random, "sweetbean_crop", "vanilla_bean_crop", "starfruit_crop");
            }
            return choose(random, "heartberry_crop", "starfruit_crop", "vanilla_bean_crop");
        }

        private static CropBlock choose(RandomSource random, String... ids) {
            Supplier<? extends CropBlock> crop = CROPS.get(ids[random.nextInt(ids.length)]);
            return crop.get();
        }
    }
}
