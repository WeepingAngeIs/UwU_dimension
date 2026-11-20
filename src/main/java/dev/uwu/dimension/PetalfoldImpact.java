package dev.uwu.dimension;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/** A hit-time snapshot. Coordinates on the face use positive X toward the player's left and positive Y up. */
record PetalfoldImpact(Vec3 center, Vec3 left, Vec3 forward, Vec3 eye, Vec3 viewForward, Vec3 viewUp,
                      double scale, int handSign, double hitX, double hitY, double grazing) {
    // Dimensions follow the outer rim of the raised wide-player mesh in blocks.
    private static final double RADIUS_X = .40, RADIUS_Y = .407;

    static PetalfoldImpact capture(LivingEntity holder, DamageSource damage) {
        var arm = holder.getUsedItemHand() == InteractionHand.MAIN_HAND ? holder.getMainArm() : holder.getMainArm().getOpposite();
        var frame = frame(holder.position(), holder.getEyePosition(), holder.getBbHeight(),
                holder.getYHeadRot(), holder.getXRot(), arm == HumanoidArm.LEFT ? 1 : -1);
        var direct = damage.getDirectEntity();
        Vec3 source = damage.sourcePositionRaw();
        if (source == null && direct instanceof LivingEntity living) {
            source = living.position().add(0, living.getBbHeight() * .60, 0);
        }
        if (source == null) source = damage.getSourcePosition();
        if (direct instanceof Projectile projectile) {
            return frame.locate(source, projectile.position(), projectile.getDeltaMovement());
        }
        return frame.locate(source, null, null);
    }

    static PetalfoldImpact frame(Vec3 feet, Vec3 eye, double height, float yaw, float pitch, int hand) {
        var forward = Vec3.directionFromRotation(0, yaw).normalize();
        var left = new Vec3(forward.z, 0, -forward.x);
        var viewForward = Vec3.directionFromRotation(pitch, yaw).normalize();
        var viewUp = viewForward.cross(left).normalize();
        double scale = height / 1.8;
        var center = feet.add(left.scale(.134 * scale * hand)).add(0, .588 * height, 0)
                .add(forward.scale(.52 * scale));
        return new PetalfoldImpact(center, left, forward, eye, viewForward, viewUp, scale, hand, 0, 0, 0);
    }

    PetalfoldImpact locate(Vec3 source, Vec3 projectileStart, Vec3 velocity) {
        if (projectileStart != null && velocity != null) {
            var normal = forward.add(left.scale(-.08 * handSign)).add(0, .185, 0).normalize();
            var travel = velocity.normalize();
            double incidence = -travel.dot(normal);
            if (incidence > .015) {
                double distance = projectileStart.subtract(center).dot(normal) / incidence;
                // Vanilla can report damage either side of the mathematical plane within one step.
                if (Math.abs(distance) <= Math.max(2.5, velocity.length() + 1)) {
                    var crossing = projectileStart.add(travel.scale(distance)).subtract(center);
                    return resolved(crossing.dot(left) / (RADIUS_X * scale), crossing.y / (RADIUS_Y * scale), 1 - incidence);
                }
            }
            if (source == null) source = projectileStart;
        }
        if (source == null) return this;
        var offset = source.subtract(center);
        double distance = Math.max(.55 * scale, Math.hypot(offset.x, offset.z));
        // Melee exposes an incoming direction, but no shield mesh intersection.
        return resolved(offset.dot(left) / (.76 * distance),
                offset.y / Math.max(.53 * scale, .48 * distance), 1 - offset.dot(forward) / distance);
    }

    private PetalfoldImpact resolved(double x, double y, double incidenceLoss) {
        double length = Math.max(1, Math.hypot(x, y));
        return new PetalfoldImpact(center, left, forward, eye, viewForward, viewUp, scale, handSign,
                x / length, y / length, Math.clamp(incidenceLoss, 0, 1));
    }

    Vec3 point(double x, double y, double lip, boolean firstPerson) {
        // These two poses are measured separately from the packaged first- and third-person models.
        if (firstPerson) {
            return eye.add(left.scale(.587 * handSign + .372 * x)).add(viewUp.scale(-.389 + .397 * y))
                    .add(viewForward.scale(1.107 + lip - .08 * y));
        }
        return center.add(left.scale(RADIUS_X * x * scale)).add(0, RADIUS_Y * y * scale, 0)
                .add(forward.scale((lip - .075 * y + .032 * handSign * x) * scale));
    }

    Vec3 toss(double lateral, double vertical, double depth, boolean firstPerson) {
        if (firstPerson) return left.scale(lateral).add(viewUp.scale(vertical)).add(viewForward.scale(depth));
        return left.scale(lateral).add(0, vertical, 0).add(forward.scale(depth));
    }
}
