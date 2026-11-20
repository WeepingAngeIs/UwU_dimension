package dev.uwu.dimension;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A confirmed catch primes the normal melee meter after Minecraft observes the re-equipped item. */
@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD)
public final class RelicCatchRecovery {
    private static final int SYNC_TICKS = 20;
    // The logical server and client each access only their own map.
    private static final Map<Player, Pending> SERVER = new WeakHashMap<>();
    private static final Map<Player, Pending> CLIENT = new WeakHashMap<>();

    private record Ready(int slot, UUID stack, ResourceLocation dimension, int damage, float attackDelay)
            implements CustomPacketPayload {
        static final Type<Ready> TYPE = new Type<>(UwUDimension.id("relic_catch_ready"));
        static final StreamCodec<RegistryFriendlyByteBuf, Ready> CODEC = StreamCodec.of(
                (buf, cue) -> {
                    buf.writeByte(cue.slot);
                    buf.writeUUID(cue.stack);
                    buf.writeResourceLocation(cue.dimension);
                    buf.writeVarInt(cue.damage);
                    buf.writeFloat(cue.attackDelay);
                },
                buf -> new Ready(buf.readUnsignedByte(), buf.readUUID(), buf.readResourceLocation(),
                        buf.readVarInt(), buf.readFloat()));
        @Override public Type<Ready> type() { return TYPE; }
    }

    private record Pending(Ready ready, int expiresAt) {}

    private RelicCatchRecovery() {}

    static boolean supports(ItemStack stack) {
        // Only the heavy returning axe and trident attacks. Satin's lash never removes the weapon.
        if (stack.is(RevivalBlossoms.AXE.get())) return true;
        return stack.getItem() instanceof Arsenal.RelicItem item
                && (item.relic == Arsenal.Relic.BELLHEART_AXE || item.relic == Arsenal.Relic.TIDELACE_TRIDENT);
    }

    private static @Nullable UUID identity(ItemStack stack) {
        if (stack.is(RevivalBlossoms.AXE.get())) return DawnpetalVanillaReach.syncedIdentity(stack);
        // The ordinary relic throw path already assigns BlossomArts' stack identity before launch.
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.hasUUID("uwu.art_stack") ? data.getUUID("uwu.art_stack") : null;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Ready.TYPE, Ready.CODEC, (cue, context) -> {
            Player player = context.player();
            if (player.level().isClientSide && cue.slot < 9 && cue.damage >= 0
                    && Float.isFinite(cue.attackDelay) && cue.attackDelay > 0
                    && player.level().dimension().location().equals(cue.dimension))
                CLIENT.put(player, new Pending(cue, player.tickCount + SYNC_TICKS));
        });
    }

    static void caught(ServerPlayer player, ItemStack stack) {
        if (!supports(stack)) return;
        UUID id = identity(stack);
        if (id == null) return;
        var cue = new Ready(player.getInventory().selected, id, player.level().dimension().location(),
                stack.getDamageValue(), 0);
        SERVER.put(player, new Pending(cue, player.tickCount + SYNC_TICKS));
    }

    public static boolean consume(Player player) {
        var pending = queue(player);
        Pending entry = pending.get(player);
        if (entry == null) return false;
        Ready cue = entry.ready;
        if (!player.isAlive() || player.isSpectator() || player.tickCount >= entry.expiresAt
                || player.getInventory().selected != cue.slot
                || !player.level().dimension().location().equals(cue.dimension)) {
            pending.remove(player);
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return false; // The normal inventory update can follow the verdict.
        if (!supports(stack) || !cue.stack.equals(identity(stack))) {
            pending.remove(player);
            return false;
        }
        if (stack.getDamageValue() != cue.damage) return false;
        float delay = player.getCurrentItemAttackStrengthDelay();
        if (!Float.isFinite(delay) || delay <= 0) {
            pending.remove(player);
            return false;
        }
        // Inventory and attributes synchronize separately. Priming with the empty hand's
        // attack speed can start a Better Combat swing that loses its charge before impact.
        if (player.level().isClientSide
                && Math.abs(delay - cue.attackDelay) > Math.max(.001F, cue.attackDelay * .0001F)) return false;

        pending.remove(player);
        if (player instanceof ServerPlayer server && server.connection != null)
            PacketDistributor.sendToPlayer(server, new Ready(cue.slot, cue.stack, cue.dimension, cue.damage, delay));
        return true;
    }

    public static void clear(Player player) {
        queue(player).remove(player);
    }

    private static Map<Player, Pending> queue(Player player) {
        return player.level().isClientSide ? CLIENT : SERVER;
    }
}
