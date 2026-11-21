package dev.uwu.dimension;

import dev.uwu.dimension.mixin.ReturnbloomInterpolationAccessor;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
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
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").playToClient(
                Turn.TYPE, Turn.CODEC, (turn, context) -> receive(context.player().level(), turn)));
    }

    static void turnAway(Mob target, float yaw) {
        if (!(target.level() instanceof ServerLevel level)) return;
        snap(target, yaw);
        var turn = new Turn(level.dimension().location(), target.getId(), target.getUUID(), yaw);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, turn);
        level.sendParticles(MagicGlowParticles.FLOWER.get(), target.getX(), target.getEyeY() + .2,
                target.getZ(), 4, .22, .13, .22, .01);
    }

    private static void receive(Level level, Turn turn) {
        if (!Float.isFinite(turn.yaw) || !level.dimension().location().equals(turn.dimension)) return;
        if (!(level.getEntity(turn.entityId) instanceof Mob target)
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
