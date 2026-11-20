package dev.uwu.dimension.traversal.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.traversal.TraversalCue;
import dev.uwu.dimension.traversal.TraversalPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

/** One instance per client session. The shared pose owner supplies the actual world-space lash socket. */
public final class TraversalView {
    @FunctionalInterface
    public interface LashSocket {
        /** Return null when the approved held model is hidden or its socket is unavailable. */
        Vec3 position(Player player, float partialTick);
    }

    private static final RenderType MOON = texture("traversal/moon");
    private static final RenderType FLOWER = texture("traversal/flower");
    private static final RenderType LASH = texture("entity/ribbon_lash");
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private final Map<Integer, TraversalCue> motions = new HashMap<>();
    private final List<TraversalCue> patches = new ArrayList<>();
    private boolean grappleForm;
    private ClientLevel level;
    private Player localPlayer;

    private static RenderType texture(String path) {
        return RenderType.entityTranslucent(ResourceLocation.fromNamespaceAndPath("uwudimension",
                "textures/" + path + ".png"));
    }

    private void updateLevel() {
        var mc = Minecraft.getInstance();
        if (level != mc.level) {
            motions.clear();
            patches.clear();
            grappleForm = false;
            level = mc.level;
        } else if (localPlayer != mc.player && localPlayer != null) {
            // A same-level respawn can replace the local object while reusing its integer ID.
            clearActor(localPlayer.getId());
            grappleForm = false;
        }
        localPlayer = mc.player;
    }

    private void clearActor(int actorId) {
        motions.remove(actorId);
        patches.removeIf(old -> old.actorId() == actorId);
    }

    public void accept(TraversalPackets.Effect effect) {
        updateLevel();
        if (level == null || !level.dimension().location().equals(effect.dimension())) return;
        TraversalCue cue = effect.cue();
        switch (cue.type()) {
            case MODE_ON, MODE_OFF -> {
                var player = Minecraft.getInstance().player;
                if (player != null && player.getId() == cue.actorId()) {
                    grappleForm = cue.type() == TraversalCue.Type.MODE_ON;
                    player.displayClientMessage(grappleForm
                        ? Component.translatableWithFallback("traversal.uwudimension.grapple", "Snowkiss · Grapple")
                        : Component.translatableWithFallback("traversal.uwudimension.lash", "Snowkiss · Lash"), true);
                }
            }
            case STOP -> motions.remove(cue.actorId());
            case CLEAR -> {
                clearActor(cue.actorId());
                var player = Minecraft.getInstance().player;
                if (player != null && player.getId() == cue.actorId()) grappleForm = false;
            }
            case MOON_HOOK, SAKURA_CAST -> {
                long age = level.getGameTime() - cue.startTick();
                // The new observer can render before the rest of the tracking snapshot arrives.
                // A historical packet retires only its own motion, never a newer swing by this actor.
                if (effect.patchOnly()) motions.remove(cue.actorId(), cue);
                // A tracked patch outlives its motion. Re-delivery after STOP must not restart it.
                if (age >= TraversalCue.PATCH_TICKS || patches.contains(cue)) return;
                patches.add(cue);
                if (effect.patchOnly()) return;
                TraversalCue active = motions.get(cue.actorId());
                if (age < cue.lifetimeTicks() && (active == null || active.startTick() <= cue.startTick())) {
                    motions.put(cue.actorId(), cue);
                }
            }
        }
    }

    public void tick() {
        updateLevel();
        if (level == null) return;
        long tick = level.getGameTime();
        motions.values().removeIf(c -> tick - c.startTick() >= c.lifetimeTicks());
        patches.removeIf(c -> tick - c.startTick() >= TraversalCue.PATCH_TICKS);
        for (var cue : patches) {
            long age = tick - cue.startTick();
            if (age < 0 || age % 6 != 0) continue;
            boolean moon = moon(cue);
            Vec3 normal = Vec3.atLowerCornerOf((moon ? cue.face() : Direction.UP).getNormal());
            Vec3 side = horizontal(normal);
            Vec3 up = normal.cross(side);
            double a = age * .49 + cue.actorId();
            Vec3 at = (moon ? cue.anchor() : cue.origin()).add(normal.scale(.04))
                .add(side.scale(.72 * Math.cos(a))).add(up.scale(.72 * Math.sin(a)));
            level.addParticle(moon ? ParticleTypes.END_ROD : ParticleTypes.CHERRY_LEAVES,
                at.x, at.y, at.z, normal.x * .012, .015, normal.z * .012);
        }
    }

    /** The animation owner aims the existing blade tip here during the short contact hold. */
    public Vec3 bladeContact(int actorId, float partialTick) {
        updateLevel();
        var cue = motions.get(actorId);
        if (cue == null || !moon(cue)) return null;
        double age = level.getGameTime() - cue.startTick() + partialTick;
        return age >= 0 && age <= cue.windupTicks() ? cue.anchor() : null;
    }

    /** World-space rotation about the wrapped grip. The caller positions the arm to put
     * the grip within one blade length of the hit, then applies this turn to the held item. */
    public Quaternionf bladeRotation(int actorId, float partialTick, Vec3 grip, Vec3 currentTip) {
        Vec3 target = bladeContact(actorId, partialTick);
        if (target == null) return new Quaternionf();
        Vec3 from = currentTip.subtract(grip).normalize();
        Vec3 to = target.subtract(grip).normalize();
        if (from.lengthSqr() == 0 || to.lengthSqr() == 0) return new Quaternionf();
        return new Quaternionf().rotationTo((float)from.x, (float)from.y, (float)from.z,
                (float)to.x, (float)to.y, (float)to.z);
    }

    /** Optional HUD study. Call only if enabled and with a free rectangle beside the left hotbar edge. */
    public void drawMode(GuiGraphics gui, int x, int y) {
        updateLevel();
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !mc.player.getMainHandItem()
                .is(Arsenal.WEAPONS.get(Arsenal.Relic.FROST_WHIP).get())) return;
        var label = grappleForm ? Component.translatableWithFallback("traversal.uwudimension.form", "Grapple")
                                : Component.translatableWithFallback("traversal.uwudimension.normal", "Lash");
        gui.fill(x, y, x + mc.font.width(label) + 8, y + 13, 0xBA261D34);
        gui.drawString(mc.font, label, x + 4, y + 3, grappleForm ? 0xffffc6de : 0xffc7edff);
    }

    public void render(RenderLevelStageEvent event, LashSocket socket) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        updateLevel();
        if (level == null || (motions.isEmpty() && patches.isEmpty())) return;
        var mc = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var buffers = mc.renderBuffers().bufferSource();
        var pose = event.getPoseStack().last();
        var lashes = new ArrayList<VisibleLash>();
        if (socket != null) {
            for (var cue : motions.values()) {
                if (moon(cue) || !(level.getEntity(cue.actorId()) instanceof Player player)) continue;
                double age = level.getGameTime() - cue.startTick() + partial;
                if (age < 0 || age >= cue.lifetimeTicks()) continue;
                Vec3 root = socket.position(player, partial);
                if (root != null) lashes.add(new VisibleLash(cue, root, age));
            }
        }
        // Keep each texture's quads contiguous for the shared buffer source.
        for (boolean isMoon : new boolean[]{true, false}) {
            RenderType type = isMoon ? MOON : FLOWER;
            var out = buffers.getBuffer(type);
            for (var cue : patches) {
                if (moon(cue) != isMoon) continue;
                double age = level.getGameTime() - cue.startTick() + partial;
                if (age < 0 || age >= TraversalCue.PATCH_TICKS) continue;
                int alpha = (int)(255 * Mth.clamp((TraversalCue.PATCH_TICKS - age) / 12, 0, 1));
                decal(out, pose, camera, isMoon ? cue.anchor() : cue.origin(), isMoon ? cue.face() : Direction.UP,
                    TraversalCue.PATCH_RADIUS, alpha << 24 | 0xffffff);
            }
            if (!isMoon) {
                for (var lash : lashes) {
                    var cue = lash.cue();
                    if (lash.age() >= cue.windupTicks()) decal(out, pose, camera, cue.anchor(), cue.face(), .20, 0xffffffff);
                    blossoms(out, pose, camera, lash.root(), cue, lash.age());
                }
            }
            buffers.endBatch(type);
        }
        var out = buffers.getBuffer(LASH);
        for (var lash : lashes) {
            double progress = Mth.clamp(lash.age() / Math.max(1, lash.cue().windupTicks()), 0, 1);
            ribbon(out, pose, camera, lash.root(), lash.cue().anchor(), progress);
        }
        buffers.endBatch(LASH);
    }

    private record VisibleLash(TraversalCue cue, Vec3 root, double age) {}

    boolean hasLash(Player actor, float partial) {
        updateLevel();
        if (actor == null || actor.level() != level) return false;
        var cue = motions.get(actor.getId());
        if (cue == null || moon(cue)) return false;
        double age = level.getGameTime() - cue.startTick() + partial;
        return age >= 0 && age < cue.lifetimeTicks();
    }

    /** Only the local hand's lash. All world marks and tracked third-person lashes draw normally. */
    void handLash(Player actor, float partial, Vec3 tip, Vec3 camera) {
        if (!hasLash(actor, partial)) return;
        var cue = motions.get(actor.getId());
        double age = level.getGameTime() - cue.startTick() + partial;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var pose = new PoseStack().last();
        var flowers = buffers.getBuffer(FLOWER);
        if (age >= cue.windupTicks()) decal(flowers, pose, camera, cue.anchor(), cue.face(), .20, 0xffffffff);
        blossoms(flowers, pose, camera, tip, cue, age);
        buffers.endBatch(FLOWER);
        ribbon(buffers.getBuffer(LASH), pose, camera, tip, cue.anchor(),
            Mth.clamp(age / Math.max(1, cue.windupTicks()), 0, 1));
        buffers.endBatch(LASH);
    }

    private static boolean moon(TraversalCue cue) { return cue.type() == TraversalCue.Type.MOON_HOOK; }

    private static Vec3 horizontal(Vec3 normal) {
        return Math.abs(normal.y) > .99 ? new Vec3(1, 0, 0) : UP.cross(normal).normalize();
    }

    private static void decal(VertexConsumer out, PoseStack.Pose pose, Vec3 camera, Vec3 at, Direction face,
                              double radius, int tint) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 u = horizontal(normal).scale(radius);
        Vec3 v = normal.cross(u);
        Vec3 center = at.subtract(camera).add(normal.scale(.018));
        quad(out, pose, center, u, v, tint);
    }

    /** Three traveling blossoms sit off the lash so the last pixel at the latch stays visible. */
    private static void blossoms(VertexConsumer out, PoseStack.Pose pose, Vec3 camera, Vec3 root,
                                 TraversalCue cue, double age) {
        double extension = Mth.clamp(age / Math.max(1, cue.windupTicks()), 0, 1);
        Vec3 travel = cue.anchor().subtract(root);
        Vec3 lateral = horizontal(travel.normalize());
        for (int i = 0; i < 3; i++) {
            double phase = (age / 13 + i / 3.0) % 1;
            double t = (.14 + .68 * phase) * extension;
            Vec3 center = root.add(travel.scale(t)).add(lateral.scale(.18 * Math.sin(phase * 6.28)))
                .add(0, .08 + .10 * phase, 0).subtract(camera);
            Vec3 normal = center.normalize().reverse();
            Vec3 u = horizontal(normal).scale(.075);
            quad(out, pose, center, u, normal.cross(u), 0xd9ffffff);
        }
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose, Vec3 center, Vec3 u, Vec3 v, int tint) {
        vertex(out, pose, center.subtract(u).subtract(v), 0, 1, tint);
        vertex(out, pose, center.add(u).subtract(v), 1, 1, tint);
        vertex(out, pose, center.add(u).add(v), 1, 0, tint);
        vertex(out, pose, center.subtract(u).add(v), 0, 0, tint);
    }

    private static void ribbon(VertexConsumer out, PoseStack.Pose pose, Vec3 camera, Vec3 root, Vec3 anchor,
                               double progress) {
        Vec3 travel = anchor.subtract(root);
        Vec3 side = travel.cross(camera.subtract(root)).normalize();
        if (side.lengthSqr() < .01) side = horizontal(travel.normalize());
        Vec3 edge = side.scale(.052);
        Vec3 previous = root.subtract(camera);
        int steps = Math.max(1, (int)Math.ceil(travel.length() * progress * 2));
        for (int i = 1; i <= steps; i++) {
            double t = i / (double)steps;
            double ripple = .22 * (1 - progress) * Math.sin(2 * Math.PI * t) * Math.sin(Math.PI * t);
            Vec3 next = root.subtract(camera).add(travel.scale(t * progress)).add(side.scale(ripple));
            vertex(out, pose, previous.subtract(edge), 0, 0, 0xffbcefff);
            vertex(out, pose, previous.add(edge), 0, 1, 0xffbcefff);
            vertex(out, pose, next.add(edge), 1, 1, 0xfff1d7ff);
            vertex(out, pose, next.subtract(edge), 1, 0, 0xfff1d7ff);
            previous = next;
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 p, float u, float v, int color) {
        out.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(color).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }
}
