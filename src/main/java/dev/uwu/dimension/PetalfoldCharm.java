package dev.uwu.dimension;

import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

@EventBusSubscriber(modid = "uwudimension")
public final class PetalfoldCharm {
    private static final int CHARM_TICKS = 6 * 20;

    private PetalfoldCharm() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer blocker)
                || !blocker.getUseItem().is(HeartShields.PETALFOLD_SHIELD.get())) {
            return;
        }
        ServerLevel level = blocker.serverLevel();
        var server = level.getServer();
        var damage = event.getDamageContainer();
        LivingEntity attacker = event.getDamageSource().getEntity() instanceof LivingEntity living
            ? living : null;
        long until = level.getGameTime() + CHARM_TICKS;

        // The event announces a proposed block. Its container records the actual
        // damage blocked only after every listener has returned. Capturing the
        // use stack above also lets a successful final durability point count.
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (event.isCanceled() || damage.getBlockedDamage() <= 0
                    || !blocker.isAlive() || blocker.level() != level) {
                return;
            }
            HeartShieldEffects.bloom(level, blocker, attacker);
            if (attacker != null && attacker != blocker && attacker.isAlive() && attacker.level() == level) {
                charm(level, blocker, attacker, until);
            }
        }));
    }

    private static void charm(ServerLevel level, ServerPlayer keeper, LivingEntity attacker, long until) {
        long now = level.getGameTime();
        if (until <= now) return;

        // Preserve existing ownership, commands and longer Cupid Bow durations.
        var data = attacker.getPersistentData();
        if (data.hasUUID(HeartMagic.OWNER) && data.getLong(HeartMagic.END) > now) {
            if (keeper.getUUID().equals(data.getUUID(HeartMagic.OWNER)) && until > data.getLong(HeartMagic.END)) {
                data.putLong(HeartMagic.END, until);
            }
            return;
        }
        // HeartMagic handles mob immunity, PvP eligibility, minion AI and expiry.
        HeartMagic.charmFromShield(level, keeper, attacker, (int)(until - now));
    }
}
