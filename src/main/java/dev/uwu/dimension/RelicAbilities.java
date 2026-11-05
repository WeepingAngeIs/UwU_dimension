package dev.uwu.dimension;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

final class RelicAbilities {
    private static final String PETALS = "uwudimension.petals";
    private static final String THROW = "uwudimension.thrown_relic";

    static boolean activate(ServerLevel level, Player player, Arsenal.Relic relic) {
        switch (relic) {
            case RIBBONHARE_SCEPTER -> {
                if (!(player.pick(25, 1, false) instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
                    player.displayClientMessage(Component.literal("Point to a ledge within 25 blocks."), true); return false;
                }
                var block = hit.getBlockPos().relative(hit.getDirection());
                Rabbit rabbit = Arsenal.HARE.get().create(level);
                if (rabbit == null) return false;
                rabbit.moveTo(block.getX() + 0.5, block.getY(), block.getZ() + 0.5, player.getYRot(), 0);
                if (!level.noCollision(rabbit) || !level.getBlockState(block.below()).isFaceSturdy(level, block.below(), Direction.UP)) {
                    player.displayClientMessage(Component.literal("The hare needs a clear ledge to land on."), true); return false;
                }
                HeartMagic.callHare(level, player, rabbit);
            }
            case BLOSSOM_GREATSWORD -> { return returnSweep(level, player); }
            case BELLHEART_AXE -> {
                ring(level, player.position(), 5, ParticleTypes.CHERRY_LEAVES);
                for (Mob foe : nearby(level, player, 5)) {
                    pushToward(foe, player.position(), 0.9);
                    foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 48, 2));
                }
                sound(level, player, SoundEvents.BELL_BLOCK, 1.65F);
            }
            case LOCKKEEPER_AXE, VESPER_SCYTHE -> {
                boolean reaping = relic == Arsenal.Relic.VESPER_SCYTHE;
                arc(level, player, reaping ? 4.5 : 5.5, reaping ? ParticleTypes.CHERRY_LEAVES : ParticleTypes.SPLASH);
                int count = 0;
                for (Mob foe : nearby(level, player, reaping ? 4.5 : 5.5)) {
                    if (!inFront(player, foe)) continue;
                    if (foe.hurt(player.damageSources().playerAttack(player), relic.damage)) count++;
                    if (!reaping) pushToward(foe, player.position(), -1.45);
                }
                if (reaping) player.heal(Math.min(count, 3) * 2);
                sound(level, player, reaping ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_SPLASH, 0.85F);
            }
            case SATIN_WHIP, BLUE_HOUR_WAND -> {
                boolean whip = relic == Arsenal.Relic.SATIN_WHIP;
                Vec3 start = player.getEyePosition(), end = player.pick(whip ? 11 : 23, 1, false).getLocation();
                EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, start, end, new AABB(start, end).inflate(1), e ->
                    e instanceof Mob mob && mob.isAlive() && !HeartMagic.friendly(player, mob));
                if (hit != null && hit.getEntity() instanceof Mob foe) {
                    end = hit.getLocation();
                    foe.hurt(player.damageSources().playerAttack(player), relic.damage);
                    if (whip) pushToward(foe, player.position(), 1.55);
                    else {
                        foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
                        foe.addEffect(new MobEffectInstance(MobEffects.GLOWING, 140));
                    }
                }
                line(level, start, end, whip ? ParticleTypes.CHERRY_LEAVES : ParticleTypes.GLOW);
                sound(level, player, whip ? SoundEvents.FISHING_BOBBER_THROW : SoundEvents.AMETHYST_BLOCK_CHIME, 1.15F);
            }
            case LANTERN_SPEAR, TIDELACE_TRIDENT -> {
                Snowball copy = new Snowball(level, player);
                copy.setItem(new ItemStack(Arsenal.WEAPONS.get(relic).get()));
                copy.getPersistentData().putString(THROW, relic.id());
                copy.shootFromRotation(player, player.getXRot(), player.getYRot(), -1.5F, 1.85F, 0.4F);
                level.addFreshEntity(copy);
                sound(level, player, SoundEvents.TRIDENT_THROW.value(), 1.2F);
            }
            default -> { return false; }
        }
        return true;
    }

    private static final String FULL_SWING = "uwudimension.full_swing";
    static void beforeAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !player.getMainHandItem().is(Arsenal.WEAPONS.get(Arsenal.Relic.BLOSSOM_GREATSWORD).get())) return;
        if (player.getAttackStrengthScale(0.5F) >= 0.9F) player.getPersistentData().putLong(FULL_SWING, player.level().getGameTime());
        else player.getPersistentData().remove(FULL_SWING);
    }

    static void scatterPetals(ServerLevel level, Player player, LivingEntity foe) {
        if (!player.getPersistentData().contains(FULL_SWING) || player.getPersistentData().getLong(FULL_SWING) != level.getGameTime()) return;
        player.getPersistentData().remove(FULL_SWING);
        Vec3 center = foe.position().add(0, foe.getBbHeight() * 0.6, 0);
        CompoundTag tag = new CompoundTag();
        tag.putLong("time", level.getGameTime());
        tag.putDouble("x", center.x); tag.putDouble("y", center.y); tag.putDouble("z", center.z);
        tag.putString("dimension", level.dimension().location().toString());
        player.getPersistentData().put(PETALS, tag);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, center.x, center.y, center.z, 21, 0.6, 0.1, 0.6, 0.025);
    }
    private static boolean returnSweep(ServerLevel level, Player p) {
        CompoundTag tag = p.getPersistentData().getCompound(PETALS);
        long age = level.getGameTime() - tag.getLong("time");
        Vec3 center = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
        if (tag.isEmpty() || !level.dimension().location().toString().equals(tag.getString("dimension")) || age < 6 || age > 60 || center.distanceToSqr(p.position()) > 36
            || center.subtract(p.getEyePosition()).normalize().dot(p.getLookAngle()) < 0.12) {
            p.displayClientMessage(Component.literal("Strike first, then sweep through the drifting petals."), true); return false;
        }
        p.getPersistentData().remove(PETALS);
        arc(level, p, 7, ParticleTypes.CHERRY_LEAVES);
        for (Mob foe : nearby(level, p, 7)) if (inFront(p, foe)) foe.hurt(p.damageSources().playerAttack(p), 16);
        sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.72F);
        return true;
    }
    static void onThrowImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Snowball ball) || !(ball.level() instanceof ServerLevel level)
            || !ball.getPersistentData().contains(THROW) || !(ball.getOwner() instanceof Player player)) return;
        boolean tide = ball.getPersistentData().getString(THROW).equals(Arsenal.Relic.TIDELACE_TRIDENT.id());
        Vec3 at = event.getRayTraceResult().getLocation();
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity foe
            && !HeartMagic.friendly(player, foe) && (!(foe instanceof Player other) || player.canHarmPlayer(other))) {
            foe.hurt(ball.damageSources().thrown(ball, player), tide ? Arsenal.Relic.TIDELACE_TRIDENT.damage : Arsenal.Relic.LANTERN_SPEAR.damage);
            if (!tide) foe.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200));
        }
        if (tide) {
            for (Mob foe : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(4), m -> !HeartMagic.friendly(player, m)))
                pushToward(foe, at, 1.1);
        }
        ring(level, at, tide ? 3.5 : 0.7, tide ? ParticleTypes.SPLASH : ParticleTypes.END_ROD);
        ball.discard();
        event.setCanceled(true);
    }
    private static java.util.List<Mob> nearby(ServerLevel level, Player player, double radius) {
        return level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius), mob -> mob.isAlive() &&
            mob.distanceToSqr(player) <= radius * radius && !HeartMagic.friendly(player, mob) && player.hasLineOfSight(mob));
    }
    private static boolean inFront(Player player, Mob foe) {
        return foe.position().subtract(player.position()).normalize().dot(player.getLookAngle()) > 0.16;
    }
    private static void pushToward(Mob mob, Vec3 goal, double force) {
        Vec3 delta = goal.subtract(mob.position()).multiply(1, 0, 1).normalize().scale(force);
        mob.push(delta.x, 0.24, delta.z);
    }
    static void ring(ServerLevel level, Vec3 spot, double radius, ParticleOptions effect) {
        for (int i = 0; i < 28; i++) {
            double a = Math.PI * 2 * i / 28;
            level.sendParticles(effect, spot.x + radius * Math.cos(a), spot.y + 0.25, spot.z + radius * Math.sin(a), 1, 0, 0, 0, 0);
        }
    }
    private static void arc(ServerLevel level, Player p, double radius, ParticleOptions effect) {
        double center = Math.toRadians(p.getYRot() + 90);
        for (int i = -12; i <= 12; i++) {
            double a = center + i * 0.105;
            level.sendParticles(effect, p.getX() + radius * Math.cos(a), p.getY() + 1.15, p.getZ() + radius * Math.sin(a), 2, 0.12, 0.1, 0.12, 0.01);
        }
    }
    private static void line(ServerLevel level, Vec3 start, Vec3 end, ParticleOptions effect) {
        int steps = Math.max(1, (int)(start.distanceTo(end) * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 v = start.lerp(end, (double)i / steps);
            level.sendParticles(effect, v.x, v.y, v.z, 1, 0, 0, 0, 0);
        }
    }
    private static void sound(ServerLevel level, Player p, SoundEvent sound, float pitch) {
        level.playSound(null, p.blockPosition(), sound, SoundSource.PLAYERS, 0.8F, pitch);
    }
}
