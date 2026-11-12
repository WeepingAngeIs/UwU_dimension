package dev.uwu.dimension;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;

/** Item-carrying impact projectile for the mod's cast weapons. */
public final class RelicProjectile extends Snowball {
    public RelicProjectile(EntityType<? extends RelicProjectile> type, Level level) {
        super(type, level);
    }

    public void launchFrom(LivingEntity owner, float speed, float inaccuracy) {
        setOwner(owner);
        setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0, speed, inaccuracy);
    }

    @Override public void tick() {
        super.tick();
        if (isAlive() && level() instanceof ServerLevel server) {
            String id = getPersistentData().getString("uwu.counter");
            CounterArsenal.Entry counter = CounterArsenal.entry(id);
            int element = counter != null && counter.theme().equals("dusk") ? 2 : 0;
            HeartCombatGlow.trail(server, position(), element);
        }
    }
}
