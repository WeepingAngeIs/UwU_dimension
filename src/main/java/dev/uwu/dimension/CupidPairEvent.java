package dev.uwu.dimension;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Fired on the game bus before invoking either parent's offspring factory. */
public final class CupidPairEvent extends Event implements ICancellableEvent {
    public final ServerPlayer archer;
    public final LivingEntity struck;
    public final LivingEntity partner;

    CupidPairEvent(ServerPlayer archer, LivingEntity struck, LivingEntity partner) {
        this.archer = archer;
        this.struck = struck;
        this.partner = partner;
    }
}

