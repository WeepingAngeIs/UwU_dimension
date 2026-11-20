package dev.uwu.lotuscheck;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Checks Minecraft's server block-light field, independently of rendering and client light mods. */
final class LotusLightPlot {
    private static final ResourceLocation MARKER =
            ResourceLocation.fromNamespaceAndPath("uwudimension", "revival_lotus_light");
    private static final int LAST_BRIGHT_TICK = 18;
    private final BlockPos origin;
    private final BlockPos sample;
    private final BlockPos torch;
    private final ServerLevel level;
    private final long preparedAt;
    private final Map<BlockPos, BlockState> retained = new HashMap<>();
    private final Map<BlockPos, BlockState> original = new HashMap<>();
    private int singlePeak;
    private int remainingPeak;
    private boolean restored;

    LotusLightPlot(ServerLevel level) {
        this.level = level;
        var chunk = new ChunkPos(level.getSharedSpawnPos());
        origin = new BlockPos(chunk.getMinBlockX() + 8, 71, chunk.getMinBlockZ() + 8);
        sample = origin.east(2);
        torch = origin.west(7).north(7);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                place(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
        }
        place(torch.below(), Blocks.STONE.defaultBlockState());
        place(torch, Blocks.TORCH.defaultBlockState());
        // Keep a real water source contained near the bloom so it cannot run into the sample cell.
        var water = origin.south(3);
        for (var wall : new BlockPos[] {water.below(), water.north(), water.south(), water.east(), water.west()}) {
            place(wall, Blocks.STONE.defaultBlockState());
        }
        place(water, Blocks.WATER.defaultBlockState());
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 2; y++) {
                    var pos = origin.offset(x, y, z);
                    original.put(pos, level.getBlockState(pos));
                }
            }
        }
        preparedAt = level.getGameTime();
    }

    BlockPos origin() {
        return origin;
    }

    boolean ready() {
        require(level.getGameTime() - preparedAt < 100, "Timed out waiting for baseline block lighting");
        require(level.getBlockState(origin).isAir() && level.getBlockState(sample).isAir(), "Expected air above the stone floor");
        return brightness(torch) == 14 && brightness(sample) == 0;
    }

    void record(long firstAge, long secondAge) {
        for (var block : retained.entrySet()) {
            require(level.getBlockState(block.getKey()).equals(block.getValue()),
                    "Bloom changed the existing " + block.getValue() + " at " + block.getKey());
        }
        int light = brightness(sample);
        if (secondAge < 0) {
            if (hasMarker() && light > 0) singlePeak = Math.max(singlePeak, light);
            return;
        }
        // The first envelope is finished here. The marker still belongs to the second bloom.
        if (firstAge > LAST_BRIGHT_TICK + 1 && secondAge <= LAST_BRIGHT_TICK - 2) {
            require(hasMarker(), "Ending the first bloom removed the overlapping light source");
            remainingPeak = Math.max(remainingPeak, light);
        }
        if (!restored && secondAge > LAST_BRIGHT_TICK + 2) {
            require(!hasMarker(), "Temporary source persisted beyond the final light envelope");
            if (light == 0 && brightness(torch) == 14) {
                for (var block : original.entrySet()) {
                    require(level.getBlockState(block.getKey()).equals(block.getValue()),
                            "Bloom left a changed block at " + block.getKey());
                }
                require(singlePeak > 0, "A solo bloom never raised actual block light in adjacent air");
                require(remainingPeak > 0, "The overlapping bloom did not keep the area lit after the first ended");
                restored = true;
                System.out.println("LOTUS BLOCK LIGHT PASS baseline=0 soloPeak=" + singlePeak
                        + " afterFirstExpiryPeak=" + remainingPeak + " restored=0 torch=14");
            }
        }
    }

    boolean restored() {
        return restored;
    }

    private void place(BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
        require(level.getBlockState(pos).equals(state), "Could not prepare light fixture at " + pos);
        retained.put(pos, state);
    }

    private int brightness(BlockPos pos) {
        return level.getBrightness(LightLayer.BLOCK, pos);
    }

    private boolean hasMarker() {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(origin).getBlock()).equals(MARKER);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
