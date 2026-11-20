package dev.uwu.dimension;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client sends only the chosen mode and original equip context, never targets or damage. */
public final class DawnpetalAbilities {
    static final double RADIUS = 5.5;
    private static final int WINDUP = 8, SWEEP_TICKS = 10;
    static final int DURATION = 23;
    private static final String COOLDOWN = "uwu.dawnpetal_active_until";
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();
    private static final Map<ServerPlayer, Spin> SPINS = new WeakHashMap<>();
    private static final ThreadLocal<DamageSource> SWEEP_DAMAGE = new ThreadLocal<>();

    private static final class Spin {
        final ServerLevel level;
        final ItemStack stack;
        final DawnpetalSpinCue.Accepted cue;
        final Set<UUID> hit = new HashSet<>();
        boolean struck;

        Spin(ServerPlayer player, UUID axe, long start) {
            level = player.serverLevel();
            stack = player.getMainHandItem();
            cue = DawnpetalSpinCue.Accepted.forPlayer(player, axe, start, Math.toRadians(player.getYRot() + 90));
        }

        boolean equipped(ServerPlayer player) {
            return player.level() == level && player.getInventory().selected == cue.slot()
                    && player.getMainHandItem() == stack && DawnpetalAbilities.equipped(player);
        }
    }
    private record Use(long sequence, long tick, ResourceLocation dimension, int slot, UUID axe, int ability)
            implements CustomPacketPayload {
        static final Type<Use> TYPE = new Type<>(UwUDimension.id("dawnpetal_ability"));
        static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = StreamCodec.of(
                (b,u) -> {
                    b.writeVarLong(u.sequence); b.writeLong(u.tick); b.writeResourceLocation(u.dimension);
                    b.writeByte(u.slot); b.writeUUID(u.axe); b.writeByte(u.ability);
                },
                b -> new Use(b.readVarLong(), b.readLong(), b.readResourceLocation(), b.readUnsignedByte(),
                        b.readUUID(), b.readUnsignedByte()));
        @Override public Type<Use> type() { return TYPE; }
    }

    static void register(IEventBus bus) {
        DawnpetalSpinCue.register(bus);
        bus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").playToServer(Use.TYPE, Use.CODEC,
                (use,ctx) -> { if (ctx.player() instanceof ServerPlayer player) receive(player, use); }));
        NeoForge.EVENT_BUS.addListener(DawnpetalAbilities::tick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            SPINS.remove(e.getEntity());
            LAST_REQUEST.remove(e.getEntity());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> SPINS.remove(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> SPINS.remove(e.getOriginal()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            SPINS.keySet().removeIf(p -> p.getServer() == e.getServer());
            LAST_REQUEST.keySet().removeIf(p -> p.getServer() == e.getServer());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e) -> {
            if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer actor) {
                Spin spin = SPINS.get(actor);
                if (spin != null && spin.equipped(actor) && spin.level.getGameTime() - spin.cue.start() < DURATION) {
                    PacketDistributor.sendToPlayer(viewer, spin.cue);
                }
            }
        });
    }

    static boolean equipped(Player p) {
        return p.isAlive() && !p.isRemoved() && !p.isSpectator() && p.getMainHandItem().is(RevivalBlossoms.AXE.get());
    }

    static void send(Player p, long sequence, UUID axe, DawnpetalAbility ability) {
        PacketDistributor.sendToServer(new Use(sequence, p.level().getGameTime(), p.level().dimension().location(),
                p.getInventory().selected, axe, ability.index));
    }

    private static CompoundTag persisted(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND))
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static boolean permitted(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        return equipped(p) && !p.isUsingItem() && !p.isShiftKeyDown()
                && level.getWorldBorder().isWithinBounds(p.blockPosition()) && level.hasChunkAt(p.blockPosition())
                && SakuraAccess.mayInteract(p, p.blockPosition());
    }

    private static void receive(ServerPlayer p, Use u) {
        if (!p.serverLevel().getServer().isSameThread() || u.sequence <= LAST_REQUEST.getOrDefault(p, 0L)) return;
        LAST_REQUEST.put(p, u.sequence);
        DawnpetalAbility ability = DawnpetalAbility.at(u.ability);
        if (ability == null || u.slot != p.getInventory().selected
                || !p.level().dimension().location().equals(u.dimension)
                || !u.axe.equals(DawnpetalVanillaReach.syncedIdentity(p.getMainHandItem()))) return;
        long tick = p.level().getGameTime();
        if (u.tick < tick - 40 || u.tick > tick + 10 || !permitted(p)) return;
        long now = p.serverLevel().getServer().overworld().getGameTime();
        if (persisted(p).getLong(COOLDOWN) > now || p.getCooldowns().isOnCooldown(p.getMainHandItem().getItem())) return;

        Spin spin = null;
        if (ability == DawnpetalAbility.SPIN) {
            spin = new Spin(p, u.axe, tick);
            SPINS.put(p, spin);
        } else if (!RelicFlight.castDawnpetal(p.serverLevel(), p, InteractionHand.MAIN_HAND)) {
            return;
        }
        persisted(p).putLong(COOLDOWN, now + ability.cooldown);
        p.getCooldowns().addCooldown(RevivalBlossoms.AXE.get(), ability.cooldown);
        if (spin != null) PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, spin.cue);
    }

    public static void slotChanged(ServerPlayer p) {
        Spin spin = SPINS.remove(p);
        if (spin != null) PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, spin.cue.cancel());
    }

    private static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Spin spin = SPINS.get(p);
        if (spin == null) return;
        long elapsed = spin.level.getGameTime() - spin.cue.start();
        if (!spin.equipped(p) || elapsed <= WINDUP + SWEEP_TICKS && !permitted(p)) {
            slotChanged(p);
            return;
        }
        if (elapsed >= DURATION) {
            SPINS.remove(p);
            if (spin.struck) DawnpetalSwing.finishGroundEffect(p);
        } else if (elapsed >= WINDUP && elapsed < WINDUP + SWEEP_TICKS) {
            if (elapsed == WINDUP) spin.struck = true;
            sweep(p, spin, (int) (elapsed - WINDUP));
        }
    }

    private static void sweep(ServerPlayer p, Spin spin, int step) {
        ServerLevel level = spin.level;
        Vec3 center = p.position();
        double arc = DawnpetalSector.REVOLUTION / SWEEP_TICKS;
        double start = spin.cue.heading() + step * arc;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RADIUS, 1, RADIUS))) {
            if (target == p || target.isSpectator() || !target.isAttackable() || spin.hit.contains(target.getUUID())
                    || (target instanceof Player && !level.getServer().isPvpAllowed())
                    || !BlossomArts.allowedTarget(level, p, target) || !p.hasLineOfSight(target)) continue;
            var bounds = target.getBoundingBox();
            double height = center.y + 1;
            if (height < bounds.minY - .55 || height > bounds.maxY + .55
                    || !DawnpetalSector.hits(RADIUS, start, arc, bounds.minX - center.x, bounds.maxX - center.x,
                            bounds.minZ - center.z, bounds.maxZ - center.z)) continue;
            spin.hit.add(target.getUUID());
            hurtWithSpin(p, target);
            // Mod damage hooks can unequip or move the actor synchronously.
            if (SPINS.get(p) != spin) return;
            if (!spin.equipped(p) || !permitted(p)) {
                slotChanged(p);
                return;
            }
        }
        for (int i = 0; i < 3; i++) {
            double angle = start + arc * i / 3.0;
            Vec3 tip = center.add(RADIUS * Math.cos(angle), 1, RADIUS * Math.sin(angle));
            if (!level.hasChunkAt(BlockPos.containing(tip))) continue;
            for (ServerPlayer viewer : level.players()) {
                if (viewer.distanceToSqr(tip) > 32 * 32 || ReachLife.preference(viewer, "sakura_effects", 2) == 0) continue;
                level.sendParticles(viewer, MagicGlowParticles.FLOWER.get(), false, tip.x, tip.y, tip.z, 1, .10,.04,.10,.01);
            }
        }
    }

    private static void hurtWithSpin(ServerPlayer attacker, LivingEntity target) {
        DamageSource previous = SWEEP_DAMAGE.get();
        DamageSource source = attacker.damageSources().playerAttack(attacker);
        SWEEP_DAMAGE.set(source);
        try {
            target.hurt(source, 8F);
        } finally {
            if (previous == null) SWEEP_DAMAGE.remove();
            else SWEEP_DAMAGE.set(previous);
        }
    }

    static boolean isSpinDamage(DamageSource source) {
        return SWEEP_DAMAGE.get() == source;
    }

    private DawnpetalAbilities() {}
}
