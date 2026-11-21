package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Uses the existing ground path and accelerates only along clear, level stretches. */
final class GuardianRescueNavigation extends GroundPathNavigation {
    private static final double RUN_SPEED = 10;
    private static final int LOOK_AHEAD = 5;
    private final BlossomWarden guardian;

    GuardianRescueNavigation(BlossomWarden guardian, Level level) {
        super(guardian, level);
        this.guardian = guardian;
    }

    @Override public void tick() {
        super.tick();
        if (!guardian.rescueSprinting() || !guardian.onGround() || isDone()) return;
        var control = guardian.getMoveControl();
        if (!control.hasWanted()) return;

        var path = getPath();
        int first = path.getNextNodeIndex();
        Vec3 destination = new Vec3(control.getWantedX(), control.getWantedY(), control.getWantedZ());
        if (Math.abs(destination.y - guardian.getY()) > .125 || !clearTo(destination)) return;

        int last = first;
        if (first + 1 < path.getNodeCount()) {
            BlockPos start = path.getNodePos(first);
            BlockPos step = path.getNodePos(first + 1).subtract(start);
            if (step.getY() == 0 && Math.abs(step.getX()) + Math.abs(step.getZ()) == 1) {
                for (int i = first + 1; i <= first + LOOK_AHEAD && i < path.getNodeCount(); i++) {
                    if (!path.getNodePos(i).equals(start.offset(step.multiply(i - first)))) break;
                    Vec3 along = path.getEntityPosAtNode(guardian, i);
                    Vec3 next = new Vec3(along.x, getGroundY(along), along.z);
                    if (Math.abs(next.y - guardian.getY()) > .125 || !clearTo(next)) break;
                    destination = next;
                    last = i;
                }
            }
        }

        Vec3 direction = destination.subtract(guardian.position());
        double distance = direction.horizontalDistance();
        if (distance < .05) return;
        float yaw = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
        if (Math.abs(Mth.wrapDegrees(yaw - guardian.getYRot())) > 80) return;
        // Leave enough room to decelerate at the selected waypoint instead of overshooting a turn.
        double normal = guardian.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (normal <= 0) return;
        double speed = Mth.clamp(distance / (normal * 1.45), 1, RUN_SPEED);
        path.setNextNodeIndex(last);
        control.setWantedPosition(destination.x, destination.y, destination.z, speed);
    }

    private boolean clearTo(Vec3 destination) {
        Vec3 movement = destination.subtract(guardian.position());
        AABB sweep = guardian.getBoundingBox().expandTowards(movement).deflate(1.0E-5);
        return level.hasChunksAt(BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ),
                        BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ))
                && level.getWorldBorder().isWithinBounds(sweep) && level.noCollision(guardian, sweep);
    }
}
