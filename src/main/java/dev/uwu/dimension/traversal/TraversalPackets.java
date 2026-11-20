package dev.uwu.dimension.traversal;

import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Wire types only. The shared network owner registers these with the mod's registrar. */
public final class TraversalPackets {
    private TraversalPackets() {}
    /** Both directions are registered with this version in the integrating mod. */
    public static final String WIRE_VERSION = "2";

    public enum Action { TOGGLE, ACTIVATE }

    public record Request(Action action) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(id("traversal_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
            (buf, request) -> buf.writeEnum(request.action), buf -> new Request(buf.readEnum(Action.class)));
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Effect(ResourceLocation dimension, TraversalCue cue, boolean patchOnly) implements CustomPacketPayload {
        public Effect(ResourceLocation dimension, TraversalCue cue) {
            this(dimension, cue, false);
        }
        public static final Type<Effect> TYPE = new Type<>(id("traversal_effect"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Effect> CODEC = StreamCodec.of(
            (buf, packet) -> {
                TraversalCue cue = packet.cue;
                buf.writeResourceLocation(packet.dimension);
                buf.writeVarInt(cue.actorId());
                buf.writeEnum(cue.type());
                buf.writeVarLong(cue.startTick());
                writePoint(buf, cue.origin());
                writePoint(buf, cue.anchor());
                buf.writeEnum(cue.face());
                buf.writeVarInt(cue.windupTicks());
                buf.writeVarInt(cue.lifetimeTicks());
                buf.writeBoolean(packet.patchOnly());
            },
            buf -> new Effect(buf.readResourceLocation(), new TraversalCue(buf.readVarInt(),
                buf.readEnum(TraversalCue.Type.class), buf.readVarLong(), readPoint(buf), readPoint(buf),
                buf.readEnum(Direction.class), buf.readVarInt(), buf.readVarInt()), buf.readBoolean()));
        @Override public Type<Effect> type() { return TYPE; }
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension", name);
    }

    private static void writePoint(RegistryFriendlyByteBuf buf, Vec3 point) {
        buf.writeDouble(point.x);
        buf.writeDouble(point.y);
        buf.writeDouble(point.z);
    }

    private static Vec3 readPoint(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
