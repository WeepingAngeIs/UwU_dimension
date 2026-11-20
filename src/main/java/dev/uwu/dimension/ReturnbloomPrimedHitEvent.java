package dev.uwu.dimension;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/** Fired on the game bus after a successful hit has used the attacker's Returnbloom prime. */
public final class ReturnbloomPrimedHitEvent extends Event {
    private final ServerPlayer attacker;
    private final LivingEntity target;

    ReturnbloomPrimedHitEvent(ServerPlayer attacker, LivingEntity target) {
        this.attacker = attacker;
        this.target = target;
    }

    public ServerPlayer attacker() {
        return attacker;
    }

    public LivingEntity target() {
        return target;
    }
}
