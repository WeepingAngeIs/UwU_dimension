package dev.uwu.dimension;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Client-only poses for the six existing female guardian bones. */
final class OwlGuardianGaits {
    // Bone order in the clip: basket, head, left arm, right arm, left leg, right leg.
    // Rows contain position offsets in model pixels, then absolute X/Y/Z rotations in radians.
    record Clip(float durationSeconds, float[][][] frames) {}
    private record Clips(Clip walk, Clip chase) {}

    private static final Clips CLIPS = load();
    private static final float MOVEMENT_THRESHOLD = 0.01F;
    private static final float FULL_MOVEMENT = 0.2F;

    private static Clips load() {
        var stream = Objects.requireNonNull(OwlGuardianGaits.class.getResourceAsStream(
                "/assets/uwudimension/animation/owl_guardian_gaits.json"));
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Clips.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read Owl Guardian gait clips", ex);
        }
    }

    static void reset(ModelPart[] parts) {
        // Children keep the per-cube scale installed by FemaleGuardianModel.sizeCubes.
        for (ModelPart part : parts) {
            part.resetPose();
        }
    }

    static Clip select(boolean grounded, boolean handlingTarget, boolean chasing, float movement) {
        if (!grounded || handlingTarget || movement <= MOVEMENT_THRESHOLD) {
            return null;
        }
        return chasing ? CLIPS.chase : CLIPS.walk;
    }

    static void apply(ModelPart[] parts, ModelPart head, Clip clip, float ageTicks, float movement) {
        double cycles = ageTicks / (20D * clip.durationSeconds);
        double position = (cycles - Math.floor(cycles)) * clip.frames.length;
        int index = (int) position;
        float mix = (float) (position - index);
        float weight = Mth.clamp((movement - MOVEMENT_THRESHOLD) / (FULL_MOVEMENT - MOVEMENT_THRESHOLD), 0F, 1F);
        float[][] current = clip.frames[index];
        float[][] next = clip.frames[(index + 1) % clip.frames.length];
        for (int i = 0; i < parts.length; i++) {
            var part = parts[i];
            float[] a = current[i], b = next[i];
            part.x += weight * Mth.lerp(mix, a[0], b[0]);
            part.y += weight * Mth.lerp(mix, a[1], b[1]);
            part.z += weight * Mth.lerp(mix, a[2], b[2]);
            float pitch = Mth.lerp(mix, a[3], b[3]);
            float yaw = Mth.lerp(mix, a[4], b[4]);
            float roll = Mth.lerp(mix, a[5], b[5]);
            // The entity's head look was applied by setupAnim just before this pass.
            if (part == head) {
                part.xRot += weight * pitch;
                part.yRot += weight * yaw;
                part.zRot += weight * roll;
            } else {
                part.xRot = Mth.lerp(weight, part.xRot, pitch);
                part.yRot = Mth.lerp(weight, part.yRot, yaw);
                part.zRot = Mth.lerp(weight, part.zRot, roll);
            }
        }
    }
}
