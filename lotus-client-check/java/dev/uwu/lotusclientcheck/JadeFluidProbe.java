package dev.uwu.lotusclientcheck;

import dev.uwu.dimension.ReefLife;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.fml.ModList;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.impl.ObjectDataCenter;
import snownee.jade.overlay.WailaTickHandler;

/** Aims Jade's normal crosshair overlay at liquid and vine targets in a disposable world. */
final class JadeFluidProbe {
    private static final ResourceKey<Biome> LANTERN_GROTTO = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("uwudimension", "lantern_grotto"));

    private record Sample(ResourceKey<Level> dimension, BlockState state, String title, boolean flowing,
                          ResourceKey<Biome> biome) {
        Sample(ResourceKey<Level> dimension, BlockState state, String title, boolean flowing) {
            this(dimension, state, title, flowing, null);
        }

        boolean vine() { return biome != null; }
        boolean ripe() { return vine() && state.getValue(CaveVines.BERRIES); }
        boolean cavePalette() {
            return ReefLife.REACH.equals(dimension) && (Biomes.LUSH_CAVES.equals(biome) || LANTERN_GROTTO.equals(biome));
        }
    }
    private record Target(BlockPos block, Vec3 feet, float pitch) {}

    private final List<Sample> samples = List.of(
            new Sample(Level.OVERWORLD, Blocks.WATER.defaultBlockState(), "Water", false),
            new Sample(Level.OVERWORLD, Blocks.LAVA.defaultBlockState(), "Lava", false),
            vine(Level.OVERWORLD, Biomes.LUSH_CAVES, false, true, false),
            new Sample(ReefLife.REACH, Blocks.WATER.defaultBlockState(), "UwU Water", false),
            new Sample(ReefLife.REACH, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), "UwU Water", true),
            new Sample(ReefLife.REACH, Blocks.LAVA.defaultBlockState(), "UwU Lava", false),
            new Sample(ReefLife.REACH, Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 2), "UwU Lava", true),
            new Sample(ReefLife.REACH, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true),
                    "Oak Slab", false),
            new Sample(ReefLife.REACH, Blocks.STONE.defaultBlockState(), "Stone", false),
            vine(ReefLife.REACH, Biomes.LUSH_CAVES, false, true, true),
            vine(ReefLife.REACH, Biomes.LUSH_CAVES, false, false, false),
            vine(ReefLife.REACH, LANTERN_GROTTO, true, true, true),
            vine(ReefLife.REACH, LANTERN_GROTTO, true, false, false),
            vine(ReefLife.REACH, Biomes.CHERRY_GROVE, true, true, false));

    private int next;
    private long deadline;
    private boolean preparing;
    private boolean harvesting;
    private volatile boolean harvested;
    private volatile Target target;
    private volatile Throwable serverFailure;
    private String latestTitle = "";

    JadeFluidProbe() {
        require(ModList.get().isLoaded("jade"), "The pinned Jade overlay must be loaded");
        var config = IWailaConfig.get().getGeneral();
        config.setDisplayTooltip(true);
        config.setDisplayBlocks(true);
        config.setDisplayMode(IWailaConfig.DisplayMode.TOGGLE);
        config.setDisplayFluids(IWailaConfig.FluidMode.ANY);
    }

    boolean tick(Minecraft client) {
        var player = client.player;
        if (player == null) return false;
        var sample = samples.get(next);
        if (!preparing) {
            prepare(client, sample);
            return false;
        }
        if (serverFailure != null) throw new AssertionError("Could not prepare Jade sample", serverFailure);
        require(System.nanoTime() < deadline, "Jade did not display " + sample.title + " for "
                + sample.state + " at " + target + "; last title=" + latestTitle);
        var expected = target;
        if (expected == null || client.level == null || !sample.dimension.equals(client.level.dimension())
                || player.position().distanceToSqr(expected.feet) > .04) return false;

        if (harvesting) {
            if (!harvested) return false;
            var after = client.level.getBlockState(expected.block);
            if (!after.is(sample.state.getBlock()) || after.getValue(CaveVines.BERRIES)) return false;
            require(after.getLightEmission() == 0, "Harvested vine still emits block light");
            return advance();
        }

        // Jade's real tick trace reads the camera entity's rotations.
        player.setYRot(-90);
        player.setXRot(expected.pitch);
        if (!(ObjectDataCenter.get() instanceof BlockAccessor block) || !sample.dimension.equals(block.getLevel().dimension())
                || !block.getPosition().equals(expected.block) || !block.getBlockState().is(sample.state.getBlock())
                || (sample.flowing && block.getBlockState().getFluidState().isSource())) return false;

        if (sample.vine() && (!block.getLevel().getBiome(expected.block).is(sample.biome)
                || block.getBlockState().getValue(CaveVines.BERRIES) != sample.ripe())) return false;

        var overlay = WailaTickHandler.instance().rootElement;
        if (overlay == null) return false;
        var tooltip = overlay.getTooltip();
        latestTitle = tooltip.getMessage(JadeIds.CORE_OBJECT_NAME);
        if (!sample.title.equals(latestTitle)) return false;
        require("Minecraft".equals(tooltip.getMessage(JadeIds.CORE_MOD_NAME)), "Jade's registry attribution changed");
        System.out.println("JADE TARGET " + sample.dimension.location() + " " + sample.state + " -> " + latestTitle);
        if (sample.vine()) {
            checkVineModel(client, expected.block, block.getBlockState(), sample);
            if (sample.ripe() && sample.cavePalette()) {
                harvest(client, expected.block);
                return false;
            }
        }
        return advance();
    }

    private boolean advance() {
        next++;
        preparing = false;
        harvesting = false;
        harvested = false;
        if (next != samples.size()) return false;
        System.out.println("JADE REACH FLUID CAPTIONS PASS real crosshair targets; source, flowing, overworld and solid controls");
        System.out.println("JADE REACH BERRY CAPTIONS PASS ripe tip and body, bare controls and biome/dimension boundaries");
        System.out.println("CAVE VINE MODELS AND HARVEST PASS real baked sprites and vanilla Glow Berries drops with light 14 -> 0");
        return true;
    }

    private static Sample vine(ResourceKey<Level> dimension, ResourceKey<Biome> biome, boolean middle,
                               boolean ripe, boolean expectedAlias) {
        var block = middle ? Blocks.CAVE_VINES_PLANT : Blocks.CAVE_VINES;
        var state = block.defaultBlockState().setValue(CaveVines.BERRIES, ripe);
        if (!middle) state = state.setValue(BlockStateProperties.AGE_25, 25);
        var vanillaName = block.getName().getString();
        require(!"UwU Berries".equals(vanillaName), "Vanilla vine localization was changed globally");
        return new Sample(dimension, state, expectedAlias ? "UwU Berries" : vanillaName, false, biome);
    }

    private static void checkVineModel(Minecraft client, BlockPos pos, BlockState state, Sample sample) {
        require(state.getLightEmission() == (sample.ripe() ? 14 : 0), "Unexpected vanilla vine light emission");
        var model = client.getBlockRenderer().getBlockModel(state);
        var data = model.getModelData(client.level, pos, state, ModelData.EMPTY);
        var sprites = model.getQuads(state, null, RandomSource.create(42), data, null).stream()
                .map(quad -> quad.getSprite().contents().name().toString()).collect(Collectors.toSet());
        String texture = state.is(Blocks.CAVE_VINES) ? "cave_vines" : "cave_vines_plant";
        if (sample.ripe()) texture += "_lit";
        Set<String> expected;
        if (sample.cavePalette()) {
            var path = "uwudimension:block/uwu_cave/" + texture;
            expected = sample.ripe() ? Set.of(path + "_foliage", path + "_glow") : Set.of(path);
        } else {
            expected = Set.of("minecraft:block/" + texture);
        }
        require(sprites.equals(expected), "Unexpected baked vine sprites for " + sample + ": " + sprites);
        System.out.println("CAVE VINE MODEL " + sample.dimension.location() + " " + sample.biome.location()
                + " " + state + " sprites=" + sprites + " emission=" + state.getLightEmission());
    }

    private void harvest(Minecraft client, BlockPos pos) {
        harvesting = true;
        var server = client.getSingleplayerServer();
        UUID playerId = client.player.getUUID();
        server.execute(() -> {
            try {
                var owner = server.getPlayerList().getPlayer(playerId);
                var world = owner.serverLevel();
                var before = world.getBlockState(pos);
                require(before.getLightEmission() == 14 && before.getValue(CaveVines.BERRIES), "Vine lost ripe state before harvest");
                var nearby = new AABB(pos).inflate(2);
                var existing = world.getEntitiesOfClass(ItemEntity.class, nearby).stream()
                        .map(ItemEntity::getUUID).collect(Collectors.toSet());
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false);
                require(before.useWithoutItem(world, owner, hit).consumesAction(), "Vanilla vine harvest did not accept");
                var after = world.getBlockState(pos);
                require(after.is(before.getBlock()) && !after.getValue(CaveVines.BERRIES) && after.getLightEmission() == 0,
                        "Harvest changed the block type or left fruit/light");
                var drops = world.getEntitiesOfClass(ItemEntity.class, nearby, entity -> !existing.contains(entity.getUUID()));
                require(drops.size() == 1 && drops.getFirst().getItem().is(Items.GLOW_BERRIES)
                        && drops.getFirst().getItem().getCount() == 1, "Harvest did not drop exactly one vanilla Glow Berries");
                require("Glow Berries".equals(drops.getFirst().getItem().getHoverName().getString()), "Glow Berries item title changed");
                System.out.println("CAVE VINE HARVEST " + before + " -> " + after + " drop=minecraft:glow_berries");
                harvested = true;
            } catch (Throwable failure) {
                serverFailure = failure;
            }
        });
    }

    private static String coords(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private void prepare(Minecraft client, Sample sample) {
        preparing = true;
        target = null;
        latestTitle = "";
        deadline = System.nanoTime() + 60_000_000_000L;
        var server = client.getSingleplayerServer();
        UUID playerId = client.player.getUUID();
        server.execute(() -> {
            try {
                var owner = server.getPlayerList().getPlayer(playerId);
                var world = server.getLevel(sample.dimension);
                require(owner != null && world != null, "Missing fixture player or dimension");
                BlockPos floor = sample.dimension.equals(Level.OVERWORLD)
                        ? owner.blockPosition().above(6)
                        : new BlockPos(8, 92, 8);
                if (sample.vine() && sample.dimension.equals(Level.OVERWORLD)) {
                    floor = new BlockPos((floor.getX() & ~15) + 6, floor.getY(), (floor.getZ() & ~15) + 8);
                }
                BlockPos hit = floor.east(2);
                for (int dx = -1; dx <= 3; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = 1; dy <= 4; dy++) {
                            world.setBlockAndUpdate(floor.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                world.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                for (var wall : List.of(hit.below(), hit.west(), hit.east(), hit.south(), hit.north(),
                        hit.north(2), hit.north().below(), hit.north().west(), hit.north().east())) {
                    world.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                }
                if (sample.flowing) world.setBlockAndUpdate(hit.north(), sample.state.getBlock().defaultBlockState());
                if (sample.vine()) {
                    world.setBlockAndUpdate(hit.west(), Blocks.AIR.defaultBlockState());
                    world.setBlockAndUpdate(hit.above(), Blocks.STONE.defaultBlockState());
                    BlockPos from = hit.offset(-5, -5, -5), to = hit.offset(5, 5, 5);
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(world).withSuppressedOutput(),
                            "fillbiome " + coords(from) + " " + coords(to) + " " + sample.biome.location());
                    require(world.getBiome(hit).is(sample.biome), "Failed to set fixture biome " + sample.biome);
                }
                world.setBlockAndUpdate(hit, sample.state);
                if (sample.state.is(Blocks.CAVE_VINES_PLANT)) {
                    world.setBlockAndUpdate(hit.below(), Blocks.CAVE_VINES.defaultBlockState().setValue(BlockStateProperties.AGE_25, 25));
                }
                Vec3 feet = Vec3.atBottomCenterOf(floor.above());
                double targetHeight = sample.vine() ? .33 : .55;
                float pitch = (float)Math.toDegrees(Math.atan2(feet.y + owner.getEyeHeight() - hit.getY() - targetHeight, 2));
                require(owner.teleportTo(world, feet.x, feet.y, feet.z, Set.of(), -90, pitch), "Teleport to Jade target failed");
                target = new Target(hit, feet, pitch);
            } catch (Throwable failure) {
                serverFailure = failure;
            }
        });
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
