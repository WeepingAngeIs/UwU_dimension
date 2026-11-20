package dev.uwu.dimension;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.uwu.dimension.mixin.ForestFluidPickerAccessor;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Exact base terrain for the dense giant-tree survey. No live chunks are acquired here. */
final class ForestHeights {
    // Sakura Reach's overworld noise uses four-block interpolation cells.
    private static final int WIDTH = 4;
    private static final Cache<ServerLevel, ForestHeights> LEVELS = CacheBuilder.newBuilder().weakKeys().build();
    private final Cache<Long, int[]> cells = CacheBuilder.newBuilder().maximumSize(8192).build();
    private final Semaphore sampling = new Semaphore(2);

    static int height(ServerLevel level, int x, int z) {
        var generator = level.getChunkSource().getGenerator();
        if (generator.getClass() != NoiseBasedChunkGenerator.class) return GroveSites.height(level, x, z);
        var noise = (NoiseBasedChunkGenerator) generator;
        var settings = noise.generatorSettings().value();
        if (settings.noiseSettings().getCellWidth() != WIDTH) return GroveSites.height(level, x, z);
        var cache = LEVELS.asMap().computeIfAbsent(level, unused -> new ForestHeights());
        int cellX = Math.floorDiv(x, WIDTH), cellZ = Math.floorDiv(z, WIDTH);
        int[] heights = cache.cells.asMap().computeIfAbsent(ChunkPos.asLong(cellX, cellZ), unused -> {
            cache.sampling.acquireUninterruptibly();
            try {
                return sample(level, noise, settings, cellX * WIDTH, cellZ * WIDTH);
            } finally {
                cache.sampling.release();
            }
        });
        return heights[Math.floorMod(x, WIDTH) * WIDTH + Math.floorMod(z, WIDTH)];
    }

    // The public unit codec returns Minecraft's exact no-structures singleton, also recognized by C2ME.
    private static final DensityFunctions.BeardifierOrMarker NO_STRUCTURES = (DensityFunctions.BeardifierOrMarker)
            DensityFunctions.BeardifierOrMarker.CODEC.codec().codec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();

    private static final class Cell extends NoiseChunk {
        Cell(ServerLevel level, NoiseSettings bounds, NoiseGeneratorSettings settings,
             Aquifer.FluidPicker fluids, int x, int z) {
            super(1, level.getChunkSource().randomState(), x, z, bounds, NO_STRUCTURES,
                    settings, fluids, Blender.empty());
        }

        boolean isGround(NoiseGeneratorSettings settings) {
            var state = getInterpolatedState();
            return Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state == null ? settings.defaultBlock() : state);
        }
    }

    private static int[] sample(ServerLevel level, NoiseBasedChunkGenerator generator,
                                NoiseGeneratorSettings settings, int x, int z) {
        var bounds = settings.noiseSettings().clampToHeightAccessor(level);
        int cellHeight = bounds.getCellHeight();
        int bottomCell = Math.floorDiv(bounds.minY(), cellHeight);
        int cellCount = Math.floorDiv(bounds.height(), cellHeight);
        int missing = level.getMinBuildHeight() - 1;
        int[] heights = new int[WIDTH * WIDTH];
        Arrays.fill(heights, missing);
        if (cellCount == 0) return heights;

        var fluids = ((ForestFluidPickerAccessor) generator).uwu$forestFluidPicker().get();
        var noise = new Cell(level, bounds, settings, fluids, x, z);
        noise.initializeForFirstCellX();
        noise.advanceCellX(0);
        int remaining = heights.length;
        // Match iterateNoiseColumn's top-down first solid. All 16 columns share the same noise cell.
        try {
            for (int cy = cellCount - 1; cy >= 0 && remaining > 0; cy--) {
                noise.selectCellYZ(cy, 0);
                for (int dy = cellHeight - 1; dy >= 0 && remaining > 0; dy--) {
                    int y = (bottomCell + cy) * cellHeight + dy;
                    noise.updateForY(y, (double) dy / cellHeight);
                    for (int dx = 0; dx < WIDTH; dx++) {
                        noise.updateForX(x + dx, (double) dx / WIDTH);
                        for (int dz = 0; dz < WIDTH; dz++) {
                            int index = dx * WIDTH + dz;
                            if (heights[index] != missing) continue;
                            noise.updateForZ(z + dz, (double) dz / WIDTH);
                            if (noise.isGround(settings)) {
                                heights[index] = y;
                                remaining--;
                            }
                        }
                    }
                }
            }
        } finally {
            noise.stopInterpolation();
        }
        return heights;
    }
}
