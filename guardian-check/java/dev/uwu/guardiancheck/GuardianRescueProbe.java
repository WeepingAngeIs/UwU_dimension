package dev.uwu.guardiancheck;

import static dev.uwu.guardiancheck.GuardianArena.require;

import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.GuardianFlight;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.People;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

/** Compares two real pursuits by the same village guard over an identical open route. */
final class GuardianRescueProbe {
    private enum Phase { READY, ORDINARY, RESET, RESCUE, THROW, AFTER_THROW, LOST, DONE }

    private final ServerLevel level;
    private final GuardianArena arena;
    private final BlockPos court;
    private final BlockPos threat;
    private final Method rushing;
    private Phase phase = Phase.READY;
    private long began;
    private BlossomWarden guard;
    private Keeper protectedKeeper;
    private Zombie offender;
    private Vec3 previous;
    private boolean observedRoute, observedBurst;
    private double peakStep, peakCommand;
    private long ordinaryTargetedAt, ordinaryTicks, rescueTicks;
    private double ordinaryPeak;

    GuardianRescueProbe(ServerLevel level) throws ReflectiveOperationException {
        this.level = level;
        arena = new GuardianArena(level);
        court = level.getSharedSpawnPos().offset(-256, 16, 0);
        threat = court.east(20);
        arena.floor(court, -4, 25, 6);
        rushing = BlossomWarden.class.getDeclaredMethod("rescueSprinting");
        rushing.setAccessible(true);
        began = level.getGameTime();
    }

    boolean tick() throws ReflectiveOperationException {
        switch (phase) {
            case READY -> {
                require(elapsed() < 250, "Guardian rescue floor never became entity-ticking");
                if (!arena.entitiesReady()) return false;
                guard = arena.natural(court);
                protectedKeeper = People.PETAL_KEEPER.get().create(level);
                require(protectedKeeper != null, "Could not create the protected Keeper");
                protectedKeeper.bindVillage(court);
                protectedKeeper.setNoAi(true);
                placeKeeper();
                require(level.addFreshEntity(protectedKeeper), "Could not spawn the protected Keeper");
                startChase(Phase.ORDINARY);
            }
            case ORDINARY -> {
                check(elapsed() <= 200, "Ordinary hostile did not reach the grab control");
                check(!burst(), "The ordinary hostile triggered a rescue without a Keeper hit");
                if (ordinaryTargetedAt == 0 && offender.getUUID().equals(guard.offender()))
                    ordinaryTargetedAt = level.getGameTime();
                observeTravel();
                if (!guard.gripping()) return false;
                check(observedRoute && traveled() >= 14 && peakCommand > 0 && peakCommand <= 1.01,
                        "Ordinary control did not follow its normal navigation path");
                check(ordinaryTargetedAt > 0, "Ordinary pursuit did not set its target");
                ordinaryTicks = level.getGameTime() - ordinaryTargetedAt;
                ordinaryPeak = peakStep;
                offender.discard();
                next(Phase.RESET);
            }
            case RESET -> {
                check(elapsed() <= 20, "Ordinary target was not released when removed");
                if (guard.offender() != null) return false;
                placeKeeper();
                startChase(Phase.RESCUE);
                protectedKeeper.setInvulnerable(true);
                require(!offender.doHurtTarget(protectedKeeper) && !burst(), "Rejected Keeper damage activated rescue");
                protectedKeeper.setInvulnerable(false);
                hitKeeper();
            }
            case RESCUE -> {
                check(elapsed() <= 55, "Boosted Guardian did not reach its grab before the rescue deadline");
                observedBurst |= burst();
                observeTravel();
                if (!guard.gripping()) return false;
                rescueTicks = elapsed();
                check(observedBurst && observedRoute && traveled() >= 14, "Keeper hit did not produce a routed rescue");
                check(rescueTicks * 2 < ordinaryTicks && peakStep > ordinaryPeak * 2,
                        "Rescue did not produce materially faster movement");
                check(peakCommand >= 9.9 && peakCommand <= 10.0001, "Clear route did not use the 10x capped rescue speed");
                check(!burst() && guard.getNavigation().isDone(), "The speed boost survived the start of the grab");
                System.out.printf(java.util.Locale.ROOT,
                        "GUARDIAN RESCUE TRAVEL ordinary=%dt rescue=%dt peakStep=%.3f->%.3f blocks/tick nav=%.2fx%n",
                        ordinaryTicks, rescueTicks, ordinaryPeak, peakStep, peakCommand);
                next(Phase.THROW);
            }
            case THROW -> {
                check(elapsed() <= 25, "The original grab did not lead to flight");
                check(!burst(), "The speed boost returned during holding or throwing");
                if (!offender.getPersistentData().contains(GuardianFlight.STATE)) return false;
                check(guard.throwing() || guard.offender() == null, "Flight started outside the original throw transition");
                offender.discard();
                next(Phase.AFTER_THROW);
            }
            case AFTER_THROW -> {
                check(elapsed() <= 25, "Guardian still targeted its previous offender");
                if (guard.offender() != null) return false;
                placeKeeper();
                startChase(Phase.LOST);
                hitKeeper();
                offender.discard();
            }
            case LOST -> {
                check(!burst(), "Rescue boost remained after the attacker was removed");
                check(elapsed() <= 20, "Removed rescue attacker stayed assigned");
                if (guard.offender() != null) return false;
                protectedKeeper.discard();
                guard.discard();
                System.out.println("GUARDIAN RESCUE PASS accepted Keeper hit, navigation, grab and throw, rejected hit and target loss");
                next(Phase.DONE);
                return true;
            }
            case DONE -> { return true; }
        }
        return false;
    }

    private void startChase(Phase phase) {
        GuardianArena.move(guard, court);
        offender = EntityType.ZOMBIE.create(level);
        require(offender != null, "Could not create the village attacker");
        move(offender, threat);
        offender.setNoAi(true);
        offender.setPersistenceRequired();
        require(level.addFreshEntity(offender), "Could not spawn the village attacker");
        previous = guard.position();
        observedRoute = observedBurst = false;
        peakCommand = peakStep = 0;
        next(phase);
    }

    private void hitKeeper() throws ReflectiveOperationException {
        float health = protectedKeeper.getHealth() + protectedKeeper.getAbsorptionAmount();
        protectedKeeper.invulnerableTime = 0;
        check(offender.isWithinMeleeAttackRange(protectedKeeper), "Keeper is out of the attacker's melee reach");
        check(offender.doHurtTarget(protectedKeeper)
                && protectedKeeper.getHealth() + protectedKeeper.getAbsorptionAmount() < health,
                "Fixture did not deliver real accepted damage to the Keeper");
        check(burst() && offender.getUUID().equals(guard.offender()), "The live Keeper hit did not select its attacker immediately");
    }

    private void observeTravel() {
        Vec3 position = guard.position();
        double step = position.subtract(previous).horizontalDistance();
        check(step < 8, "Guardian teleported instead of traveling on the open route");
        previous = position;
        var path = guard.getNavigation().getPath();
        if (offender.getUUID().equals(guard.offender()) && path != null && !path.isDone() && path.canReach()) {
            observedRoute = true;
            peakStep = Math.max(peakStep, step);
            peakCommand = Math.max(peakCommand, guard.getMoveControl().getSpeedModifier());
        }
    }

    private void placeKeeper() {
        move(protectedKeeper, threat.south());
        protectedKeeper.setDeltaMovement(Vec3.ZERO);
    }

    private static void move(LivingEntity entity, BlockPos pos) {
        Vec3 center = Vec3.atBottomCenterOf(pos);
        entity.moveTo(center.x, center.y, center.z, 0, 0);
    }

    private boolean burst() throws ReflectiveOperationException { return (Boolean) rushing.invoke(guard); }
    private double traveled() { return guard.position().subtract(Vec3.atBottomCenterOf(court)).horizontalDistance(); }
    private long elapsed() { return level.getGameTime() - began; }
    private void next(Phase phase) { this.phase = phase; began = level.getGameTime(); }

    private void check(boolean valid, String detail) {
        if (valid) return;
        throw new AssertionError(detail + " phase=" + phase + " elapsed=" + elapsed() + " guard=" + guard.position()
                + " target=" + guard.offender() + " route=" + guard.getNavigation().getTargetPos()
                + " peakStep=" + peakStep + " peakCommand=" + peakCommand);
    }
}
