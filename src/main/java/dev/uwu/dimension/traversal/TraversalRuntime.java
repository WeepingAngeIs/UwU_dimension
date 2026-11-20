package dev.uwu.dimension.traversal;

import dev.uwu.dimension.TraversalServer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-thread adapter. Construct for one server lifetime, then connect #1's shared hooks. */
public final class TraversalRuntime {
    private final Map<ServerLevel, TraversalServer> levels = new HashMap<>();

    public void request(ServerPlayer sender, TraversalPackets.Request request) {
        var state = levels.computeIfAbsent(sender.serverLevel(),
            level -> new TraversalServer(level, cue -> broadcast(level, cue)));
        switch (request.action()) {
            case ACTIVATE -> state.activate(sender);
            case TOGGLE -> state.toggle(sender);
        }
    }

    public void tick(ServerLevel level) {
        var state = levels.get(level);
        if (state != null) state.tick();
    }

    public void equipmentChanged(ServerPlayer player) {
        var state = levels.get(player.serverLevel());
        if (state != null) state.equipmentChanged(player);
    }

    public void startTracking(ServerPlayer observer, ServerPlayer subject) {
        var level = subject.serverLevel();
        var state = levels.get(level);
        if (state == null || observer.level() != level || level.getEntity(observer.getId()) != observer) return;
        state.replayTracking(subject, (cue, patchOnly) -> PacketDistributor.sendToPlayer(observer,
            new TraversalPackets.Effect(level.dimension().location(), cue, patchOnly)));
    }

    public void forget(ServerLevel oldLevel, ServerPlayer subject) {
        var state = levels.get(oldLevel);
        if (state != null) state.forget(subject);
    }

    public void unload(ServerLevel level) {
        levels.remove(level);
    }

    private static void broadcast(ServerLevel level, TraversalCue cue) {
        if (!(level.getEntity(cue.actorId()) instanceof ServerPlayer actor)) return;
        var effect = new TraversalPackets.Effect(level.dimension().location(), cue);
        switch (cue.type()) {
            case MODE_ON, MODE_OFF -> PacketDistributor.sendToPlayer(actor, effect);
            default -> PacketDistributor.sendToPlayersTrackingEntityAndSelf(actor, effect);
        }
    }
}
