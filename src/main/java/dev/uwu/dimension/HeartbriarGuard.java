package dev.uwu.dimension;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

@EventBusSubscriber(modid = "uwudimension")
public final class HeartbriarGuard {
    private HeartbriarGuard() {}

    static boolean usingHeartbriar(LivingEntity holder) {
        return holder.getUseItem().is(HeartShields.HEARTBRIAR_SHIELD.get())
            && holder.getItemInHand(holder.getUsedItemHand()).is(HeartShields.HEARTBRIAR_SHIELD.get());
    }

    @SubscribeEvent
    public static void blockingAura(LivingEntityUseItemEvent.Tick event) {
        var holder = event.getEntity();
        if (holder.level() instanceof ServerLevel level && holder.isBlocking()
                && event.getItem().is(HeartShields.HEARTBRIAR_SHIELD.get()) && usingHeartbriar(holder)) {
            HeartShieldEffects.aura(level, holder,
                holder.getTicksUsingItem() - ShieldItem.EFFECTIVE_BLOCK_DELAY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockFromAnyDirection(LivingShieldBlockEvent event) {
        var defender = event.getEntity();
        if (event.getOriginalBlock() || !defender.isBlocking() || !usingHeartbriar(defender)) {
            return;
        }

        // Keep the eligibility checks from LivingEntity.isDamageSourceBlocked,
        // allowing positioned hits from any bearing once the shield is raised.
        var source = event.getDamageSource();
        if (source.is(DamageTypeTags.BYPASSES_SHIELD) || source.getSourcePosition() == null
                || source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
            return;
        }

        event.setBlocked(true);
    }
}
