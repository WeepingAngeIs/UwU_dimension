package dev.uwu.dimension;

import dev.uwu.dimension.mixin.ReturnbloomInterpolationAccessor;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

final class ReturnbloomFacing {
    private record Turn(ResourceLocation dimension, int entityId, UUID uuid, float yaw) implements CustomPacketPayload {
        static final Type<Turn> TYPE = new Type<>(UwUDimension.id("returnbloom_turn"));
        static final StreamCodec<RegistryFriendlyByteBuf, Turn> CODEC = StreamCodec.of(
                (buf, turn) -> {
                    buf.writeResourceLocation(turn.dimension);
                    buf.writeVarInt(turn.entityId);
                    buf.writeUUID(turn.uuid);
                    buf.writeFloat(turn.yaw);
                },
                buf -> new Turn(buf.readResourceLocation(), buf.readVarInt(), buf.readUUID(), buf.readFloat()));

        @Override public Type<Turn> type() { return TYPE; }
    }

    static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ReturnbloomFacing::turnAway);
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").playToClient(
                Turn.TYPE, Turn.CODEC, (turn, context) -> receive(context.player().level(), turn)));
    }

    private static void turnAway(ReturnbloomPrimedHitEvent event) {
        var attacker = event.attacker();
        var target = event.target();
        if (target.isRemoved() || target.level() != attacker.serverLevel()) return;

        double dx = target.getX() - attacker.getX();
        double dz = target.getZ() - attacker.getZ();
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || (dx == 0.0 && dz == 0.0)) return;

        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)));
        snap(target, yaw);
        var turn = new Turn(target.level().dimension().location(), target.getId(), target.getUUID(), yaw);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, turn);
    }

    private static void receive(Level level, Turn turn) {
        if (!Float.isFinite(turn.yaw) || !level.dimension().location().equals(turn.dimension)) return;
        if (!(level.getEntity(turn.entityId) instanceof LivingEntity target)
                || target.isRemoved() || !target.getUUID().equals(turn.uuid)) return;

        snap(target, turn.yaw);
        // A remote entity may still be interpolating an earlier movement packet.
        // Replace only its pending yaw; position and pitch keep interpolating normally.
        ((ReturnbloomInterpolationAccessor) target).uwudimension$setReturnbloomLerpYaw(turn.yaw);
        target.lerpHeadTo(turn.yaw, 0);
    }

    static void snap(LivingEntity target, float yaw) {
        target.setYRot(yaw);
        target.setYHeadRot(yaw);
        target.setYBodyRot(yaw);
        target.yRotO = yaw;
        target.yHeadRotO = yaw;
        target.yBodyRotO = yaw;
    }

    private ReturnbloomFacing() {}
}
