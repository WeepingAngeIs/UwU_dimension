package dev.uwu.dimension.traversal.client;

import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.traversal.TraversalInputPacket;
import dev.uwu.dimension.traversal.TraversalInputPacket.Command;
import dev.uwu.dimension.traversal.TraversalPackets;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-thread endpoint for the optional v2 equipped-input channel. Never retain clicks for a future lease. */
public final class TraversalInputClient {
    private record Session(ClientPacketListener connection, LocalPlayer player, ClientLevel level, UUID lease) {
        boolean current(Minecraft mc) {
            return mc.player == player && mc.level == level && player.level() == level && mc.getConnection() == connection;
        }
    }

    private static Session session;

    private TraversalInputClient() {}

    public static void accept(ICommonPacketListener source, TraversalInputPacket packet) {
        var mc = Minecraft.getInstance();
        var connection = mc.getConnection();
        if (packet.command() != Command.LEASE || connection == null || connection != source || mc.player == null
                || mc.level == null || mc.player.level() != mc.level
                || !mc.level.dimension().location().equals(packet.dimension())) return;
        if (session != null && session.current(mc) && session.lease.equals(packet.lease())) return;
        TraversalControls.discardCaptured();
        session = new Session(connection, mc.player, mc.level, packet.lease());
    }

    /** Owner's single client post-tick entry. Older v2 peers still receive their original action-only packet. */
    public static void poll() {
        var mc = Minecraft.getInstance();
        if (session != null && !session.current(mc)) {
            session = null;
        }
        TraversalControls.poll(action -> {
            var connection = mc.getConnection();
            if (connection == null) return;
            if (!connection.hasChannel(TraversalInputPacket.TYPE)) {
                PacketDistributor.sendToServer(new TraversalPackets.Request(action));
                return;
            }
            if (session == null || !session.current(mc)) return;
            var stack = session.player.getMainHandItem();
            Command command;
            if (stack.is(Arsenal.WEAPONS.get(Arsenal.Relic.FROST_WHIP).get())) {
                command = action == TraversalPackets.Action.TOGGLE ? Command.TOGGLE_SNOWKISS : Command.ACTIVATE_SNOWKISS;
            } else if (action == TraversalPackets.Action.ACTIVATE
                    && stack.is(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get())) {
                command = Command.ACTIVATE_VESPER;
            } else {
                return;
            }
            int slot = session.player.getInventory().selected;
            if (slot >= 0 && slot <= 8) {
                PacketDistributor.sendToServer(new TraversalInputPacket(command, session.lease, session.level.dimension().location(), slot));
            }
        });
    }
}
