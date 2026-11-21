package dev.uwu.shrinecheck;

import dev.uwu.dimension.BlossomVillage;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/** Independent witness for the native settlement road and gate around the proposed shrine. */
final class ShrineGate {
    private static final int HALF_ROAD = 32;
    private static final Set<String> PETAL_ROAD = Set.of("shellstone_ashlar", "lotus_scale_tile", "shellstone_steps");
    private static final Set<String> DUSK_ROAD = Set.of("vesper_ashlar", "starvein_frieze", "nightbriar_shingles", "vesper_steps");

    private ShrineGate() {}

    static int index(BlossomVillage.Plan plan) {
        return plan.dusk() ? plan.road().length - 3 : 2;
    }

    static int localX(BlossomVillage.Plan plan) {
        return plan.x() + index(plan) - HALF_ROAD;
    }

    static int localZ(BlossomVillage.Plan plan) {
        return plan.z() + plan.bends()[index(plan)];
    }

    static BlockPos center(BlossomVillage.Plan plan) {
        return road(plan, index(plan), 0);
    }

    static BlockPos road(BlossomVillage.Plan plan, int index, int lateral) {
        return plan.site().pos(plan.x() + index - HALF_ROAD, plan.road()[index],
                plan.z() + plan.bends()[index] + lateral);
    }

    static ChunkPos farEnd(BlossomVillage.Plan plan) {
        return new ChunkPos(road(plan, plan.road().length - 1, 0));
    }

    static Direction rotate(BlossomVillage.Plan plan, Direction local) {
        for (int turn = 0; turn < plan.site().turns(); ++turn) local = local.getClockWise();
        return local;
    }

    static void verify(ServerLevel level, BlossomVillage.Plan plan) {
        int index = index(plan);
        int y = plan.road()[index];
        String beam = plan.dusk() ? "velvetwood_beam" : "silkwood_beam";
        for (int side : new int[]{-3, 3}) {
            BlockPos post = plan.site().pos(localX(plan), y + 2, localZ(plan) + side);
            require(uwuId(beam).equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(post).getBlock())),
                    "Native " + beam + " gate post missing at " + post);
        }
        for (int roadIndex = index - 2; roadIndex <= index + 2; ++roadIndex) {
            for (int across = -1; across <= 1; ++across) {
                BlockPos ground = road(plan, roadIndex, across);
                verifyRoad(level, plan, ground);
                AABB playerClearance = new AABB(ground).move(0, 1, 0).inflate(-.19, 0, -.19)
                        .expandTowards(0, .8, 0);
                require(!level.getBlockCollisions(null, playerClearance).iterator().hasNext(),
                        "Gate travel lane lost foot and head clearance at " + ground.above());
            }
        }
    }

    static void verifyRoad(ServerLevel level, BlossomVillage.Plan plan, BlockPos ground) {
        var id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(ground).getBlock());
        var allowed = plan.dusk() ? DUSK_ROAD : PETAL_ROAD;
        require(id.getNamespace().equals("uwudimension") && allowed.contains(id.getPath()),
                "Expected authored village road at " + ground + ", got " + id);
    }

    static ResourceLocation uwuId(String path) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension", path);
    }

    static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
