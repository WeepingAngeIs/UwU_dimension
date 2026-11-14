package dev.uwu.dimension;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;

/** Lets hostile monsters target the village's Blossom Warden like they target an iron golem. */
public final class GuardianTargeting {
    private static final int TARGET_PRIORITY = 2;

    private GuardianTargeting() {
    }

    public static void addGuardTargetGoal(GoalSelector targetSelector, Mob mob) {
        if (!(mob instanceof Enemy)) return;
        targetSelector.addGoal(TARGET_PRIORITY,
                new NearestAttackableTargetGoal<>(mob, BlossomWarden.class, true, target -> true));
    }
}
