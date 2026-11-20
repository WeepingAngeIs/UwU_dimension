package dev.uwu.dimension;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** A single native attack start authorizes at most one reach extension and one strike cue. */
@EventBusSubscriber(modid = "uwudimension")
public final class DawnpetalAttackWindow {
    private record Swing(ResourceKey<Level> dimension, int slot, ItemStack weapon,
                         String animation, long hit, long nextStart, boolean spent) {
        boolean heldBy(ServerPlayer player) {
            return player.isAlive() && !player.isSpectator() && !player.isRemoved() && !player.isUsingItem()
                    && player.level().dimension().equals(dimension) && player.getInventory().selected == slot
                    && player.getMainHandItem() == weapon;
        }
        Swing spend() {
            return new Swing(dimension, slot, weapon, animation, hit, nextStart, true);
        }
    }

    // Access only on the logical server thread. The value deliberately contains no player or world object.
    private static final Map<ServerPlayer, Swing> SWINGS = new WeakHashMap<>();

    private DawnpetalAttackWindow() {}

    public static void start(ServerPlayer player, String animation, int windup, int duration) {
        long now = player.serverLevel().getGameTime();
        var previous = SWINGS.get(player);
        if (previous != null && previous.heldBy(player) && now < previous.nextStart()) return;
        var swing = new Swing(player.level().dimension(), player.getInventory().selected, player.getMainHandItem(),
                animation, now + windup, now + Math.max(1, duration - 1), false);
        if (!swing.heldBy(player) || !swing.weapon().is(RevivalBlossoms.AXE.get())) return;
        SWINGS.put(player, swing);
        DawnpetalSwing.start(player, windup, duration);
    }

    public static boolean strike(ServerPlayer player, ItemStack weapon, String animation, int slot) {
        var swing = SWINGS.get(player);
        if (swing == null || swing.spent() || !swing.heldBy(player) || swing.weapon() != weapon
                || swing.slot() != slot || !swing.animation().equals(animation)) return false;
        long now = player.serverLevel().getGameTime();
        // Use server time and a short arrival allowance for independently delivered BC packets.
        // BC sends one request per swing. An early request cannot be replayed in the valid tick.
        SWINGS.put(player, swing.spend());
        if (now < swing.hit() - 1 || now > swing.hit() + 2) return false;
        DawnpetalSwing.strike(player);
        return true;
    }

    public static void cancel(ServerPlayer player) {
        var swing = SWINGS.get(player);
        if (swing == null) return;
        // Keep the next-start fence through a feint, even if another start arrives in this tick.
        if (!swing.spent()) SWINGS.put(player, swing.spend());
        DawnpetalSwing.cancel(player);
    }

    private static void forget(ServerPlayer player) {
        var swing = SWINGS.remove(player);
        if (swing != null && !swing.spent()) DawnpetalSwing.cancel(player);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent
    public static void transfer(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var swing = SWINGS.get(player);
        if (swing == null) return;
        long now = player.serverLevel().getGameTime();
        if (!swing.heldBy(player) || now > Math.max(swing.nextStart(), swing.hit() + 2)) forget(player);
    }
}
