package dev.uwu.dimension;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A female Guardian may use a hand-openable door on her current path. */
final class GuardianDoorGoal extends Goal {
    private record Door(ServerLevel level, BlockPos lower, DoorBlock block) {
        AABB passage() {
            return new AABB(lower).expandTowards(0, 1, 0);
        }
    }

    private final BlossomWarden guard;
    @Nullable private Door candidate, opened;
    private Vec3 approach = Vec3.ZERO;
    private int ticksOpen;

    GuardianDoorGoal(BlossomWarden guard) {
        this.guard = guard;
    }

    @Override
    public boolean canUse() {
        if (!guard.female() || !(guard.level() instanceof ServerLevel level)
                || !(guard.getNavigation() instanceof GroundPathNavigation navigation)
                || !navigation.canOpenDoors()) return false;
        var path = navigation.getPath();
        if (path == null || path.isDone()) return false;
        for (int i = Math.max(0, path.getNextNodeIndex() - 1);
                i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 3); i++) {
            var node = path.getNode(i);
            BlockPos pos = new BlockPos(node.x, node.y, node.z);
            if (Vec3.atBottomCenterOf(pos).distanceToSqr(guard.position()) > 2.5 * 2.5) continue;
            candidate = closedDoor(level, pos);
            if (candidate == null) candidate = closedDoor(level, pos.above());
            if (candidate != null) return true;
        }
        return false;
    }

    @Override
    public void start() {
        Door door = candidate;
        candidate = null;
        if (door == null || guard.level() != door.level() || !guard.female()) return;
        BlockState state = currentDoor(door);
        if (state == null || state.getValue(DoorBlock.OPEN)) return;
        door.block().setOpen(guard, door.level(), state, door.lower(), true);
        BlockState after = currentDoor(door);
        if (after == null || !after.getValue(DoorBlock.OPEN)) return;
        opened = door;
        approach = directionFromDoor(door);
        ticksOpen = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return opened != null && guard.level() == opened.level() && guard.isAlive() && guard.female();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Door door = opened;
        if (door == null) return;
        BlockState state = currentDoor(door);
        if (state == null || !state.getValue(DoorBlock.OPEN)) {
            opened = null;
            return;
        }
        Vec3 side = directionFromDoor(door);
        boolean passed = approach.dot(side) < 0;
        boolean leftPassage = side.lengthSqr() > 2.5 * 2.5 || guard.getNavigation().isDone();
        if (++ticksOpen >= 2 && (passed || leftPassage) && clear(door)) {
            door.block().setOpen(guard, door.level(), state, door.lower(), false);
            opened = null;
        }
    }

    @Override
    public void stop() {
        Door door = opened;
        opened = null;
        candidate = null;
        if (door == null || guard.level() != door.level()) return;
        BlockState state = currentDoor(door);
        if (state != null && state.getValue(DoorBlock.OPEN) && clear(door))
            door.block().setOpen(guard, door.level(), state, door.lower(), false);
    }

    private Vec3 directionFromDoor(Door door) {
        return guard.position().subtract(Vec3.atBottomCenterOf(door.lower())).multiply(1, 0, 1);
    }

    private boolean clear(Door door) {
        AABB passage = door.passage();
        return !guard.getBoundingBox().intersects(passage)
                && door.level().getEntities(guard, passage, Entity::isAlive).isEmpty();
    }

    private static @Nullable Door closedDoor(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (!DoorBlock.isWoodenDoor(state) || state.getValue(DoorBlock.OPEN)) return null;
        BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        Door door = new Door(level, lower.immutable(), (DoorBlock) state.getBlock());
        BlockState base = currentDoor(door);
        return base != null && !base.getValue(DoorBlock.OPEN) ? door : null;
    }

    private static @Nullable BlockState currentDoor(Door door) {
        ServerLevel level = door.level();
        BlockPos lower = door.lower(), upper = lower.above();
        // Setting a door state also updates the other half and adjacent block shapes.
        if (!level.hasChunksAt(lower.offset(-1, 0, -1), upper.offset(1, 0, 1))
                || level.isOutsideBuildHeight(lower) || level.isOutsideBuildHeight(upper)) return null;
        BlockState bottom = level.getBlockState(lower), top = level.getBlockState(upper);
        if (!bottom.is(door.block()) || !top.is(door.block())
                || bottom.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                || top.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
                || bottom.getValue(DoorBlock.POWERED) || top.getValue(DoorBlock.POWERED)) return null;
        return bottom;
    }
}
