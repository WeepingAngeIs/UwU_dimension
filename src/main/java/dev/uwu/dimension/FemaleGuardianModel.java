package dev.uwu.dimension;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** The artist's Blockbench boxes with their per-face skin baked into a cube atlas. */
final class FemaleGuardianModel {
    private record Box(String name, float[] position, float[] size, int tile) {}
    private record Bone(String name, float[] pivot, Box[] cubes) {}
    private record Geometry(int width, int height, Bone[] bones) {}
    private static final Geometry GEOMETRY = load();

    private static Geometry load() {
        var stream = Objects.requireNonNull(FemaleGuardianModel.class.getResourceAsStream(
                "/assets/uwudimension/geometry/guardian_bones.json"));
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Geometry.class);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Cannot read guardian geometry", ex);
        }
    }

    static LayerDefinition layer() {
        var mesh = new MeshDefinition();
        for (Bone bone : GEOMETRY.bones) {
            var parent = mesh.getRoot().addOrReplaceChild(bone.name, CubeListBuilder.create(), offset(bone.pivot));
            for (Box box : bone.cubes) {
                var cube = CubeListBuilder.create().texOffs((box.tile % 4) * 64, (box.tile / 4) * 32)
                        .addBox(0, 0, 0, 16, 16, 16);
                parent.addOrReplaceChild(box.name, cube, offset(box.position));
            }
        }
        return LayerDefinition.create(mesh, GEOMETRY.width, GEOMETRY.height);
    }

    static ModelPart sizeCubes(ModelPart root) {
        for (Bone bone : GEOMETRY.bones) {
            var parent = root.getChild(bone.name);
            for (Box box : bone.cubes) {
                var part = parent.getChild(box.name);
                part.xScale = box.size[0] / 16;
                part.yScale = box.size[1] / 16;
                part.zScale = box.size[2] / 16;
            }
        }
        return root;
    }

    private static PartPose offset(float[] p) {
        return PartPose.offset(p[0], p[1], p[2]);
    }
}
