package dev.uwu.dimension;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Arms one exact held Dirk for a successful primary melee hit. */
public final class ReturnbloomPrime {
    private record Prime(ServerLevel level, int slot, ItemStack stack) {
        boolean isHeldBy(ServerPlayer player) {
            return player.isAlive() && !player.isRemoved() && !player.isSpectator() && player.serverLevel() == level
                    && player.getInventory().selected == slot && player.getMainHandItem() == stack
                    && stack.is(RevivalBlossoms.DIRK.get());
        }
    }

    private static final class Hit {
        final LivingEntity target;
        final DamageSource source;
        boolean dealtDamage;

        Hit(LivingEntity target, DamageSource source) {
            this.target = target;
            this.source = source;
        }
    }

    private static final Map<ServerPlayer, Prime> PRIMES = new WeakHashMap<>();
    private static final ThreadLocal<Hit> PRIMARY_HIT = new ThreadLocal<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(ReturnbloomPrime::afterDamage);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                Prime prime = PRIMES.get(player);
                if (prime != null && !prime.isHeldBy(player)) PRIMES.remove(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> PRIMES.remove(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> PRIMES.remove(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> PRIMES.remove(event.getOriginal()));
    }

    static boolean arm(ServerPlayer player, ItemStack stack) {
        Prime existing = PRIMES.get(player);
        if (existing != null && existing.stack() == stack && existing.isHeldBy(player)) return true;
        Prime prime = new Prime(player.serverLevel(), player.getInventory().selected, stack);
        if (!prime.isHeldBy(player)) return false;
        PRIMES.put(player, prime);
        return true;
    }

    public static void clear(ServerPlayer player) {
        PRIMES.remove(player);
    }

    /** Call from Player.attack's primary hurt invocation so other playerAttack damage cannot consume a prime. */
    public static boolean attack(Player player, LivingEntity target, DamageSource source, BooleanSupplier hurt) {
        if (!(player instanceof ServerPlayer attacker) || !source.is(DamageTypes.PLAYER_ATTACK)
                || source.getEntity() != attacker || source.getDirectEntity() != attacker) return hurt.getAsBoolean();
        Prime prime = PRIMES.get(attacker);
        if (prime == null || !prime.isHeldBy(attacker)) return hurt.getAsBoolean();
        if (target instanceof Player opponent && !attacker.canHarmPlayer(opponent)) return hurt.getAsBoolean();

        Hit previous = PRIMARY_HIT.get();
        Hit hit = new Hit(target, source);
        PRIMARY_HIT.set(hit);
        boolean accepted;
        try {
            accepted = hurt.getAsBoolean();
        } finally {
            if (previous == null) PRIMARY_HIT.remove();
            else PRIMARY_HIT.set(previous);
        }
        // Post can run inside hurt(), before a modded target returns its final result.
        if (accepted && hit.dealtDamage && prime.isHeldBy(attacker) && PRIMES.get(attacker) == prime) {
            PRIMES.remove(attacker);
            NeoForge.EVENT_BUS.post(new ReturnbloomPrimedHitEvent(attacker, target));
        }
        return accepted;
    }

    private static void afterDamage(LivingDamageEvent.Post event) {
        Hit hit = PRIMARY_HIT.get();
        if (hit != null && hit.target == event.getEntity() && hit.source == event.getSource()
                && event.getNewDamage() > 0) hit.dealtDamage = true;
    }

    private ReturnbloomPrime() {}
}
