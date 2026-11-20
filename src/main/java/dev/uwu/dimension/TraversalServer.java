package dev.uwu.dimension;

import dev.uwu.dimension.traversal.TraversalCue;
import dev.uwu.dimension.traversal.TraversalCue.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Isolated level-local prototype. All entry points belong on the logical server thread.
 * The owner supplies tick, input, lifecycle, and cue delivery at integration time.
 */
public final class TraversalServer {
    private static final double CLIMB_HEIGHT = 8;
    private static final double HOOK_REACH = 2.35;
    private static final double GRAPPLE_REACH = 18;
    private static final double CLIMB_SPEED = .82;
    private static final double PULL_SPEED = .95;
    private static final double CONTACT_DEPTH = .60;
    private static final int SLOW_AMPLIFIER = 0;
    private static final int PATCH_PULSE = 4;

    private final ServerLevel level;
    private final Consumer<TraversalCue> emit;
    // Respawn reuses the UUID and network entity ID. State belongs to the live player instance.
    private final Map<ServerPlayer, Motion> motions = new IdentityHashMap<>();
    private final Set<ServerPlayer> grappleMode = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Patch> patches = new ArrayList<>();
    private final Map<ServerPlayer, Long> lastActivation = new IdentityHashMap<>();
    private final Map<ServerPlayer, Long> lastToggle = new IdentityHashMap<>();

    private static final class Motion {
        final TraversalCue cue;
        final BlockPos block;
        final double startingY;
        boolean appliedMotion;

        Motion(TraversalCue cue, BlockPos block, double startingY) {
            this.cue = cue;
            this.block = block;
            this.startingY = startingY;
        }
    }

    private record Patch(ServerPlayer owner, TraversalCue cue, BlockPos support) {
        Vec3 center() { return cue.type() == Type.MOON_HOOK ? cue.anchor() : cue.origin(); }
        Direction face() { return cue.type() == Type.MOON_HOOK ? cue.face() : Direction.UP; }
        long expiry() { return cue.startTick() + TraversalCue.PATCH_TICKS; }
    }

    public TraversalServer(ServerLevel level, Consumer<TraversalCue> emit) {
        this.level = level;
        this.emit = emit;
    }

    public void toggle(ServerPlayer player) {
        if (!available(player) || !holds(player, Arsenal.Relic.FROST_WHIP)
                || (!grappleMode.contains(player) && combatBusy(player, false))
                || !newPress(lastToggle, player)) return;
        if (grappleMode.remove(player)) {
            stop(player, true);
            signal(player, Type.MODE_OFF);
        } else {
            grappleMode.add(player);
            signal(player, Type.MODE_ON);
        }
    }

    public void activate(ServerPlayer player) {
        if (!available(player)) return;
        boolean moon = holds(player, Arsenal.Relic.VESPER_SCYTHE);
        if (!moon && !(holds(player, Arsenal.Relic.FROST_WHIP) && grappleMode.contains(player))) return;
        if (combatBusy(player, moon) || !newPress(lastActivation, player)) return;

        Motion previous = motions.get(player);
        if (previous != null) {
            if (!moon && previous.cue.type() == Type.SAKURA_CAST) {
                stop(player, true); // Pressing skill while tethered releases the whip.
                return;
            }
            // A second press can catch a fresh wall as soon as the previous hook has pulled.
            if (moon && !previous.appliedMotion && previous.cue.type() == Type.MOON_HOOK) return;
        }

        Vec3 eye = player.getEyePosition();
        double reach = moon ? HOOK_REACH : GRAPPLE_REACH;
        Vec3 end = eye.add(player.getLookAngle().scale(reach));
        BlockHitResult hit = trace(player, eye, end);
        if (hit == null || hit.isInside() || !allowed(player, hit.getBlockPos())
                || (moon && !hit.getDirection().getAxis().isHorizontal())) return;

        BlockHitResult floor = moon ? null : launchSurface(player);
        Vec3 origin = floor == null ? player.position() : floor.getLocation();
        BlockPos support = moon ? hit.getBlockPos() : floor == null ? BlockPos.containing(origin) : floor.getBlockPos();
        if (!allowed(player, support) || !allowed(player, BlockPos.containing(origin))) return;
        int windup = moon ? 2 : Math.max(2, (int)Math.ceil(eye.distanceTo(hit.getLocation()) / 3));
        int lifetime = moon ? 22 : 36;
        TraversalCue cue = new TraversalCue(player.getId(), moon ? Type.MOON_HOOK : Type.SAKURA_CAST,
                level.getGameTime(), origin, hit.getLocation(), hit.getDirection(), windup, lifetime);
        stop(player, true);
        motions.put(player, new Motion(cue, hit.getBlockPos(), player.getY()));
        patches.add(new Patch(player, cue, support));
        emit.accept(cue);
    }

    private boolean combatBusy(ServerPlayer player, boolean moon) {
        return player.isUsingItem() || player.swinging || player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem())
                || (moon && player.getPersistentData().getLong("uwu.scythe_flight") > level.getGameTime());
    }

    private static boolean holds(ServerPlayer player, Arsenal.Relic weapon) {
        return player.getMainHandItem().is(Arsenal.WEAPONS.get(weapon).get());
    }

    private boolean current(ServerPlayer player) {
        return player.level() == level && level.getEntity(player.getId()) == player;
    }

    private boolean available(ServerPlayer player) {
        return current(player) && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isPassenger() && !player.isSleeping() && !player.isFallFlying()
                && !player.getAbilities().flying && !player.getPersistentData().contains(GuardianFlight.STATE);
    }

    private boolean newPress(Map<ServerPlayer, Long> ledger, ServerPlayer player) {
        long now = level.getGameTime();
        Long previous = ledger.put(player, now);
        return previous == null || previous.longValue() != now;
    }

    private boolean allowed(ServerPlayer player, BlockPos pos) {
        return level.hasChunkAt(pos) && !level.isOutsideBuildHeight(pos)
                && level.getWorldBorder().isWithinBounds(pos) && SakuraAccess.mayInteract(player, pos);
    }

    private BlockHitResult launchSurface(ServerPlayer player) {
        Vec3 feet = player.position();
        BlockHitResult support = trace(player, feet.add(0, .10, 0), feet.add(0, -.60, 0));
        return support != null && !support.isInside() && support.getDirection() == Direction.UP ? support : null;
    }

    /** A block collider ray. Fluids and entities are never latch candidates. */
    private BlockHitResult trace(ServerPlayer player, Vec3 start, Vec3 end) {
        if (!loaded(new AABB(start, end))) return null;
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private boolean loaded(AABB area) {
        return level.hasChunksAt(BlockPos.containing(area.minX, area.minY, area.minZ),
                BlockPos.containing(area.maxX, area.maxY, area.maxZ));
    }

    public void tick() {
        long now = level.getGameTime();
        lastActivation.values().removeIf(t -> t < now);
        lastToggle.values().removeIf(t -> t < now);
        grappleMode.removeIf(player -> {
            if (!current(player)) return true;
            if (available(player) && holds(player, Arsenal.Relic.FROST_WHIP)) return false;
            signal(player, Type.MODE_OFF);
            return true;
        });
        for (var it = motions.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            ServerPlayer player = entry.getKey();
            if (!current(player)) {
                it.remove();
                continue;
            }
            Motion motion = entry.getValue();
            boolean moon = motion.cue.type() == Type.MOON_HOOK;
            boolean canContinue = available(player) && !combatBusy(player, moon)
                    && holds(player, moon ? Arsenal.Relic.VESPER_SCYTHE : Arsenal.Relic.FROST_WHIP)
                    && (moon || grappleMode.contains(player));
            if (canContinue && advance(player, motion)) continue;
            it.remove();
            finish(player, motion, canContinue);
        }
        for (var it = patches.iterator(); it.hasNext();) {
            Patch patch = it.next();
            ServerPlayer owner = patch.owner();
            if (now >= patch.expiry() || !current(owner) || !owner.isAlive()) {
                it.remove();
            } else if ((patch.expiry() - now) % PATCH_PULSE == 0 && allowed(owner, patch.support())) {
                slow(owner, patch, Math.min(PATCH_PULSE, (int)(patch.expiry() - now)));
            }
        }
    }

    private boolean advance(ServerPlayer player, Motion motion) {
        TraversalCue cue = motion.cue;
        long age = level.getGameTime() - cue.startTick();
        if (age >= cue.lifetimeTicks() || !allowed(player, motion.block)) return false;
        Vec3 normal = Vec3.atLowerCornerOf(cue.face().getNormal());
        // Re-test the precise contact even on slabs, stairs, open doors and broken blocks.
        BlockHitResult contact = trace(player, cue.anchor().add(normal.scale(.035)), cue.anchor().subtract(normal.scale(.035)));
        if (contact == null || !contact.getBlockPos().equals(motion.block) || contact.getDirection() != cue.face()
                || contact.getLocation().distanceToSqr(cue.anchor()) > .0001) return false;
        boolean moon = cue.type() == Type.MOON_HOOK;
        if (age < cue.windupTicks()) {
            return !moon || player.getEyePosition().distanceTo(cue.anchor()) <= HOOK_REACH + .25;
        }
        Vec3 velocity;
        if (moon) {
            double remaining = motion.startingY + CLIMB_HEIGHT - player.getY();
            if (remaining <= .08) return false;
            // Maintain a real wall beside the rising player; the crest ends the stroke.
            Vec3 chest = player.position().add(0, player.getBbHeight() * .50, 0);
            BlockHitResult wall = trace(player, chest, chest.subtract(normal.scale(HOOK_REACH)));
            if (wall == null || wall.getDirection() != cue.face() || !allowed(player, wall.getBlockPos())) return false;
            velocity = new Vec3(0, Math.min(CLIMB_SPEED, remaining), 0);
        } else {
            Vec3 eye = player.getEyePosition();
            Vec3 outside = cue.anchor().add(normal.scale(.025));
            if (eye.distanceTo(outside) > GRAPPLE_REACH + 1 || !loaded(new AABB(eye, outside))
                    || trace(player, eye, outside) != null) return false;
            // Stop with the whole body on the free side of the exact hit shape.
            double clearance = switch (cue.face()) {
                case UP -> .08;
                case DOWN -> player.getBbHeight() + .08;
                default -> player.getBbWidth() / 2 + .12;
            };
            Vec3 destination = cue.anchor().add(normal.scale(clearance));
            if (cue.face().getAxis().isHorizontal()) destination = destination.add(0, -player.getBbHeight() * .55, 0);
            Vec3 delta = destination.subtract(player.position());
            if (delta.length() <= .16) return false;
            velocity = delta.normalize().scale(Math.min(PULL_SPEED, delta.length()));
        }
        AABB body = player.getBoundingBox();
        AABB sweep = body.expandTowards(velocity);
        if (!loaded(sweep) || !level.getWorldBorder().isWithinBounds(sweep)
                || sweep.minY < level.getMinBuildHeight() || sweep.maxY > level.getMaxBuildHeight()) return false;
        // A low anchor can still pull horizontally across a floor. Clip each component
        // using vanilla's body sweep so stepping against the ground does not cancel the grapple.
        velocity = Entity.collideBoundingBox(player, velocity, body, level, level.getEntityCollisions(player, sweep));
        if (velocity.lengthSqr() < .0004 || !allowed(player, BlockPos.containing(player.position().add(velocity)))) return false;
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.fallDistance = 0;
        motion.appliedMotion = true;
        return true;
    }

    private void slow(ServerPlayer owner, Patch patch, int ticks) {
        Vec3 center = patch.center();
        double radius = TraversalCue.PATCH_RADIUS;
        Vec3 normal = Vec3.atLowerCornerOf(patch.face().getNormal());
        AABB bounds = new AABB(center, center).inflate(radius);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, bounds, mob -> mob instanceof Enemy)) {
            if (!RangedRelics.enemy(owner, mob) || !allowed(owner, mob.blockPosition())) continue;
            AABB body = mob.getBoundingBox();
            Vec3 near = new Vec3(Math.clamp(center.x, body.minX, body.maxX),
                    Math.clamp(center.y, body.minY, body.maxY), Math.clamp(center.z, body.minZ, body.maxZ));
            Vec3 offset = near.subtract(center);
            double depth = offset.dot(normal);
            if (body.getCenter().subtract(center).dot(normal) < 0 || depth > CONTACT_DEPTH
                    || offset.lengthSqr() - depth * depth > radius * radius) continue;
            Vec3 from = center.add(normal.scale(.04));
            Vec3 intoMob = near.add(body.getCenter().subtract(near).scale(.1));
            if (!loaded(new AABB(from, intoMob)) || trace(owner, from, intoMob) != null) continue;
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, SLOW_AMPLIFIER, false, false));
        }
    }

    private void signal(ServerPlayer player, Type type) {
        emit.accept(stateCue(player, type));
    }

    private TraversalCue stateCue(ServerPlayer player, Type type) {
        return new TraversalCue(player.getId(), type, level.getGameTime(), player.position(),
                player.position(), Direction.UP, 0, 0);
    }

    private void finish(ServerPlayer player, Motion motion, boolean brake) {
        if (brake && motion.appliedMotion && available(player)) {
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
        }
        signal(player, Type.STOP);
    }

    private void stop(ServerPlayer player, boolean brake) {
        Motion motion = motions.remove(player);
        if (motion != null) finish(player, motion, brake);
    }

    /** An equipped stack changed. Marks already placed in the world keep their original expiry. */
    public void equipmentChanged(ServerPlayer player) {
        if (!current(player)) return;
        stop(player, true);
        if (grappleMode.remove(player)) signal(player, Type.MODE_OFF);
        lastActivation.remove(player);
        lastToggle.remove(player);
    }

    @FunctionalInterface
    public interface TrackingSink {
        void send(TraversalCue cue, boolean patchOnly);
    }

    /** Ordered cue stream for callers that only inspect traversal state. */
    public void replayTracking(ServerPlayer subject, Consumer<TraversalCue> direct) {
        replayTracking(subject, (cue, patchOnly) -> direct.accept(cue));
    }

    /** The observer may render between packets. A historical mark must never start a motion. */
    public void replayTracking(ServerPlayer subject, TrackingSink direct) {
        if (!current(subject)) return;
        direct.send(stateCue(subject, grappleMode.contains(subject) ? Type.MODE_ON : Type.MODE_OFF), false);
        Motion active = motions.get(subject);
        boolean sentPatch = false;
        for (Patch patch : patches) {
            if (patch.owner() == subject && patch.expiry() > level.getGameTime()
                    && (active == null || !patch.cue().equals(active.cue))) {
                direct.send(patch.cue(), true);
                sentPatch = true;
            }
        }
        if (active != null) direct.send(active.cue, false);
        else if (sentPatch) direct.send(stateCue(subject, Type.STOP), false);
    }

    /** Call on the old level when the player leaves, dies, or is replaced. */
    public void forget(ServerPlayer player) {
        boolean hadMotion = motions.remove(player) != null;
        boolean hadForm = grappleMode.remove(player);
        lastActivation.remove(player);
        lastToggle.remove(player);
        boolean hadMarks = patches.removeIf(patch -> patch.owner() == player);
        // A confirmed death and the later player removal can both reach this point.
        if ((hadMotion || hadForm || hadMarks) && current(player)) signal(player, Type.CLEAR);
    }
}
