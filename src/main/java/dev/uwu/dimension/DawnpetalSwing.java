package dev.uwu.dimension;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Tracks the presentation and a single terrain effect. Combat remains responsible for hits. */
public final class DawnpetalSwing {
    private record Cue(int actor, UUID actorId, UUID axeId, int slot, long start, int strike, int duration)
            implements CustomPacketPayload {
        static final Type<Cue> TYPE = new Type<>(UwUDimension.id("dawnpetal_swing"));
        static final StreamCodec<RegistryFriendlyByteBuf, Cue> CODEC = StreamCodec.of(
                (b,m) -> { b.writeVarInt(m.actor); b.writeUUID(m.actorId); b.writeUUID(m.axeId);
                    b.writeVarInt(m.slot); b.writeLong(m.start); b.writeVarInt(m.strike); b.writeVarInt(m.duration); },
                b -> new Cue(b.readVarInt(), b.readUUID(), b.readUUID(), b.readVarInt(),
                        b.readLong(), b.readVarInt(), b.readVarInt()));
        @Override public Type<Cue> type() { return TYPE; }
    }

    private static final class Swing {
        final ServerLevel level;
        final ItemStack stack;
        final Cue cue;
        boolean struck;
        Swing(ServerPlayer p, int windup, int duration) {
            level = p.serverLevel();
            stack = p.getMainHandItem();
            cue = new Cue(p.getId(), p.getUUID(), RevivalBlossoms.identity(stack), p.getInventory().selected,
                    level.getGameTime(), windup, duration);
        }
        boolean equipped(ServerPlayer p) {
            return p.serverLevel() == level && p.isAlive() && !p.isSpectator()
                    && p.getMainHandItem() == stack && p.getInventory().selected == cue.slot;
        }
    }

    private static final Map<ServerPlayer, Swing> ACTIVE = new IdentityHashMap<>();
    // This map is accessed only on the physical client's main/render thread.
    private static final Map<LivingEntity, Cue> VISIBLE = new WeakHashMap<>();

    static void register(IEventBus bus) {
        DawnpetalVanillaReach.register(bus);
        bus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").playToClient(Cue.TYPE, Cue.CODEC,
                (cue,ctx) -> {
                    var entity = ctx.player().level().getEntity(cue.actor);
                    if (!(entity instanceof Player p) || !p.getUUID().equals(cue.actorId)) return;
                    if (cue.duration == 0) VISIBLE.remove(p);
                    else VISIBLE.put(p, cue);
                }));
        NeoForge.EVENT_BUS.addListener(DawnpetalSwing::tick);
        NeoForge.EVENT_BUS.addListener(DawnpetalSwing::vanillaHit);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ACTIVE.remove(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> ACTIVE.remove(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> ACTIVE.remove(e.getOriginal()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ACTIVE.keySet().removeIf(p -> p.getServer() == e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e) -> {
            if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer actor) {
                Swing swing = ACTIVE.get(actor);
                if (swing != null && swing.equipped(actor)) PacketDistributor.sendToPlayer(viewer, swing.cue);
            }
        });
    }

    public static void start(ServerPlayer p, int windupTicks, int durationTicks) {
        if (!p.getMainHandItem().is(RevivalBlossoms.AXE.get()) || !p.isAlive() || p.isSpectator()) return;
        int duration = Math.clamp(durationTicks, 3, 200);
        Swing swing = new Swing(p, Math.clamp(windupTicks, 1, duration-1), duration);
        Swing previous = ACTIVE.put(p, swing);
        // A legal next combo can arrive one tick before the post-tick that normally finishes the last.
        if (previous != null && previous.equipped(p)
                && previous.level.getGameTime() - previous.cue.start >= previous.cue.duration - 1)
            finish(p, previous);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, swing.cue);
    }

    public static void strike(ServerPlayer p) {
        Swing swing = ACTIVE.get(p);
        if (swing != null && swing.equipped(p)) swing.struck = true;
    }

    public static void cancel(ServerPlayer p) {
        Swing swing = ACTIVE.remove(p);
        if (swing == null) return;
        Cue c = swing.cue;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,
                new Cue(c.actor, c.actorId, c.axeId, c.slot, c.start, 0, 0));
    }

    private static void vanillaHit(LivingDamageEvent.Post e) {
        if (ModList.get().isLoaded("bettercombat") || e.getNewDamage() <= 0
                || !(e.getSource().getDirectEntity() instanceof ServerPlayer p)
                || !e.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                || !p.getMainHandItem().is(RevivalBlossoms.AXE.get())
                || DawnpetalAbilities.isSpinDamage(e.getSource())) return;
        start(p, 1, 6);
        strike(p);
    }

    private static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Swing swing = ACTIVE.get(p);
        if (swing == null) return;
        if (!swing.equipped(p)) { cancel(p); return; }
        if (swing.level.getGameTime() - swing.cue.start < swing.cue.duration) return;
        ACTIVE.remove(p);
        finish(p, swing);
    }

    private static void finish(ServerPlayer p, Swing swing) {
        if (swing.struck) finishGroundEffect(p);
    }

    static void finishGroundEffect(ServerPlayer p) {
        if (p.onGround() && !p.isPassenger() && !p.isInWaterOrBubble()) groundBurst(p);
    }

    private static void groundBurst(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 ahead = Vec3.directionFromRotation(0, p.getYRot()).scale(.65);
        Vec3 start = p.position().add(ahead).add(0,.45,0);
        Vec3 end = start.add(0,-.85,0);
        if (!level.hasChunkAt(BlockPos.containing(start))) return;
        var hit = level.clip(new ClipContext(start,end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,p));
        if (hit.getType()!=HitResult.Type.BLOCK || hit.isInside() || hit.getDirection()!=Direction.UP) return;
        var state = level.getBlockState(hit.getBlockPos());
        if (!state.getFluidState().isEmpty()) return;
        var pos=hit.getLocation().add(0,.025,0);
        var grit=new BlockParticleOption(ParticleTypes.BLOCK,state).setPos(hit.getBlockPos());
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(pos)>32*32 || ReachLife.preference(viewer,"sakura_effects",2)==0) continue;
            level.sendParticles(viewer,grit,false,pos.x,pos.y,pos.z,7,.22,.035,.22,.035);
            level.sendParticles(viewer,MagicGlowParticles.FLOWER.get(),false,pos.x,pos.y+.035,pos.z,5,.24,.055,.24,.028);
        }
    }

    /** Includes the opening windup before the model starts growing. */
    public static boolean hasLocalCue(Player owner, float partialTick) {
        Cue cue = VISIBLE.get(owner);
        return cue != null && cue.actorId.equals(owner.getUUID()) && cue.slot == owner.getInventory().selected
                && (owner.level().getGameTime() - cue.start) + partialTick < cue.duration
                && cue.axeId.equals(DawnpetalVanillaReach.syncedIdentity(owner.getMainHandItem()));
    }

    /** Normalized growth on a client for the actual attacking stack. */
    public static double expansion(LivingEntity owner, ItemStack rendered, float partialTick) {
        Cue c = VISIBLE.get(owner);
        if (c == null || !(owner instanceof Player)) return 0;
        double age = (owner.level().getGameTime() - c.start) + partialTick;
        if (age >= c.duration) { VISIBLE.remove(owner); return 0; }
        // Remote players don't synchronize the owner's selected hotbar index, but do synchronize the held stack.
        var tag = rendered.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID("uwu_revival_id") || !c.axeId.equals(tag.getUUID("uwu_revival_id"))) return 0;
        return DawnpetalMotion.expansion(age,c.strike,c.duration);
    }
    private DawnpetalSwing() {}
}
