package dev.uwu.dimension;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Collects accepted melee damage for one native attack or one Better Combat attack request. */
public final class ReturnbloomTurnBack {
    static final int COOLDOWN_TICKS = 120;
    private static final String COOLDOWN = "uwu.returnbloom_turn_back_until";

    private record Swing(ServerPlayer player, ItemStack weapon, float yaw, Set<Mob> targets) {}
    private static final class DamageWitness {
        private final Mob target;
        private final DamageSource source;
        private boolean dealtDamage;

        private DamageWitness(Mob target, DamageSource source) {
            this.target = target;
            this.source = source;
        }
    }

    private static final ThreadLocal<Swing> SWING = new ThreadLocal<>();
    private static final ThreadLocal<DamageWitness> HIT = new ThreadLocal<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post event) -> {
            DamageWitness witness = HIT.get();
            if (witness != null && witness.target == event.getEntity()
                    && witness.source == event.getSource() && event.getNewDamage() > 0)
                witness.dealtDamage = true;
        });
    }

    public static void vanilla(Player actor, Runnable attack) {
        if (!(actor instanceof ServerPlayer player) || SWING.get() != null
                || !player.getMainHandItem().is(RevivalBlossoms.DIRK.get())) {
            attack.run();
            return;
        }
        swing(player, true, attack);
    }

    public static void betterCombat(ServerPlayer player, ItemStack selected, boolean mainHand, Runnable attack) {
        if (!player.getMainHandItem().is(RevivalBlossoms.DIRK.get())) {
            attack.run();
            return;
        }
        // Offhand and rejected selections suppress the vanilla wrapper for the entire native batch.
        swing(player, mainHand && selected == player.getMainHandItem(), attack);
    }

    private static void swing(ServerPlayer player, boolean eligible, Runnable attack) {
        if (SWING.get() != null) {
            attack.run();
            return;
        }
        CompoundTag saved = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        long now = player.serverLevel().getServer().overworld().getGameTime();
        float yaw = player.getYRot();
        ItemStack held = eligible && player.isAlive() && !player.isSpectator() && Float.isFinite(yaw)
                && saved.getLong(COOLDOWN) <= now ? player.getMainHandItem() : ItemStack.EMPTY;
        Swing swing = new Swing(player, held, Mth.wrapDegrees(yaw), new LinkedHashSet<>());
        SWING.set(swing);
        try {
            attack.run();
        } finally {
            SWING.remove();
            if (!swing.targets.isEmpty()) {
                CompoundTag data = player.getPersistentData();
                if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND))
                    data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
                data.getCompound(Player.PERSISTED_NBT_TAG).putLong(COOLDOWN, now + COOLDOWN_TICKS);
                for (Mob target : swing.targets) {
                    if (target.isAlive() && target.level() == player.serverLevel())
                        ReturnbloomFacing.turnAway(target, swing.yaw);
                }
            }
        }
    }

    public static boolean damage(Player actor, Entity victim, DamageSource source, BooleanSupplier hurt) {
        Swing swing = SWING.get();
        if (swing == null || actor != swing.player || swing.weapon.isEmpty()
                || swing.weapon != actor.getMainHandItem() || !(victim instanceof Mob target)
                || source.getEntity() != actor || source.getDirectEntity() != actor
                || !source.is(DamageTypes.PLAYER_ATTACK)
                || !BlossomArts.allowedTarget(swing.player.serverLevel(), actor, target))
            return hurt.getAsBoolean();

        DamageWitness previous = HIT.get();
        DamageWitness witness = new DamageWitness(target, source);
        HIT.set(witness);
        try {
            boolean accepted = hurt.getAsBoolean();
            if (accepted && witness.dealtDamage) swing.targets.add(target);
            return accepted;
        } finally {
            if (previous == null) HIT.remove();
            else HIT.set(previous);
        }
    }

    private ReturnbloomTurnBack() {}
}
