package dev.uwu.guardiancheck;

import static dev.uwu.guardiancheck.GuardianArena.require;

import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.People;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

final class KeeperDoorProbe {
    private static final int QUIET_UPDATE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private final List<Lane> lanes = List.of(
            new Lane("silkwood_door", DoorHingeSide.LEFT, false, false),
            new Lane("silkwood_door", DoorHingeSide.RIGHT, true, false),
            new Lane("velvetwood_door", DoorHingeSide.RIGHT, true, true));
    private GuardianArena arena;
    private int ticks;
    private boolean started;

    boolean tick(ServerLevel level) {
        ++ticks;
        if (arena == null) {
            arena = new GuardianArena(level);
            BlockPos start = level.getSharedSpawnPos().offset(0, 16, 280);
            for (int i = 0; i < lanes.size(); i++) {
                Lane lane = lanes.get(i);
                lane.place(level, arena, start.south(i * 32));
            }
            return false;
        }
        require(ticks <= 560, "Keeper doorway timed out: " + lanes.stream().map(Lane::describe).toList());
        if (!started) {
            if (!arena.entitiesReady()) return false;
            for (Lane lane : lanes) lane.spawn(level);
            started = true;
            return false;
        }
        boolean allPassed = true;
        for (Lane lane : lanes) {
            lane.observe(level);
            allPassed &= lane.passed;
        }
        if (!allPassed) return false;
        System.out.println("KEEPER DOOR SERVER PASS both Silkwood hinges at night and a Velvetwood threat escape");
        return true;
    }

    private static final class Lane {
        private final String id;
        private final DoorHingeSide hinge;
        private final boolean dusk;
        private final boolean threat;
        private BlockPos door;
        private BlockPos bed;
        private Keeper keeper;
        private boolean lastOpen;
        private boolean observedFear;
        private boolean passed;
        private int opens;

        Lane(String id, DoorHingeSide hinge, boolean dusk, boolean threat) {
            this.id = id;
            this.hinge = hinge;
            this.dusk = dusk;
            this.threat = threat;
        }

        void place(ServerLevel level, GuardianArena arena, BlockPos origin) {
            door = origin;
            bed = origin.east(6);
            arena.floor(origin, -10, 8, 2);
            for (int x = -10; x <= 8; x++) {
                for (int y = 0; y < 4; y++) {
                    level.setBlock(origin.offset(x, y, -1), Blocks.CALCITE.defaultBlockState(), QUIET_UPDATE);
                    level.setBlock(origin.offset(x, y, 1), Blocks.CALCITE.defaultBlockState(), QUIET_UPDATE);
                }
            }
            for (int y = 2; y < 4; y++) level.setBlock(door.above(y), Blocks.CALCITE.defaultBlockState(), QUIET_UPDATE);
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("uwudimension", id));
            require(block instanceof DoorBlock && DoorBlock.isWoodenDoor(block.defaultBlockState()), "Missing actual registered " + id);
            var lower = block.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST)
                    .setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            level.setBlock(door, lower, QUIET_UPDATE);
            level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), QUIET_UPDATE);
            var foot = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
            level.setBlock(bed.west(), foot.setValue(BedBlock.PART, BedPart.FOOT), QUIET_UPDATE);
            level.setBlock(bed, foot.setValue(BedBlock.PART, BedPart.HEAD), QUIET_UPDATE);
        }

        void spawn(ServerLevel level) {
            var type = dusk ? People.DUSK_KEEPER : People.PETAL_KEEPER;
            keeper = type.get().create(level);
            require(keeper != null, "Could not construct " + type.getId());
            keeper.moveTo(door.getX() - 4.5, door.getY(), door.getZ() + .5, -90, 0);
            keeper.setVillagerData(keeper.getVillagerData().setProfession(VillagerProfession.NITWIT));
            keeper.refreshBrain(level);
            keeper.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
            keeper.getBrain().updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
            require(level.addFreshEntity(keeper), "Could not add Keeper");
            if (!threat) return;
            var zombie = EntityType.ZOMBIE.create(level);
            require(zombie != null, "Could not create hostile witness");
            zombie.setNoAi(true);
            zombie.setSilent(true);
            zombie.moveTo(door.getX() - 7.5, door.getY(), door.getZ() + .5, -90, 0);
            require(level.addFreshEntity(zombie), "Could not add hostile witness");
        }

        void observe(ServerLevel level) {
            if (passed) return;
            var lower = level.getBlockState(door);
            var upper = level.getBlockState(door.above());
            require(lower.getValue(DoorBlock.HINGE) == hinge && upper.getValue(DoorBlock.HINGE) == hinge,
                    "Door hinge changed: " + id);
            boolean open = lower.getValue(DoorBlock.OPEN);
            require(open == upper.getValue(DoorBlock.OPEN), "Door halves desynchronized: " + id);
            if (open && !lastOpen) ++opens;
            lastOpen = open;
            var speed = keeper.getEffect(MobEffects.MOVEMENT_SPEED);
            observedFear |= speed != null && speed.getAmplifier() >= 3;
            if (opens == 0 || open || keeper.getX() < door.getX() + 1.35) return;
            require(opens == 1, "Keeper repeatedly cycled the door: " + describe());
            require(!threat || observedFear, "Threat fixture did not use the Keeper's fear speed");
            require(threat || keeper.isSleeping() || keeper.getBrain().getMemory(MemoryModuleType.HOME)
                    .map(GlobalPos::pos).filter(bed::equals).isPresent(), "Keeper lost its bed memory");
            if (!threat && !keeper.isSleeping()) return;
            System.out.println("KEEPER DOOR PASS " + describe());
            keeper.discard();
            passed = true;
        }

        String describe() {
            var route = keeper == null ? null : keeper.getNavigation().getPath();
            return id + "/" + hinge + "/" + (dusk ? "dusk" : "petal") + ", opens=" + opens + ", fear=" + observedFear + ", position="
                    + (keeper == null ? "not spawned" : keeper.position() + ", sleep=" + keeper.isSleeping()
                    + ", home=" + keeper.getBrain().getMemory(MemoryModuleType.HOME))
                    + ", next=" + (route == null || route.isDone() ? "none" : route.getNextNodePos());
        }
    }
}
