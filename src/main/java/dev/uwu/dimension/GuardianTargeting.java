package dev.uwu.dimension;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/** Lets hostile monsters target the village's Blossom Warden like they target an iron golem. */
public final class GuardianTargeting {
    private static final int TARGET_PRIORITY = 2;
    /** World game rule: whether creepers may target the Blossom Warden. Saved with the world, on by default. */
    private static GameRules.Key<GameRules.BooleanValue> creeperAggro;

    private GuardianTargeting() {
    }

    /** Called while the mod loads, before any world exists, because game rules are fixed once a world is created. */
    public static void register() {
        creeperAggro = GameRules.register("uwuGuardianCreeperAggro", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));
    }

    public static boolean creeperAggroOn(Level level) {
        return creeperAggro == null || level.getGameRules().getBoolean(creeperAggro);
    }

    public static void setCreeperAggro(Level level, boolean on, MinecraftServer server) {
        level.getGameRules().getRule(creeperAggro).set(on, server);
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
