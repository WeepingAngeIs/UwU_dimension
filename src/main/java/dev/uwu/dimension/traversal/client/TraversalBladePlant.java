package dev.uwu.dimension.traversal.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A rigid correction of the finished held-item pose. All returned distances are in its output space. */
public final class TraversalBladePlant {
    private final Vec3 grip;
    private final Vec3 gripDelta;
    private final Quaternionf turn;
    private final Matrix4f correctedMesh;

    private TraversalBladePlant(Vec3 grip, Vec3 gripDelta, Quaternionf turn, Matrix4f correctedMesh) {
        this.grip = grip;
        this.gripDelta = gripDelta;
        this.turn = turn;
        this.correctedMesh = correctedMesh;
    }

    /** Original wrapped grip, before applying the traversal-only pose. */
    public Vec3 grip() { return grip; }

    /** Move the owner's wrist by this vector to keep its approved grip on the shaft. */
    public Vec3 gripDelta() { return gripDelta; }

    /** Rotation about the wrap in the output space of the supplied mesh matrix. */
    public Quaternionf turn() { return new Quaternionf(turn); }

    /** Mesh-to-pose matrix after the wrist translation and blade rotation. */
    public Matrix4f correctedMesh() { return new Matrix4f(correctedMesh); }

    static TraversalBladePlant solve(Vec3 meshGrip, Vec3 meshTip, Matrix4fc meshToPose, Vec3 contact) {
        if (!finite(meshGrip) || !finite(meshTip) || !finite(contact) || meshToPose == null
                || !meshToPose.isFinite() || !meshToPose.isAffine() || meshToPose.determinant() == 0) return null;
        Vec3 grip = transform(meshToPose, meshGrip);
        Vec3 blade = transform(meshToPose, meshTip).subtract(grip);
        double length = blade.length();
        if (!Double.isFinite(length) || length < 1e-7) return null;

        Vec3 axis = blade.scale(1 / length);
        Vec3 toContact = contact.subtract(grip);
        double distance = toContact.length();
        if (!Double.isFinite(distance)) return null;
        Vec3 aim = distance > 1e-7 ? toContact.scale(1 / distance) : axis;
        // The shortest wrist translation that leaves one complete blade length to the contact.
        Vec3 newGrip = contact.subtract(aim.scale(length));
        Vec3 shift = newGrip.subtract(grip);
        Quaternionf turn = turnBetween(axis, aim);
        Matrix4f corrected = new Matrix4f()
            .translation((float)newGrip.x, (float)newGrip.y, (float)newGrip.z)
            .rotate(turn)
            .translate((float)-grip.x, (float)-grip.y, (float)-grip.z)
            .mul(meshToPose);
        return corrected.isFinite() ? new TraversalBladePlant(grip, shift, turn, corrected) : null;
    }

    private static Quaternionf turnBetween(Vec3 from, Vec3 to) {
        Vec3 cross = from.cross(to);
        double sine = cross.length();
        double cosine = from.dot(to);
        if (sine < 1e-12) {
            if (cosine >= 0) return new Quaternionf();
            // At 180 degrees the cross product vanishes. Select a stable perpendicular axis.
            Vec3 normal = from.cross(Math.abs(from.x) < .8 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
            return new Quaternionf((float)normal.x, (float)normal.y, (float)normal.z, 0);
        }
        double halfAngle = .5 * Math.atan2(sine, cosine);
        double scale = Math.sin(halfAngle) / sine;
        return new Quaternionf((float)(cross.x * scale), (float)(cross.y * scale),
            (float)(cross.z * scale), (float)Math.cos(halfAngle));
    }

    private static Vec3 transform(Matrix4fc matrix, Vec3 point) {
        Vector3f result = matrix.transformPosition((float)point.x, (float)point.y, (float)point.z, new Vector3f());
        return new Vec3(result.x, result.y, result.z);
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
