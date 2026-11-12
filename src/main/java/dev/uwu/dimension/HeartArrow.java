package dev.uwu.dimension;

import net.minecraft.core.particles.ParticleTypes;
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
    public HeartArrow(EntityType<? extends Arrow> type, Level level) { super(type, level); }
    static void fire(ServerLevel level, Player player, Arsenal.Relic kind, float draw) {
        HeartArrow arrow = new HeartArrow(Arsenal.ARROW.get(), level);
        arrow.setOwner(player);
        arrow.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
        arrow.getPersistentData().putBoolean("ripple", kind == Arsenal.Relic.RIPPLE_BOW);
        arrow.getPersistentData().putFloat("strength", draw);
        arrow.pickup = Pickup.DISALLOWED;
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 3.0F * draw, 0.3F);
        level.addFreshEntity(arrow);
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.9F, 1.25F - 0.25F * draw);
    }
    @Override public void tick() {
        super.tick();
        if (isAlive() && level() instanceof ServerLevel server && tickCount % 2 == 0) {
            server.sendParticles(getPersistentData().getBoolean("ripple") ? ParticleTypes.GLOW : CupidParticles.PASTEL_HEART.get(), getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            if (tickCount % 4 == 0) HeartCombatGlow.trail(server, position(), getPersistentData().getBoolean("ripple") ? 1 : 0);
        }
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel server) || !(getOwner() instanceof Player player)) { super.onHitEntity(hit); return; }
        boolean ripple = getPersistentData().getBoolean("ripple");
        if (hit.getEntity() instanceof LivingEntity creature && !HeartMagic.friendly(player, creature)) {
            boolean charmed = !ripple && creature instanceof Mob mob && HeartMagic.charm(server, player, mob,true);
            if (!charmed && (!(creature instanceof Player other) || player.canHarmPlayer(other)))
                creature.hurt(damageSources().arrow(this, player), (ripple ? Arsenal.Relic.RIPPLE_BOW.damage : Arsenal.Relic.CUPID_BOW.damage) * getPersistentData().getFloat("strength"));
        }
        HeartCombatGlow.burst(server, hit.getLocation(), 1.5, ripple ? 1 : 0);
        burst(server, hit.getLocation(), player);
        discard();
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        if (level() instanceof ServerLevel server) {
            HeartCombatGlow.burst(server, hit.getLocation(), .9, getPersistentData().getBoolean("ripple") ? 1 : 0);
            if (getOwner() instanceof Player player) burst(server, hit.getLocation(), player);
            discard();
        }
    }
    private void burst(ServerLevel level, Vec3 spot, Player player) {
        if (!getPersistentData().getBoolean("ripple")) return;
        RelicAbilities.ring(level, spot, 3.5, ParticleTypes.GLOW);
        for (LivingEntity mob : level.getEntitiesOfClass(Mob.class, new AABB(spot, spot).inflate(3.5), m -> !HeartMagic.friendly(player, m))) {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 140));
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 75, 1));
        }
    }
}
