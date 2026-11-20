package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/** A server-side contact impulse, independent of block placement and shield damage handling. */
@EventBusSubscriber(modid = "uwudimension")
public final class HeartbriarBoundary {
    static final int INTERVAL = 4;
    private static final int CANDIDATE_LIMIT = 24, CONTACT_LIMIT = 8, NEIGHBOR_LIMIT = 24;
    private static final double MOTION_EPSILON = .003;
    private static final EntityTypeTest<Entity, LivingEntity> LIVING = EntityTypeTest.forClass(LivingEntity.class);
    private static final EntityTypeTest<Entity, Entity> ENTITIES = EntityTypeTest.forClass(Entity.class);
    private static final Map<ServerPlayer, Long> LAST_PULSE = new WeakHashMap<>();
    private static final Map<LivingEntity, Integer> LAST_IMPULSE = new WeakHashMap<>();

    private HeartbriarBoundary() {}

    @SubscribeEvent
    public static void useTick(LivingEntityUseItemEvent.Tick event) {
        if (event.getEntity() instanceof ServerPlayer holder
                && event.getItem().is(HeartShields.HEARTBRIAR_SHIELD.get())) {
            pulse(holder.serverLevel(), holder);
        }
    }

    static void pulse(ServerLevel level, ServerPlayer holder) {
        long now = level.getGameTime();
        if (!guarding(holder) || holder.level() != level || Math.floorMod(now + holder.getId(), INTERVAL) != 0
                || LAST_PULSE.getOrDefault(holder, Long.MIN_VALUE) == now) return;
        LAST_PULSE.put(holder, now);
        double y = HeartbriarBoundaryShape.centerY(holder.getY(), holder.getBbHeight());
        double ry = HeartbriarBoundaryShape.verticalRadius(holder.getBbHeight());
        double r = HeartbriarBoundaryShape.HORIZONTAL_RADIUS;
        AABB shell = new AABB(holder.getX() - r, y - ry, holder.getZ() - r,
                              holder.getX() + r, y + ry, holder.getZ() + r);
        var candidates = new ArrayList<LivingEntity>(CANDIDATE_LIMIT + 1);
        level.getEntities(LIVING, shell, entity -> true, candidates, CANDIDATE_LIMIT);
        long sequence = Math.floorDiv(now + holder.getId(), INTERVAL);
        int sector = Math.floorMod(sequence, 5);
        if (candidates.size() >= CANDIDATE_LIMIT && sector != 0) {
            // Saturated scenes alternate a whole-box pass and four quadrants.
            // Keep alliance and terrain work outside the engine's bounded query.
            boolean east = (sector & 1) != 0, south = (sector & 2) != 0;
            AABB quadrant = new AABB(east ? holder.getX() : shell.minX, shell.minY, south ? holder.getZ() : shell.minZ,
                east ? shell.maxX : holder.getX(), shell.maxY, south ? shell.maxZ : holder.getZ());
            candidates.clear();
            level.getEntities(LIVING, quadrant, entity -> true, candidates, CANDIDATE_LIMIT);
        }
        int count = Math.min(candidates.size(), CANDIDATE_LIMIT);
        if (count == 0) return;
        var neighbors = new ArrayList<Entity>(NEIGHBOR_LIMIT + 2);
        int start = Math.floorMod(sequence, count);
        int attempts = 0;
        for (int i = 0; i < count && attempts < CONTACT_LIMIT; i++) {
            LivingEntity target = candidates.get((start + i) % count);
            if (!eligible(holder, target)) continue;
            int since = target.tickCount - LAST_IMPULSE.getOrDefault(target, target.tickCount - INTERVAL);
            if (since >= 0 && since < INTERVAL) continue;
            var direction = push(holder, target.getBoundingBox());
            double speed = Math.hypot(direction.x(), direction.z());
            if (speed <= MOTION_EPSILON) continue;
            ++attempts;
            double nx = direction.x() / speed, nz = direction.z() / speed;
            var current = target.getDeltaMovement();
            double change = speed - (current.x * nx + current.z * nz);
            if (change <= MOTION_EPSILON) continue;
            // Replace the inward radial component; preserve tangential and vertical travel.
            double dx = current.x + nx * change, dz = current.z + nz * change;
            if (!safeStep(level, holder, target, dx, dz, neighbors)) continue;
            target.setDeltaMovement(dx, current.y, dz);
            LAST_IMPULSE.put(target, target.tickCount);
            if (target instanceof ServerPlayer) target.hurtMarked = true; // vanilla tracker also sends to the owner
            else target.hasImpulse = true;
        }
    }

    private static boolean guarding(ServerPlayer holder) {
        return holder.isAlive() && !holder.isSpectator() && holder.isBlocking()
            && holder.getUseItem().is(HeartShields.HEARTBRIAR_SHIELD.get())
            && holder.getItemInHand(holder.getUsedItemHand()).is(HeartShields.HEARTBRIAR_SHIELD.get());
    }

    private static boolean eligible(ServerPlayer holder, LivingEntity target) {
        if (target == holder || !target.isAlive() || !target.isPushable() || target.noPhysics
                || target.isSpectator() || target.isPassenger() || target.isVehicle()) return false;
        if (target instanceof ServerPlayer player) {
            if (!holder.serverLevel().getServer().isPvpAllowed()
                    || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        } else if (!(target instanceof Mob mob) || !(mob instanceof Enemy || mob.getTarget() == holder)) {
            return false;
        }
        return !HeartbriarAllies.allied(holder, target);
    }

    private static HeartbriarBoundaryShape.Push push(ServerPlayer holder, AABB box) {
        var facing = holder.getLookAngle();
        return HeartbriarBoundaryShape.outward(holder.getX(), holder.getY(), holder.getZ(), holder.getBbHeight(),
            box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, facing.x, facing.z);
    }

    private static boolean safeStep(ServerLevel level, ServerPlayer holder, LivingEntity target,
                                    double dx, double dz, List<Entity> neighbors) {
        AABB body = target.getBoundingBox();
        AABB destination = body.move(dx, 0, dz);
        AABB swept = body.expandTowards(dx, 0, dz);
        // Refuse oversized/invalid paths before any block enumeration. Vanilla
        // supplies collision shapes lazily and stops at the first obstruction.
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || swept.getXsize() > 8 || swept.getZsize() > 8
                || swept.getYsize() > 8 || !level.getWorldBorder().isWithinBounds(swept)
                || !level.hasChunksAt(BlockPos.containing(swept.minX - 1, swept.minY - 1, swept.minZ - 1),
                                     BlockPos.containing(swept.maxX + 1, swept.maxY + 1, swept.maxZ + 1))
                || level.getBlockCollisions(target, swept).iterator().hasNext()) return false;
        if (target.onGround() && !level.getBlockCollisions(target, destination.move(0, -.08, 0)).iterator().hasNext()) {
            return false;
        }
        neighbors.clear();
        level.getEntities(ENTITIES, swept.inflate(HeartbriarBoundaryShape.HORIZONTAL_RADIUS, .8,
                HeartbriarBoundaryShape.HORIZONTAL_RADIUS), entity -> true, neighbors, NEIGHBOR_LIMIT + 1);
        if (neighbors.size() > NEIGHBOR_LIMIT) return false;
        for (Entity other : neighbors) {
            if (other == target || other == holder || !other.isAlive() || other.isSpectator() || other.noPhysics) continue;
            AABB obstacle = other.getBoundingBox();
            if ((other instanceof LivingEntity || target.canCollideWith(other)) && swept.intersects(obstacle)) {
                // An existing overlap may shrink, but this pulse cannot enter a
                // new occupied space or increase overlap with another body.
                if (!body.intersects(obstacle) || overlap(destination, obstacle) > overlap(body, obstacle) + 1e-7) return false;
            }
            if (other instanceof ServerPlayer guard && guarding(guard) && eligible(guard, target)) {
                var opposite = push(guard, destination);
                if (dx * opposite.x() + dz * opposite.z() < -1e-7) return false;
            }
        }
        return true;
    }

    private static double overlap(AABB a, AABB b) {
        return Math.max(0, Math.min(a.maxX,b.maxX)-Math.max(a.minX,b.minX))
             * Math.max(0, Math.min(a.maxZ,b.maxZ)-Math.max(a.minZ,b.minZ));
    }
}
