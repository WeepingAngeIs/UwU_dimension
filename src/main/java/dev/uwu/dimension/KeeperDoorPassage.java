package dev.uwu.dimension;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Guides only Keepers through authored doors on a route chosen by their normal villager brain. */
final class KeeperDoorPassage {
    private static final double[] LANES = {0, -.09, .09, -.145, .145};
    private static final int STALL_TICKS = 6;
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_GUIDE_TICKS = 32;
    private static final int RETRY_DELAY = 30;
    private static final double CLEARANCE = 1.0E-5;

    private final Keeper keeper;
    @Nullable private Passage passage;
    @Nullable private BlockPos blockedDoor;
    private long retryAt;

    private static final class Passage {
        final ServerLevel level;
        final BlockPos door;
        final DoorBlock block;
        final Direction.Axis axis;
        final int direction;
        final boolean opened;
        final long deadline;
        double lane;
        double walkSpeed;
        double lastDistance = Double.POSITIVE_INFINITY;
        long lastProgress;
        int lanesTried;
        int attempts;
        boolean linedUp;

        Passage(ServerLevel level, BlockPos door, BlockState state, int direction, boolean opened) {
            this.level = level;
            this.door = door;
            this.block = (DoorBlock) state.getBlock();
            this.axis = state.getValue(DoorBlock.FACING).getAxis();
            this.direction = direction;
            this.opened = opened;
            this.deadline = level.getGameTime() + MAX_GUIDE_TICKS;
        }

        double progress(Vec3 position) {
            return direction * (position.get(axis) - (door.get(axis) + .5));
        }

        double lateral(Vec3 position) {
            return axis == Direction.Axis.X ? position.z - door.getZ() - .5 : position.x - door.getX() - .5;
        }

        Vec3 at(double progress, double y, double lane) {
            double across = direction * progress;
            return new Vec3(door.getX() + .5 + (axis == Direction.Axis.X ? across : lane), y,
                    door.getZ() + .5 + (axis == Direction.Axis.X ? lane : across));
        }
    }

    KeeperDoorPassage(Keeper keeper) {
        this.keeper = keeper;
    }

    boolean guiding() {
        return passage != null || blockedDoor != null && keeper.blockPosition().closerThan(blockedDoor, 2);
    }

    void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now >= retryAt) blockedDoor = null;
        Path route = keeper.getNavigation().getPath();
        boolean walking = keeper.getTarget() == null && !keeper.isTrading() && !keeper.isSleeping();
        if (passage == null && walking && route != null && !route.isDone()) passage = findDoor(level, route);
        Passage current = passage;
        if (current == null) return;
        if (level != current.level || !current.door.closerToCenterThan(keeper.position(), 2.75)) {
            passage = null;
            return;
        }

        double progress = current.progress(keeper.position());
        boolean passed = progress > .5 + keeper.getBbWidth() * .5;
        if (passed || !walking) {
            if (closeWhenClear(current) || now >= current.deadline + 20) passage = null;
            return;
        }

        BlockState state = doorState(level, current.door);
        if (state == null || !state.is(current.block) || !state.getValue(DoorBlock.OPEN)) {
            passage = null;
            return;
        }
        if (now >= current.deadline || route == null && progress < -.5) {
            retryRoute(current, now);
            return;
        }

        // First let the normal navigator approach a step or landing up to the door's height.
        if (Math.abs(keeper.getY() - current.door.getY()) > .55) return;
        if (current.attempts == 0 && !chooseLane(current, now)) {
            retryRoute(current, now);
            return;
        }

        Vec3 wanted = waypoint(current, progress);
        double remaining = keeper.position().distanceTo(wanted);
        if (remaining < current.lastDistance - .025) {
            current.lastProgress = now;
            current.lastDistance = remaining;
        }
        if (now - current.lastProgress >= STALL_TICKS || !clearFirstStep(current, wanted)) {
            if (!chooseLane(current, now)) {
                retryRoute(current, now);
                return;
            }
            wanted = waypoint(current, progress);
            if (!clearFirstStep(current, wanted)) return;
        }
        var move = keeper.getMoveControl();
        if (route != null && !route.isDone() && move.hasWanted())
            current.walkSpeed = move.getSpeedModifier();
        double speed = current.walkSpeed;
        if (!current.linedUp) {
            double movement = keeper.getAttributeValue(Attributes.MOVEMENT_SPEED);
            speed = Math.min(speed, Math.max(.05, keeper.position().distanceTo(wanted) / Math.max(.001, movement * 2)));
        }
        if (speed > 0) move.setWantedPosition(wanted.x, wanted.y, wanted.z, speed);
    }

    @Nullable private Passage findDoor(ServerLevel level, Path route) {
        int first = Math.max(0, route.getNextNodeIndex() - 1);
        int stop = Math.min(route.getNodeCount() - 1, route.getNextNodeIndex() + 2);
        for (int i = first; i <= stop; i++) {
            BlockPos node = route.getNodePos(i);
            if (!level.hasChunkAt(node) || !node.closerToCenterThan(keeper.position(), 1.65)) continue;
            BlockState seen = level.getBlockState(node);
            if (!isAuthoredWood(seen)) continue;
            BlockPos lower = seen.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? node : node.below();
            if (lower.equals(blockedDoor)) continue;
            BlockState door = doorState(level, lower);
            if (door == null) continue;
            Direction.Axis axis = door.getValue(DoorBlock.FACING).getAxis();
            // A route must continue to the far side, not just target this door or its handle.
            int next = i + 1;
            while (next < route.getNodeCount() && route.getNodePos(next).get(axis) == lower.get(axis)) ++next;
            if (next == route.getNodeCount()) continue;
            int direction = Integer.signum(route.getNodePos(next).get(axis) - lower.get(axis));
            double approach = direction * (keeper.position().get(axis) - (lower.get(axis) + .5));
            if (approach > .4) continue;
            boolean opened = !door.getValue(DoorBlock.OPEN);
            if (opened) ((DoorBlock) door.getBlock()).setOpen(keeper, level, door, lower, true);
            return new Passage(level, lower, door, direction, opened);
        }
        return null;
    }

    private Vec3 waypoint(Passage current, double progress) {
        double y = Math.max(current.door.getY(), keeper.getY());
        if (!current.linedUp) {
            if (Math.abs(current.lateral(keeper.position()) - current.lane) > .045)
                return current.at(progress, y, current.lane);
            current.linedUp = true;
            current.lastDistance = Double.POSITIVE_INFINITY;
            current.lastProgress = current.level.getGameTime();
        }
        return current.at(.98, y, current.lane);
    }

    private boolean chooseLane(Passage current, long now) {
        if (current.attempts >= MAX_ATTEMPTS) return false;
        for (int i = 0; i < LANES.length; i++) {
            if ((current.lanesTried & 1 << i) != 0) continue;
            current.lanesTried |= 1 << i;
            double lane = LANES[i];
            double y = current.door.getY();
            if (!collisionFree(current.level, current.at(0, y, lane))
                    || !collisionFree(current.level, current.at(-.48, y, lane))
                    || !collisionFree(current.level, current.at(.48, y, lane))) continue;
            current.lane = lane;
            current.attempts++;
            current.linedUp = false;
            current.lastDistance = Double.POSITIVE_INFINITY;
            current.lastProgress = now;
            return true;
        }
        return false;
    }

    private boolean clearFirstStep(Passage current, Vec3 target) {
        Vec3 delta = target.subtract(keeper.position());
        return collisionFree(current.level, keeper.position().add(delta.scale(Math.min(1, .18 / Math.max(delta.length(), .001)))));
    }

    private boolean collisionFree(ServerLevel level, Vec3 feet) {
        AABB box = keeper.getBoundingBox().move(feet.subtract(keeper.position())).deflate(CLEARANCE);
        return level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                && level.getWorldBorder().isWithinBounds(box) && level.noCollision(keeper, box);
    }

    private void retryRoute(Passage current, long now) {
        blockedDoor = current.door;
        retryAt = now + RETRY_DELAY;
        closeWhenClear(current);
        passage = null;
        // Keep WALK_TARGET, its speed and Brain activity. The normal navigator recalculates its route.
        keeper.getNavigation().recomputePath();
    }

    private boolean closeWhenClear(Passage current) {
        if (!current.opened || keeper.level() != current.level) return true;
        BlockState state = doorState(current.level, current.door);
        if (state == null || !state.is(current.block) || !state.getValue(DoorBlock.OPEN)) return true;
        AABB opening = new AABB(current.door).expandTowards(0, 1, 0);
        if (!current.level.getEntitiesOfClass(LivingEntity.class, opening, LivingEntity::isAlive).isEmpty()) return false;
        if (!current.level.getEntitiesOfClass(Keeper.class, opening.inflate(.9), other -> other != keeper
                && other.isAlive() && other.usesPassage(current.door)).isEmpty()) return false;
        current.block.setOpen(keeper, current.level, state, current.door, false);
        return true;
    }

    boolean uses(BlockPos door) {
        if (passage != null && passage.door.equals(door)) return true;
        Path route = keeper.getNavigation().getPath();
        if (route == null || route.isDone()) return false;
        int first = Math.max(0, route.getNextNodeIndex() - 1);
        int stop = Math.min(route.getNodeCount() - 1, route.getNextNodeIndex() + 1);
        for (int i = first; i <= stop; i++) {
            if (route.getNodePos(i).equals(door)) return true;
        }
        return false;
    }

    private static boolean isAuthoredWood(BlockState state) {
        return state.is(ReachArchitecture.PETAL.door().get()) || state.is(ReachArchitecture.DUSK.door().get())
                || state.is(MoonveilWood.DOOR.get());
    }

    @Nullable private static BlockState doorState(ServerLevel level, BlockPos lower) {
        BlockPos upper = lower.above();
        if (!level.hasChunksAt(lower.offset(-1, 0, -1), upper.offset(1, 0, 1))) return null;
        BlockState base = level.getBlockState(lower), top = level.getBlockState(upper);
        if (!isAuthoredWood(base) || !top.is(base.getBlock()) || !DoorBlock.isWoodenDoor(base)
                || base.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                || top.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER || base.getValue(DoorBlock.POWERED)
                || top.getValue(DoorBlock.POWERED)) return null;
        return base;
    }
}
