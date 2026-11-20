package dev.uwu.dimension;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** An accepted Spin cue shares the server's cast clock and leaves ordinary attack cues independent. */
public final class DawnpetalSpinCue {
    public record Frame(UUID castId, long start, double age, double heading, HumanoidArm arm) {
        public float castYaw() {
            return (float) (Math.toDegrees(heading) - 90.0);
        }
    }

    record Accepted(int actor, UUID actorId, UUID axeId, int slot, ResourceLocation dimension,
                    UUID castId, long start, double heading, boolean left, boolean cancelled)
            implements CustomPacketPayload {
        static final Type<Accepted> TYPE = new Type<>(UwUDimension.id("dawnpetal_spin"));
        static final StreamCodec<RegistryFriendlyByteBuf, Accepted> CODEC = StreamCodec.of(
                (b, cue) -> {
                    b.writeVarInt(cue.actor);
                    b.writeUUID(cue.actorId);
                    b.writeUUID(cue.axeId);
                    b.writeByte(cue.slot);
                    b.writeResourceLocation(cue.dimension);
                    b.writeUUID(cue.castId);
                    b.writeLong(cue.start);
                    b.writeDouble(cue.heading);
                    b.writeBoolean(cue.left);
                    b.writeBoolean(cue.cancelled);
                },
                b -> new Accepted(b.readVarInt(), b.readUUID(), b.readUUID(), b.readUnsignedByte(),
                        b.readResourceLocation(), b.readUUID(), b.readLong(), b.readDouble(),
                        b.readBoolean(), b.readBoolean()));

        static Accepted forPlayer(ServerPlayer player, UUID axe, long start, double heading) {
            return new Accepted(player.getId(), player.getUUID(), axe, player.getInventory().selected,
                    player.level().dimension().location(), UUID.randomUUID(), start, heading,
                    player.getMainArm() == HumanoidArm.LEFT, false);
        }

        Accepted cancel() {
            return new Accepted(actor, actorId, axeId, slot, dimension, castId, start, heading, left, true);
        }

        @Override public Type<Accepted> type() { return TYPE; }
    }

    private static final class ClientCast {
        final WeakReference<Player> actor;
        final Accepted cue;
        boolean seenEquipped;
        boolean ended;

        ClientCast(Player player, Accepted cue) {
            this.actor = new WeakReference<>(player);
            this.cue = cue;
            this.ended = cue.cancelled;
        }
    }

    // The payload and rendered view access this state only on the physical client's main thread.
    private static final Map<Player, ClientCast> VISIBLE = new WeakHashMap<>();

    static void register(IEventBus bus) {
        bus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1")
                .playToClient(Accepted.TYPE, Accepted.CODEC, (cue, context) -> receive(context.player(), cue)));
    }

    private static void receive(Player viewer, Accepted cue) {
        var level = viewer.level();
        if (!level.isClientSide || !level.dimension().location().equals(cue.dimension)
                || cue.slot >= 9 || !Double.isFinite(cue.heading)
                || level.getGameTime() - cue.start >= DawnpetalAbilities.DURATION) return;
        if (!(level.getEntity(cue.actor) instanceof Player player) || !player.getUUID().equals(cue.actorId)) return;
        ClientCast current = castFor(player);
        if (current != null) {
            if (current.cue.castId.equals(cue.castId)) {
                if (cue.cancelled) current.ended = true;
                return;
            }
            if (cue.cancelled || cue.start <= current.cue.start) return;
        }
        VISIBLE.put(player, new ClientCast(player, cue));
    }

    /** Returns the current frame for the exact held axe. Only the local actor's slot index is synchronized. */
    public static Frame frame(Player player, float partialTick, boolean localOwner) {
        if (!player.level().isClientSide) return null;
        ClientCast cast = castFor(player);
        if (cast == null) return null;
        Accepted cue = cast.cue;
        double age = (player.level().getGameTime() - cue.start) + partialTick;
        if (age >= DawnpetalAbilities.DURATION || !player.level().dimension().location().equals(cue.dimension)
                || !player.getUUID().equals(cue.actorId)) {
            VISIBLE.remove(player);
            return null;
        }
        if (cast.ended || age < 0) return null;
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()
                || localOwner && player.getInventory().selected != cue.slot) {
            cast.ended = true;
            return null;
        }
        var held = player.getMainHandItem();
        if (!held.is(RevivalBlossoms.AXE.get()) || !cue.axeId.equals(DawnpetalVanillaReach.syncedIdentity(held))) {
            if (cast.seenEquipped || localOwner) cast.ended = true;
            return null;
        }
        cast.seenEquipped = true;
        return new Frame(cue.castId, cue.start, age, cue.heading, cue.left ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
    }

    private static ClientCast castFor(Player player) {
        ClientCast current = VISIBLE.get(player);
        // Entity equality uses the network ID, which can be reused after client untracking.
        if (current != null && current.actor.get() != player) {
            VISIBLE.remove(player);
            return null;
        }
        return current;
    }

    private DawnpetalSpinCue() {}
}
