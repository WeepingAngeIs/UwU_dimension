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

    boolean rippleBow() { return entityData.get(RIPPLE); }

    static void fire(ServerLevel level, Player player, Arsenal.Relic kind, float draw) {
        fire(level, player, kind, draw, null, null);
    }

    static boolean fire(ServerLevel level, Player player, Arsenal.Relic kind, float draw, java.util.UUID glitchStack, java.util.UUID glitchToken) {
        HeartArrow arrow = new HeartArrow(Arsenal.ARROW.get(), level);
        arrow.setOwner(player);
        arrow.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
        arrow.entityData.set(RIPPLE, kind == Arsenal.Relic.RIPPLE_BOW);
        arrow.getPersistentData().putBoolean("ripple", arrow.rippleBow());
        arrow.getPersistentData().putFloat("strength", draw);
        if (glitchToken != null && glitchStack != null) {
            arrow.getPersistentData().putUUID("uwu.glitch_id", glitchStack);
            arrow.getPersistentData().putUUID("uwu.glitch_token", glitchToken);
        }
        arrow.pickup = Pickup.DISALLOWED;
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 3.0F * draw, 0.3F);
        if (!level.addFreshEntity(arrow)) return false;
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.9F, 1.25F - 0.25F * draw);
        return true;
    }

    @Override public void remove(Entity.RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel server
                && getPersistentData().hasUUID("uwu.glitch_id") && getPersistentData().hasUUID("uwu.glitch_token"))
            GlitchCompletions.complete(server, getPersistentData().getUUID("uwu.glitch_id"), getPersistentData().getUUID("uwu.glitch_token"),getPersistentData().getInt("uwu.glitch_effect_ticks"));
        super.remove(reason);
    }
    @Override public void tick() {
        super.tick();
        if (!isAlive()) return;
        if (level() instanceof ServerLevel server) {
            if (rippleBow()) {
                server.sendParticles(ParticleTypes.GLOW, getX(), getY(), getZ(), 1, .025, .025, .025, 0);
                HeartCombatGlow.trail(server, position(), 1, false);
            } else CupidEffects.trail(server, this);
        }
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel server) || !(getOwner() instanceof Player player)) { super.onHitEntity(hit); return; }
        boolean ripple = rippleBow();
        // An archer's tame companion is normally friendly. Mark it gently for a wish without a damage path.
        if (!ripple && player instanceof net.minecraft.server.level.ServerPlayer owner && hit.getEntity() instanceof TamableAnimal pet
                && pet.isOwnedBy(owner) && CupidWishes.enabled(owner)) {
            if(HeartMagic.charm(server,owner,pet,true)) {glitchEffect(800);GlitchCompletions.track(this,pet,"charm");}
        } else if (hit.getEntity() instanceof LivingEntity creature && !HeartMagic.friendly(player, creature)) {
            boolean charmed = !ripple && HeartMagic.charm(server, player, creature, true);
            if(charmed){glitchEffect(800);GlitchCompletions.track(this,creature,"charm");}
            if (!charmed && (!(creature instanceof Player other) || player.canHarmPlayer(other)))
                creature.hurt(damageSources().arrow(this, player), (ripple ? Arsenal.Relic.RIPPLE_BOW.damage : Arsenal.Relic.CUPID_BOW.damage) * getPersistentData().getFloat("strength"));
        }
        if (ripple) HeartCombatGlow.burst(server, hit.getLocation(), 1.5, 1);
        else {
            CupidEffects.impact(server,hit.getEntity());
            HeartCombatGlow.illuminate(server,hit.getLocation(),1.5);
        }
        burst(server, hit.getLocation(), player);
        discard();
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        if (level() instanceof ServerLevel server) {
            if (rippleBow()) HeartCombatGlow.burst(server,hit.getLocation(),.9,1);
            else {
                HeartCombatGlow.illuminate(server,hit.getLocation(),.9);
                Vec3 at=hit.getLocation();
                CupidEffects.impact(server,at.add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.08)),hit.getDirection(),getYRot());
            }
            if (getOwner() instanceof Player player) burst(server, hit.getLocation(), player);
            discard();
        }
    }

    private void burst(ServerLevel level, Vec3 spot, Player player) {
        if (!rippleBow()) return;
        RelicAbilities.ring(level, spot, 3.5, ParticleTypes.GLOW);
        for (LivingEntity mob : level.getEntitiesOfClass(Mob.class, new AABB(spot, spot).inflate(3.5), m -> !HeartMagic.friendly(player, m))) {
            glitchEffect(140);
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 140));
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 75, 1));
            GlitchCompletions.track(this,mob,"glow");GlitchCompletions.track(this,mob,"slow");
        }
    }

    private void glitchEffect(int ticks) {
        if(getPersistentData().hasUUID("uwu.glitch_token"))
            getPersistentData().putInt("uwu.glitch_effect_ticks",Math.max(ticks,getPersistentData().getInt("uwu.glitch_effect_ticks")));
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
