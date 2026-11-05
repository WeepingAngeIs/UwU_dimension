package dev.uwu.dimension;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

final class HeartMagic {
    private static final String OWNER = "uwudimension.charm_owner", END = "uwudimension.charm_end", PREVIOUS = "uwudimension.previous_target";
    private static final String CALLER = "uwudimension.hare_caller", THUMP_END = "uwudimension.hare_end";
    private static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE, Arsenal.id("charm_immune"));

    static boolean charm(ServerLevel level, Player keeper, Mob mob) {
        if (mob.getType().is(IMMUNE) || (mob.getBbWidth() >= 6 && mob.getBbHeight() >= 5)) return false;
        CompoundTag data = mob.getPersistentData();
        if (!data.hasUUID(OWNER) && mob.getTarget() != null) data.putUUID(PREVIOUS, mob.getTarget().getUUID());
        mob.addTag(OWNER);
        data.putUUID(OWNER, keeper.getUUID());
        data.putLong(END, level.getGameTime() + 800);
        clearTarget(mob);
        level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getEyeY(), mob.getZ(), 10, 0.5, 0.2, 0.5, 0.05);
        return true;
    }

    static boolean friendly(Player keeper, LivingEntity entity) {
        if (entity instanceof VelvetVesper moth && moth.isCharmed(keeper)) return true;
        CompoundTag data = entity.getPersistentData();
        return entity == keeper || keeper.isAlliedTo(entity)
            || (entity instanceof TamableAnimal pet && pet.isOwnedBy(keeper))
            || (data.hasUUID(OWNER) && data.getUUID(OWNER).equals(keeper.getUUID()) && entity.level().getGameTime() < data.getLong(END))
            || (data.hasUUID(CALLER) && data.getUUID(CALLER).equals(keeper.getUUID()));
    }

    static void filterTarget(LivingChangeTargetEvent e) {
        LivingEntity mob = e.getEntity(), next = e.getNewAboutToBeSetTarget();
        if (next != null && mob.getTags().contains(OWNER) && mob.getPersistentData().hasUUID(OWNER) && next.getUUID().equals(mob.getPersistentData().getUUID(OWNER))
            && mob.level().getGameTime() < mob.getPersistentData().getLong(END)) e.setNewAboutToBeSetTarget(null);
    }
    static void protectKeeper(LivingIncomingDamageEvent e) {
        if (e.getSource().getEntity() instanceof LivingEntity mob && mob.getTags().contains(OWNER) && mob.getPersistentData().hasUUID(OWNER)
            && mob.getPersistentData().getUUID(OWNER).equals(e.getEntity().getUUID()) && mob.level().getGameTime() < mob.getPersistentData().getLong(END)) e.setCanceled(true);
    }

    static void tick(EntityTickEvent.Post e) {
        if (!(e.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        // Avoid allocating persistent tags on unrelated mobs.
        if (!mob.getTags().contains(OWNER) && !mob.getTags().contains(CALLER)) return;
        CompoundTag data = mob.getPersistentData();
        if (mob instanceof Rabbit rabbit && data.hasUUID(CALLER)) tickHare(level, rabbit, data);
        if (!data.hasUUID(OWNER)) return;
        if (level.getGameTime() >= data.getLong(END)) {
            mob.removeTag(OWNER);
            data.remove(OWNER);
            data.remove(END);
            clearTarget(mob);
            if (data.hasUUID(PREVIOUS) && level.getEntity(data.getUUID(PREVIOUS)) instanceof LivingEntity old && old.isAlive()) mob.setTarget(old);
            data.remove(PREVIOUS);
            return;
        }
        if (mob.tickCount % 5 != 0) return;
        if (mob.tickCount % 20 == 0) level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getEyeY(), mob.getZ(), 1, 0.16, 0.1, 0.16, 0);
        if (!(level.getEntity(data.getUUID(OWNER)) instanceof Player keeper)) { clearTarget(mob); return; }
        LivingEntity foe = keeper.tickCount - keeper.getLastHurtByMobTimestamp() < 220 ? keeper.getLastHurtByMob() : null;
        if (!eligible(keeper, mob, foe)) foe = keeper.tickCount - keeper.getLastHurtMobTimestamp() < 220 ? keeper.getLastHurtMob() : null;
        if (eligible(keeper, mob, foe)) {
            mob.setTarget(foe);
            mob.getNavigation().moveTo(foe, 1.2);
            if (mob.tickCount % 20 == 0 && mob.distanceToSqr(foe) <= 5 && mob.hasLineOfSight(foe)) {
                if (mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null) mob.doHurtTarget(foe);
                else foe.hurt(mob.damageSources().mobAttack(mob), 3);
            }
        } else {
            if (mob.getTarget() != null) clearTarget(mob);
            if (mob.distanceToSqr(keeper) > 12) mob.getNavigation().moveTo(keeper, 1.15);
        }
    }
    private static void clearTarget(Mob mob) {
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        mob.getNavigation().stop();
    }
    private static boolean eligible(Player keeper, Mob mob, LivingEntity foe) {
        return foe != null && foe != mob && foe.isAlive() && !friendly(keeper, foe) && keeper.distanceToSqr(foe) < 24 * 24
            && (!(foe instanceof Player other) || keeper.canHarmPlayer(other));
    }

    static void callHare(ServerLevel level, Player keeper, Rabbit rabbit) {
        CompoundTag data = rabbit.getPersistentData();
        rabbit.addTag(CALLER);
        data.putUUID(CALLER, keeper.getUUID());
        data.putLong(THUMP_END, level.getGameTime() + 160);
        rabbit.setInvulnerable(true);
        rabbit.setPersistenceRequired();
        level.addFreshEntity(rabbit);
        thump(level, rabbit, keeper);
    }
    private static void tickHare(ServerLevel level, Rabbit rabbit, CompoundTag data) {
        long remaining = data.getLong(THUMP_END) - level.getGameTime();
        if (remaining <= 0 || !(level.getEntity(data.getUUID(CALLER)) instanceof Player keeper)) {
            level.sendParticles(ParticleTypes.CHERRY_LEAVES, rabbit.getX(), rabbit.getY() + 0.3, rabbit.getZ(), 9, 0.2, 0.2, 0.2, 0.03);
            rabbit.discard();
            return;
        }
        if (remaining % 20 == 0) thump(level, rabbit, keeper);
        if (remaining < 125 && remaining % 5 == 0 && rabbit.distanceToSqr(keeper) > 4) rabbit.getNavigation().moveTo(keeper, 1.35);
    }
    private static void thump(ServerLevel level, Rabbit rabbit, Player keeper) {
        rabbit.startJumping();
        HeartwaterFeature.reveal(level, rabbit, keeper);
        level.playSound(null, rabbit.blockPosition(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASEDRUM.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 1.4F);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, rabbit.getX(), rabbit.getY() + 0.2, rabbit.getZ(), 9, 0.6, 0.1, 0.6, 0.02);
        for (Mob foe : level.getEntitiesOfClass(Mob.class, rabbit.getBoundingBox().inflate(10), m -> m instanceof Enemy && !friendly(keeper, m) && m.hasLineOfSight(rabbit))) {
            foe.setTarget(rabbit);
            foe.getNavigation().moveTo(rabbit, 1.1);
        }
    }
}

