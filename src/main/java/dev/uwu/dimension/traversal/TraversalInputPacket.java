package dev.uwu.dimension.traversal;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Optional v2 play payload. The two previously recorded v2 payloads retain their wire layout. */
public record TraversalInputPacket(Command command, UUID lease, ResourceLocation dimension, int slot)
        implements CustomPacketPayload {
    public enum Command { LEASE, TOGGLE_SNOWKISS, ACTIVATE_SNOWKISS, ACTIVATE_VESPER }

    public static final Type<TraversalInputPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("uwudimension", "traversal_equipped_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TraversalInputPacket> CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeEnum(packet.command);
            buf.writeUUID(packet.lease);
            buf.writeResourceLocation(packet.dimension);
            if (packet.command != Command.LEASE) buf.writeByte(packet.slot);
        },
        buf -> {
            Command command = buf.readEnum(Command.class);
            UUID lease = buf.readUUID();
            ResourceLocation dimension = buf.readResourceLocation();
            return new TraversalInputPacket(command, lease, dimension, command == Command.LEASE ? -1 : buf.readUnsignedByte());
        });

    public TraversalInputPacket {
        if (command == null || lease == null || dimension == null)
            throw new IllegalArgumentException("Incomplete equipped traversal input");
        if (command == Command.LEASE ? slot != -1 : slot < 0 || slot > 8)
            throw new IllegalArgumentException("Invalid traversal hotbar slot " + slot);
    }

    @Override public Type<TraversalInputPacket> type() { return TYPE; }
}
