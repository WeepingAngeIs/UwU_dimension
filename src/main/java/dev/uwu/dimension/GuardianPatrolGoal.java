package dev.uwu.dimension;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Handles noncombat travel around the Guardian's own court. */
final class GuardianPatrolGoal extends Goal {
    static final int IDLE_RETURN_TICKS = 4_800;
    private static final double HOME_RADIUS_SQUARED = 8 * 8;
    private static final int OUTSIDE_LIMIT = 64;
    private static final double STATIONARY_TOLERANCE = 1.0E-6;
    private static final double WALK_SPEED = .8, RETURN_SPEED = 1.5;
    private static final int[] X = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] Z = {0, 1, 1, 1, 0, -1, -1, -1};

    private final BlossomWarden guard;
    @Nullable private ServerLevel observedLevel;
    @Nullable private BlockPos observedHome;
    @Nullable private Vec3 previousPosition;
    private int stationaryTicks;
    private boolean hadCombat, night, returning, idleReturn, ownsSprint, outward = true;
    private long retryAt;
    @Nullable private Path route;
    private boolean routeReturning;

    GuardianPatrolGoal(BlossomWarden guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** Sample actual active server ticks, independently of the reduced goal tick cadence. */
    void observe() {
        if (!(guard.level() instanceof ServerLevel level)) return;
        BlockPos home = guard.villageHome();
        if (observedLevel != level || !Objects.equals(home, observedHome)) {
            stop();
            observedLevel = level;
            observedHome = home;
            stationaryTicks = 0;
            idleReturn = false;
            returning = false;
            previousPosition = null;
            retryAt = level.getGameTime();
        }
        Vec3 position = guard.position();
        Vec3 last = previousPosition == null ? new Vec3(guard.xo, guard.yo, guard.zo) : previousPosition;
        boolean moved = last.distanceToSqr(position) > STATIONARY_TOLERANCE;
        previousPosition = position;
        boolean combat = guard.hasOffenders();
        if (home == null || combat) {
            stationaryTicks = 0;
            idleReturn = false;
            returning = false;
            releaseRoute();
        } else {
            boolean wasReturning = returning;
            boolean wasNight = night;
            night = isNight(level.getDayTime());
            if (position.distanceToSqr(Vec3.atBottomCenterOf(home)) <= HOME_RADIUS_SQUARED) {
                stationaryTicks = 0;
                idleReturn = false;
                returning = false;
            } else {
                stationaryTicks = moved || hadCombat ? 0 : Math.min(stationaryTicks + 1, IDLE_RETURN_TICKS);
                idleReturn |= stationaryTicks == IDLE_RETURN_TICKS;
                returning = night || idleReturn;
            }
            if (wasReturning != returning || wasNight != night) {
                releaseRoute();
                retryAt = level.getGameTime();
            }
        }
        hadCombat = combat;
    }

    static boolean isNight(long dayTime) {
        long time = Math.floorMod(dayTime, 24_000L);
        return time >= 13_000L && time < 23_000L;
    }

    int stationaryTicks() { return stationaryTicks; }
    boolean returningHome() { return returning; }

    @Override
    public boolean canUse() {
        if (observedLevel == null || observedHome == null || guard.hasOffenders()
                || guard.level() != observedLevel || night && !returning
                || observedLevel.getGameTime() < retryAt) return false;
        if (!returning && outsideDistanceSquared(guard.position(), observedHome) > OUTSIDE_LIMIT * OUTSIDE_LIMIT)
            return false;
        if (!returning && !outward && guard.distanceToSqr(Vec3.atBottomCenterOf(observedHome)) <= HOME_RADIUS_SQUARED)
            outward = true;

        routeReturning = returning;
        Set<BlockPos> waypoints = returning || !outward ? towardsCourt(observedLevel, observedHome)
                : outwardWaypoints(observedLevel, observedHome);
        route = waypoints.isEmpty() ? null : guard.getNavigation().createPath(waypoints, 0);
        retryAt = observedLevel.getGameTime() + 20;
        if (route == null || !route.canReach() || !safeRoute(observedLevel, observedHome, route)) {
            route = null;
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        if (route == null || !guard.getNavigation().moveTo(route, routeReturning ? RETURN_SPEED : WALK_SPEED)) {
            route = null;
            return;
        }
        if (routeReturning && !guard.isSprinting()) {
            guard.setSprinting(true);
            ownsSprint = true;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (route == null || observedLevel == null || observedHome == null || guard.hasOffenders()
                || routeReturning != returning || night && !returning) return false;
        Path current = guard.getNavigation().getPath();
        if (current == null || !route.getTarget().equals(current.getTarget())) return false;
        // Navigation can recompute a running path, so validate the remaining nodes again.
        route = current;
        return !route.isDone() && safeRoute(observedLevel, observedHome, route);
    }

    @Override
    public void stop() {
        if (route != null && !routeReturning && route.isDone() && !guard.hasOffenders()) {
            outward = !outward;
            if (observedLevel != null) retryAt = observedLevel.getGameTime() + 35 + guard.getRandom().nextInt(46);
        }
        releaseRoute();
    }

    private void releaseRoute() {
        if (route != null) {
            Path current = guard.getNavigation().getPath();
            if (current == route || !guard.hasOffenders() && current != null
                    && route.getTarget().equals(current.getTarget()))
                guard.getNavigation().stop();
            route = null;
        }
        if (ownsSprint) {
            guard.setSprinting(false);
            ownsSprint = false;
        }
    }

    private Set<BlockPos> towardsCourt(ServerLevel level, BlockPos home) {
        Vec3 center = Vec3.atBottomCenterOf(home);
        Vec3 offset = guard.position().subtract(center).multiply(1, 0, 1);
        double distance = offset.horizontalDistance();
        // The native path search has a finite range. Approach a distant court in short legs.
        Vec3 waypoint = distance <= 30 ? center : center.add(offset.scale((distance - 26) / distance));
        int y = distance <= 30 ? home.getY() : guard.getBlockY();
        Set<BlockPos> options = new LinkedHashSet<>();
        BlockPos around = BlockPos.containing(waypoint.x, y, waypoint.z);
        addStandable(level, options, around);
        for (int i = 0; i < X.length; i++) addStandable(level, options, around.offset(X[i] * 3, 0, Z[i] * 3));
        return options;
    }

    private Set<BlockPos> outwardWaypoints(ServerLevel level, BlockPos home) {
        if (guard.distanceToSqr(Vec3.atBottomCenterOf(home)) > 40 * 40) return towardsCourt(level, home);
        Set<BlockPos> options = new LinkedHashSet<>();
        int first = guard.getRandom().nextInt(X.length);
        for (int i = 0; i < X.length && options.size() < 4; i++) {
            int direction = (first + i) % X.length;
            int radius = 20 + guard.getRandom().nextInt(24);
            BlockPos spot = home.offset(X[direction] * radius, guard.getBlockY() - home.getY(), Z[direction] * radius);
            if (Vec3.atBottomCenterOf(spot).subtract(guard.position()).horizontalDistanceSqr() >= 8 * 8)
                addStandable(level, options, spot);
        }
        return options;
    }

    private void addStandable(ServerLevel level, Set<BlockPos> options, BlockPos around) {
        for (int distance = 0; distance <= 5; distance++) {
            if (addStandableAt(level, options, around.above(distance))
                    || distance > 0 && addStandableAt(level, options, around.below(distance))) return;
        }
    }

    private boolean addStandableAt(ServerLevel level, Set<BlockPos> options, BlockPos spot) {
        Vec3 feet = Vec3.atBottomCenterOf(spot);
        AABB bounds = guard.getBoundingBox().move(feet.subtract(guard.position()));
        if (!loaded(level, bounds) || !level.getWorldBorder().isWithinBounds(bounds)) return false;
        BlockPos floor = spot.below();
        if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                || !level.noCollision(guard, bounds) || level.containsAnyLiquid(bounds)) return false;
        options.add(spot);
        return true;
    }

    private boolean safeRoute(ServerLevel level, BlockPos home, Path path) {
        double limitSquared = OUTSIDE_LIMIT * OUTSIDE_LIMIT;
        double previousOutside = outsideDistanceSquared(guard.position(), home);
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            Vec3 position = path.getEntityPosAtNode(guard, i);
            double outside = outsideDistanceSquared(position, home);
            // A displaced Guardian can return from beyond the band. Its route must head inward.
            double greatestInwardStep = i == 0 && previousOutside > limitSquared
                    ? Math.sqrt(previousOutside) + .75 : Math.sqrt(previousOutside) + .001;
            if (outside > limitSquared && (!routeReturning || outside > greatestInwardStep * greatestInwardStep))
                return false;
            AABB bounds = guard.getBoundingBox().move(position.subtract(guard.position()));
            if (!loaded(level, bounds) || !level.getWorldBorder().isWithinBounds(bounds)) return false;
            previousOutside = outside;
        }
        return true;
    }

    private static boolean loaded(ServerLevel level, AABB bounds) {
        AABB margin = bounds.inflate(1);
        return !level.isOutsideBuildHeight(BlockPos.containing(bounds.minX, bounds.minY - 1, bounds.minZ))
                && !level.isOutsideBuildHeight(BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))
                && level.hasChunksAt(BlockPos.containing(margin.minX, margin.minY, margin.minZ),
                        BlockPos.containing(margin.maxX, margin.maxY, margin.maxZ));
    }

    private static double outsideDistanceSquared(Vec3 position, BlockPos home) {
        double x = Math.max(0, Math.abs(position.x - (home.getX() + .5)) - GuardianVillageHomes.PRECINCT_RADIUS);
        double z = Math.max(0, Math.abs(position.z - (home.getZ() + .5)) - GuardianVillageHomes.PRECINCT_RADIUS);
        return x * x + z * z;
    }
}
