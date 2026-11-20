package dev.uwu.dimension;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Lets hostile monsters target the village's Blossom Warden like they target an iron golem. */
public final class GuardianTargeting {
    private static final int TARGET_PRIORITY = 2;
    /** Implemented on vanilla's swell goal by a mixin, for safe pre-fuse inspection. */
    public interface GuardianSwellTarget {
        boolean uwu$hasGuardianTarget();
    }
    /** World game rule: saved per world; new worlds default to preventing creeper aggro. */
    private static GameRules.Key<GameRules.BooleanValue> creeperAggro;

    private GuardianTargeting() {
    }

    /** Called while the mod loads, before any world exists, because game rules are fixed once a world is created. */
    public static void register() {
        creeperAggro = GameRules.register("uwuGuardianCreeperAggro", GameRules.Category.MOBS, GameRules.BooleanValue.create(false));
        NeoForge.EVENT_BUS.addListener(GuardianTargeting::filterCreeperTarget);
        NeoForge.EVENT_BUS.addListener(GuardianTargeting::clearExistingCreeperTarget);
    }

    public static boolean creeperAggroOn(Level level) {
        return creeperAggro != null && level.getGameRules().getBoolean(creeperAggro);
    }

    public static void setCreeperAggro(Level level, boolean on, MinecraftServer server) {
        level.getGameRules().getRule(creeperAggro).set(on, server);
    }

    /** Reject all setTarget routes, including retaliation or goals added by other mods. */
    private static void filterCreeperTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper) || creeper.level().isClientSide
                || creeperAggroOn(creeper.level()) || !(event.getNewAboutToBeSetTarget() instanceof BlossomWarden)) return;
        // A rejected guardian target must not erase an existing player/other target.
        LivingEntity existing = creeper.getTarget();
        event.setNewAboutToBeSetTarget(existing instanceof BlossomWarden ? null : existing);
    }

    /** Defuse before Creeper.tick advances an already-primed fuse. Explicit ignition is independent. */
    private static void clearExistingCreeperTarget(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Creeper creeper) || creeper.level().isClientSide
                || creeperAggroOn(creeper.level())) return;
        boolean currentGuardian = creeper.getTarget() instanceof BlossomWarden;
        boolean cachedGuardian = false;
        for (var goal : creeper.goalSelector.getAvailableGoals()) {
            if (goal.isRunning() && goal.getGoal() instanceof GuardianSwellTarget swell && swell.uwu$hasGuardianTarget()) {
                cachedGuardian = true;
                break;
            }
        }
        if (!currentGuardian && !cachedGuardian) return;
        if (currentGuardian) creeper.setTarget(null);
        if (!creeper.isIgnited()) creeper.setSwellDir(-1);
    }

    public static void addGuardTargetGoal(GoalSelector targetSelector, Mob mob) {
        if (!(mob instanceof Enemy)) return;
        targetSelector.addGoal(TARGET_PRIORITY, new GuardTargetGoal(mob));
    }

    /** The same target goal every hostile already had. Creepers skip it while creeper aggro is off. */
    private static final class GuardTargetGoal extends NearestAttackableTargetGoal<BlossomWarden> {
        private final Mob owner;

        GuardTargetGoal(Mob owner) {
            super(owner, BlossomWarden.class, true, target -> true);
            this.owner = owner;
        }

        @Override public boolean canUse() {
            return allowed() && super.canUse();
        }

        @Override public boolean canContinueToUse() {
            return allowed() && super.canContinueToUse();
        }

        private boolean allowed() {
            return !(owner instanceof Creeper) || creeperAggroOn(owner.level());
        }
    }
}
