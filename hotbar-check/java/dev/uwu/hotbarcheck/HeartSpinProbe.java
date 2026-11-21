package dev.uwu.hotbarcheck;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Records authoritative damage, vertical movement and delivered particle parameters for real casts. */
final class HeartSpinProbe {
    enum Trial { PLAIN, HIT, REJECTED }

    private record Heart(long tick, Vec3 offset, int count, Vec3 direction, double speed, boolean delivered) {}

    private static final ResourceLocation HEART = ResourceLocation.fromNamespaceAndPath("uwudimension", "neon_heart");
    private final ServerPlayer actor;
    private final List<Heart> hearts = new ArrayList<>();
    private Trial trial;
    private Cow target;
    private double actorY, targetY, actorRise, targetRise;
    private float startingHealth, lowestHealth;
    private long cooldownUntil;

    HeartSpinProbe(ServerPlayer actor) {
        this.actor = actor;
        var level = actor.serverLevel();
        BlockPos floor = actor.blockPosition().below();
        // The existing landing lane supports the player. Extend it under the stationary sweep witness.
        for (int x = -1; x <= 1; x++) {
            for (int z = 1; z <= 8; z++) {
                BlockPos block = floor.offset(x, 0, z);
                level.setBlockAndUpdate(block, Blocks.DEEPSLATE.defaultBlockState());
                for (int height = 1; height <= 13; height++) {
                    level.setBlockAndUpdate(block.above(height), Blocks.AIR.defaultBlockState());
                }
            }
        }
        prepare(Trial.PLAIN);
    }

    synchronized void prepare(Trial next) {
        if (target != null) target.discard();
        Vec3 origin = actor.position();
        target = EntityType.COW.create(actor.serverLevel());
        require(target != null, "Could not create the Spin sweep witness");
        // NoAI also disables vanilla travel. Remove decisions while keeping gravity and knockback.
        target.goalSelector.removeAllGoals(goal -> true);
        target.targetSelector.removeAllGoals(goal -> true);
        require(target.isControlledByLocalInstance(), "Spin witness must use normal server physics");
        target.setPersistenceRequired();
        target.setInvulnerable(next == Trial.REJECTED);
        target.moveTo(origin.x, origin.y, origin.z + 2.25, 0, 0);
        require(actor.serverLevel().addFreshEntity(target), "Could not add the Spin sweep witness");
        hearts.clear();
        actorY = origin.y;
        targetY = target.getY();
        actorRise = targetRise = 0;
        startingHealth = lowestHealth = target.getHealth();
        trial = next;
    }

    synchronized boolean prepared(Trial expected) { return trial == expected; }

    synchronized void observeTick(ServerPlayer player) {
        if (player != actor || target == null) return;
        actorRise = Math.max(actorRise, actor.getY() - actorY);
        targetRise = Math.max(targetRise, target.getY() - targetY);
        lowestHealth = Math.min(lowestHealth, target.getHealth());
        cooldownUntil = actor.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                .getLong("uwu.dawnpetal_heart_spin_until");
    }

    synchronized void particle(ServerPlayer viewer, ParticleOptions options, double x, double y, double z,
                               int count, double dx, double dy, double dz, double speed, boolean delivered) {
        if (viewer != actor || trial == null || !HEART.equals(BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()))) return;
        hearts.add(new Heart(actor.serverLevel().getGameTime(), new Vec3(x, y, z).subtract(actor.position()),
                count, new Vec3(dx, dy, dz), speed, delivered));
    }

    synchronized void verify(Trial expected, long cueStart) {
        require(trial == expected, "Unexpected observed Spin trial " + trial);
        require(actorRise < .20, "Dawnpetal skill lifted its wielder: rise=" + actorRise);
        if (trial == Trial.PLAIN) {
            require(hearts.isEmpty(), "Ordinary Spin sent " + hearts.size() + " heart packets");
            // Vanilla damage can give a grounded victim a normal .4-block/tick knockback impulse.
            require(lowestHealth < startingHealth && targetRise < 1.5 && cooldownUntil == 0,
                    "Ordinary Spin differed from normal damage and knockback: health=" + lowestHealth
                            + " rise=" + targetRise + " heart cooldown=" + cooldownUntil);
            return;
        }
        require(cooldownUntil == cueStart + 120, "Heart Spin did not set its own 120-tick cooldown");
        checkRing(cueStart);
        if (trial == Trial.HIT) {
            require(lowestHealth < startingHealth && targetRise >= 9.3 && targetRise <= 10.7,
                    "Heart Spin did not produce the expected damage and 10-block apex: health=" + lowestHealth
                            + " rise=" + targetRise);
        } else {
            require(lowestHealth == startingHealth && targetRise < .20,
                    "Rejected Heart Spin damage still changed health or launched the target: health=" + lowestHealth
                            + " rise=" + targetRise);
        }
    }

    synchronized void printResult() {
        System.out.printf(java.util.Locale.ROOT,
                "DAWNPETAL HEART SPIN HIT PASS 12 outward hearts at +8t, target rise=%.3f blocks, caster rise=%.3f%n",
                targetRise, actorRise);
    }

    synchronized void finish() {
        if (target != null) target.discard();
        target = null;
        trial = null;
    }

    private void checkRing(long cueStart) {
        require(hearts.size() == 12, "Heart Spin did not send exactly one 12-point ring: packets=" + hearts.size());
        var angles = new ArrayList<Double>();
        for (Heart heart : hearts) {
            require(heart.delivered && heart.count == 0 && heart.tick - cueStart == 8,
                    "Heart point was not individually sent on the first sweep: " + heart + ", cue=" + cueStart);
            require(Math.abs(heart.offset.horizontalDistance() - .75) < .025
                            && (Math.abs(heart.offset.y - 1) < .025 || Math.abs(heart.offset.y - 1.12) < .025),
                    "Heart did not begin at the expected ring: " + heart.offset);
            Vec3 radial = heart.offset.multiply(1, 0, 1).normalize();
            Vec3 direction = heart.direction.multiply(1, 0, 1);
            require(radial.dot(direction) > .99 && Math.abs(direction.lengthSqr() - 1) < .02
                            && Math.abs(heart.direction.y - .18) < .02 && Math.abs(heart.speed - .46) < .025,
                    "Heart did not use its exact outward velocity: " + heart);
            angles.add(Math.atan2(radial.z, radial.x));
        }
        angles.sort(Double::compare);
        for (int index = 0; index < angles.size(); index++) {
            double next = index + 1 < angles.size() ? angles.get(index + 1) : angles.getFirst() + Math.TAU;
            require(Math.abs(next - angles.get(index) - Math.TAU / 12) < .025,
                    "Heart ring did not cover twelve evenly spaced directions: " + angles);
        }
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
