package dev.uwu.dimension;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;

/** Prototype tuning in blocks, added only to the range used for a Dawnpetal melee strike. */
public final class DawnpetalReach {
    public static final double BONUS = 0.75D;

    private record Forwarded(ServerGamePacketListenerImpl listener, ServerboundInteractPacket packet, ItemStack stack) {}
    private static final ThreadLocal<Forwarded> FORWARDED = new ThreadLocal<>();
    private record Strike(ServerPlayer player, CustomPacketPayload request, ItemStack stack) {}
    private static final ThreadLocal<Strike> STRIKE = new ThreadLocal<>();

    private DawnpetalReach() {}

    public static void nativeStrike(ServerPlayer player, CustomPacketPayload request, ItemStack stack, Runnable action) {
        var previous = STRIKE.get();
        STRIKE.set(new Strike(player, request, stack));
        try {
            action.run();
        } finally {
            if (previous == null) STRIKE.remove();
            else STRIKE.set(previous);
        }
    }

    public static boolean applies(ServerPlayer player, CustomPacketPayload request, ItemStack stack) {
        var strike = STRIKE.get();
        return strike != null && strike.player() == player && strike.request() == request && strike.stack() == stack
                && player.getMainHandItem() == stack;
    }

    public static void forward(ServerGamePacketListenerImpl listener, ServerboundInteractPacket packet,
                               ItemStack weapon, Runnable vanilla) {
        var previous = FORWARDED.get();
        FORWARDED.set(new Forwarded(listener, packet, weapon));
        try {
            vanilla.run();
        } finally {
            if (previous == null) FORWARDED.remove();
            else FORWARDED.set(previous);
        }
    }

    public static double allowance(ServerGamePacketListenerImpl listener, ServerboundInteractPacket packet) {
        var call = FORWARDED.get();
        if (call == null || call.listener() != listener || call.packet() != packet) return 0;
        var player = listener.player;
        return player.getMainHandItem() == call.stack() ? BONUS : 0;
    }
}
