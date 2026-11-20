package dev.uwu.dimension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** One presentation bit per wielder. The existing ledger alone decides eligibility. */
final class RevivalReady {
    private record Actor(UUID uuid, int entityId) {}
    private record State(ResourceLocation dimension, Actor actor, boolean ready) implements CustomPacketPayload {
        static final Type<State> TYPE = new Type<>(UwUDimension.id("revival_ready"));
        static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(
                (buf,s) -> { buf.writeResourceLocation(s.dimension); buf.writeUUID(s.actor.uuid);
                    buf.writeVarInt(s.actor.entityId); buf.writeBoolean(s.ready); },
                buf -> new State(buf.readResourceLocation(), new Actor(buf.readUUID(),buf.readVarInt()),buf.readBoolean()));
        @Override public Type<State> type() { return TYPE; }
    }

    private static final Set<ServerPlayer> ANNOUNCED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Level,Set<Actor>> CLIENT = new WeakHashMap<>();

    static void register(IEventBus bus) {
        bus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").playToClient(State.TYPE,State.CODEC,
                (state,ctx) -> {
                    Level world=ctx.player().level();
                    if (!world.dimension().location().equals(state.dimension)) return;
                    Set<Actor> players=CLIENT.computeIfAbsent(world, unused -> new HashSet<>());
                    if (state.ready) players.add(state.actor); else players.remove(state.actor);
                }));
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p) update(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e) -> {
            if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer actor) {
                update(actor);
                PacketDistributor.sendToPlayer(viewer,state(actor,ANNOUNCED.contains(actor)));
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StopTracking e) -> {
            if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer actor)
                PacketDistributor.sendToPlayer(viewer,state(actor,false));
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> snapshotSelf(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> snapshotSelf(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> snapshotSelf(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent e) -> {
            if (e.getLevel().isClientSide() && e.getEntity() instanceof Player p) {
                Set<Actor> players=CLIENT.get(e.getLevel());
                if (players!=null) players.remove(new Actor(p.getUUID(),p.getId()));
            }
        });
    }

    private static void snapshotSelf(Player actor) {
        if (actor instanceof ServerPlayer player) {
            update(player);
            PacketDistributor.sendToPlayer(player,state(player,ANNOUNCED.contains(player)));
        }
    }

    private static State state(ServerPlayer player, boolean ready) {
        return new State(player.level().dimension().location(),new Actor(player.getUUID(),player.getId()),ready);
    }

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && (available(player,player.getMainHandItem())
                || available(player,player.getOffhandItem()));
    }

    private static boolean available(ServerPlayer player,ItemStack stack) {
        return RevivalBlossoms.isWeapon(stack) && RevivalBlossoms.remaining(player,stack)==0;
    }

    static void update(ServerPlayer player) {
        boolean ready=eligible(player);
        if (ready ? ANNOUNCED.add(player) : ANNOUNCED.remove(player))
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,state(player,ready));
    }

    static void consumed(ServerPlayer player) {
        ANNOUNCED.remove(player);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,state(player,false));
    }

    static boolean visible(Player player) {
        Set<Actor> ready=CLIENT.get(player.level());
        return ready!=null && player.isAlive() && !player.isSpectator() && !player.isInvisible() && !player.isSleeping()
                && (RevivalBlossoms.isWeapon(player.getMainHandItem()) || RevivalBlossoms.isWeapon(player.getOffhandItem()))
                && ready.contains(new Actor(player.getUUID(),player.getId()));
    }

    private RevivalReady() {}
}
