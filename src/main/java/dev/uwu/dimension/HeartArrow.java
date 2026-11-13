package dev.uwu.dimension;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

public final class HeartArrow extends Arrow {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> RIPPLE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(HeartArrow.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    public HeartArrow(EntityType<? extends Arrow> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RIPPLE, false);
    }

    private boolean rippleBow() { return entityData.get(RIPPLE); }

    static void fire(ServerLevel level, Player player, Arsenal.Relic kind, float draw) {
        HeartArrow arrow = new HeartArrow(Arsenal.ARROW.get(), level);
        arrow.setOwner(player);
        arrow.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
        arrow.entityData.set(RIPPLE, kind == Arsenal.Relic.RIPPLE_BOW);
        arrow.getPersistentData().putBoolean("ripple", arrow.rippleBow());
        arrow.getPersistentData().putFloat("strength", draw);
        arrow.pickup = Pickup.DISALLOWED;
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 3.0F * draw, 0.3F);
        level.addFreshEntity(arrow);
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.9F, 1.25F - 0.25F * draw);
    }
    @Override public void tick() {
        super.tick();
        if (!isAlive()) return;
        if (level().isClientSide) {
            if (!rippleBow()) {
                double offsetX = (random.nextDouble() - .5) * .06;
                double offsetZ = (random.nextDouble() - .5) * .06;
                level().addParticle(CupidParticles.PASTEL_HEART.get(), getX() + offsetX, getY(), getZ() + offsetZ,
                        0, .015, 0);
            }
        } else if (level() instanceof ServerLevel server) {
            if (rippleBow())
                server.sendParticles(ParticleTypes.GLOW, getX(), getY(), getZ(), 1, .025, .025, .025, 0);
            HeartCombatGlow.trail(server, position(), rippleBow() ? 1 : 0, false);
        }
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel server) || !(getOwner() instanceof Player player)) { super.onHitEntity(hit); return; }
        boolean ripple = rippleBow();
        if (hit.getEntity() instanceof LivingEntity creature && !HeartMagic.friendly(player, creature)) {
            boolean charmed = !ripple && creature instanceof Mob mob && HeartMagic.charm(server, player, mob,true);
            if (!charmed && (!(creature instanceof Player other) || player.canHarmPlayer(other)))
                creature.hurt(damageSources().arrow(this, player), (ripple ? Arsenal.Relic.RIPPLE_BOW.damage : Arsenal.Relic.CUPID_BOW.damage) * getPersistentData().getFloat("strength"));
        }
        HeartCombatGlow.burst(server, hit.getLocation(), 1.5, ripple ? 1 : 0);
        heartBurst(server, hit.getLocation(), ripple);
        burst(server, hit.getLocation(), player);
        discard();
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        if (level() instanceof ServerLevel server) {
            HeartCombatGlow.burst(server, hit.getLocation(), .9, rippleBow() ? 1 : 0);
            heartBurst(server, hit.getLocation(), rippleBow());
            if (getOwner() instanceof Player player) burst(server, hit.getLocation(), player);
            discard();
        }
    }

    private static void heartBurst(ServerLevel level, Vec3 center, boolean ripple) {
        if (ripple) return;
        level.sendParticles(CupidParticles.PASTEL_HEART.get(), center.x, center.y, center.z,
                8, .18, .16, .18, .02);
    }

    private void burst(ServerLevel level, Vec3 spot, Player player) {
        if (!rippleBow()) return;
        RelicAbilities.ring(level, spot, 3.5, ParticleTypes.GLOW);
        for (LivingEntity mob : level.getEntitiesOfClass(Mob.class, new AABB(spot, spot).inflate(3.5), m -> !HeartMagic.friendly(player, m))) {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 140));
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 75, 1));
        }
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("uwu_ripple_bow", rippleBow());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(RIPPLE, tag.getBoolean("uwu_ripple_bow"));
    }
}
