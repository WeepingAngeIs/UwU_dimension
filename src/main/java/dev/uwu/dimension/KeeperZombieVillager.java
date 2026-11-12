package dev.uwu.dimension;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.level.Level;

/** Dedicated resident-undead type so these villagers never revert to vanilla zombie villagers. */
public final class KeeperZombieVillager extends ZombieVillager {
    public KeeperZombieVillager(EntityType<? extends KeeperZombieVillager> type, Level level) {
        super(type, level);
    }
}
