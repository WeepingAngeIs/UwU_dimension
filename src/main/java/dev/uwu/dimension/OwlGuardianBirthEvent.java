package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Last server-side veto before a juvenile is added. Cancelling keeps both parents
 * receptive and never consumes a pending second crop. */
public final class OwlGuardianBirthEvent extends Event implements ICancellableEvent {
    private final BlossomWarden first,second,child;
    private final ServerPlayer firstFeeder,secondFeeder;
    private final BlockPos position;
    OwlGuardianBirthEvent(BlossomWarden first,BlossomWarden second,BlossomWarden child,
            ServerPlayer firstFeeder,ServerPlayer secondFeeder,BlockPos position) {
        this.first=first;this.second=second;this.child=child;
        this.firstFeeder=firstFeeder;this.secondFeeder=secondFeeder;this.position=position.immutable();
    }
    public BlossomWarden firstParent(){return first;}
    public BlossomWarden secondParent(){return second;}
    public BlossomWarden child(){return child;}
    public ServerPlayer firstFeeder(){return firstFeeder;}
    public ServerPlayer secondFeeder(){return secondFeeder;}
    public BlockPos position(){return position;}
}
