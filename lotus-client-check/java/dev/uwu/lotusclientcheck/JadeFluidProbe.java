package dev.uwu.lotusclientcheck;

import dev.uwu.dimension.ReefLife;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.impl.ObjectDataCenter;
import snownee.jade.overlay.WailaTickHandler;

/** Aims Jade's normal crosshair overlay at fluid blocks in two disposable dimensions. */
final class JadeFluidProbe {
    private record Sample(ResourceKey<Level> dimension, BlockState state, String title, boolean flowing) {}
    private record Target(BlockPos block, Vec3 feet, float pitch) {}

    private final List<Sample> samples = List.of(
            new Sample(Level.OVERWORLD, Blocks.WATER.defaultBlockState(), "Water", false),
            new Sample(Level.OVERWORLD, Blocks.LAVA.defaultBlockState(), "Lava", false),
            new Sample(ReefLife.REACH, Blocks.WATER.defaultBlockState(), "UwU Water", false),
            new Sample(ReefLife.REACH, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), "UwU Water", true),
            new Sample(ReefLife.REACH, Blocks.LAVA.defaultBlockState(), "UwU Lava", false),
            new Sample(ReefLife.REACH, Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 2), "UwU Lava", true),
            new Sample(ReefLife.REACH, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true),
                    "Oak Slab", false),
            new Sample(ReefLife.REACH, Blocks.STONE.defaultBlockState(), "Stone", false));

    private int next;
    private long deadline;
    private boolean preparing;
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

        // Jade's real tick trace reads the camera entity's rotations.
        player.setYRot(-90);
        player.setXRot(expected.pitch);
        if (!(ObjectDataCenter.get() instanceof BlockAccessor block) || !sample.dimension.equals(block.getLevel().dimension())
                || !block.getPosition().equals(expected.block) || !block.getBlockState().is(sample.state.getBlock())
                || (sample.flowing && block.getBlockState().getFluidState().isSource())) return false;

        var overlay = WailaTickHandler.instance().rootElement;
        if (overlay == null) return false;
        var tooltip = overlay.getTooltip();
        latestTitle = tooltip.getMessage(JadeIds.CORE_OBJECT_NAME);
        if (!sample.title.equals(latestTitle)) return false;
        require("Minecraft".equals(tooltip.getMessage(JadeIds.CORE_MOD_NAME)), "Jade's registry attribution changed");
        System.out.println("JADE FLUID TARGET " + sample.dimension.location() + " " + sample.state + " -> " + latestTitle);
        next++;
        preparing = false;
        if (next != samples.size()) return false;
        System.out.println("JADE REACH FLUID CAPTIONS PASS real crosshair targets; source, flowing, overworld and solid controls");
        return true;
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
                world.setBlockAndUpdate(hit, sample.state);
                Vec3 feet = Vec3.atBottomCenterOf(floor.above());
                float pitch = (float)Math.toDegrees(Math.atan2(feet.y + owner.getEyeHeight() - hit.getY() - .55, 2));
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
