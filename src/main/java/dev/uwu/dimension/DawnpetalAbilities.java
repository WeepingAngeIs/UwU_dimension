package dev.uwu.dimension;

import java.lang.ref.WeakReference;
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
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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
    private static final String AXE_COOLDOWN = "uwu.dawnpetal_active_until";
    private static final String HEART_SPIN_COOLDOWN = "uwu.dawnpetal_heart_spin_until";
    private static final int HEART_COUNT = 12;
    // A launch at 1.345 blocks/tick rises about ten blocks with normal living-entity drag and gravity.
    private static final double HEART_SPIN_LIFT = 1.345;
    private static final String DIRK_THROW_COOLDOWN = "uwu.returnbloom_throw_until";
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();
    private static final Map<ServerPlayer, Spin> SPINS = new WeakHashMap<>();
    private static final ThreadLocal<SweepHit> SWEEP_DAMAGE = new ThreadLocal<>();
    private static WeakReference<Player> heartCooldownOwner = new WeakReference<>(null);
    private static long heartCooldownEnd;

    private static final class SweepHit {
        final DamageSource source;
        final LivingEntity target;
        boolean didDamage;

        SweepHit(DamageSource source, LivingEntity target) {
            this.source = source;
            this.target = target;
        }
    }

    private record HeartCooldown(int ticks) implements CustomPacketPayload {
        static final Type<HeartCooldown> TYPE = new Type<>(UwUDimension.id("dawnpetal_heart_cooldown"));
        static final StreamCodec<RegistryFriendlyByteBuf, HeartCooldown> CODEC = StreamCodec.of(
                (buffer, cooldown) -> buffer.writeVarInt(cooldown.ticks),
                buffer -> new HeartCooldown(buffer.readVarInt()));

        @Override public Type<HeartCooldown> type() { return TYPE; }
    }

    private static final class Spin {
        final ServerLevel level;
        final ItemStack stack;
        final DawnpetalSpinCue.Accepted cue;
        final Set<UUID> hit = new HashSet<>();
        final boolean hearts;
        boolean struck;

        Spin(ServerPlayer player, UUID axe, long start, boolean hearts) {
            this.hearts = hearts;
            level = player.serverLevel();
            stack = player.getMainHandItem();
            cue = DawnpetalSpinCue.Accepted.forPlayer(player, axe, start, Math.toRadians(player.getYRot() + 90));
        }

        boolean equipped(ServerPlayer player) {
            return player.level() == level && player.getInventory().selected == cue.slot()
                    && player.getMainHandItem() == stack && DawnpetalAbilities.equipped(player);
        }
    }
    private record Use(long sequence, long tick, ResourceLocation dimension, int slot, UUID weapon, int ability)
            implements CustomPacketPayload {
        static final Type<Use> TYPE = new Type<>(UwUDimension.id("dawnpetal_ability"));
        static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = StreamCodec.of(
                (b,u) -> {
                    b.writeVarLong(u.sequence); b.writeLong(u.tick); b.writeResourceLocation(u.dimension);
                    b.writeByte(u.slot); b.writeUUID(u.weapon); b.writeByte(u.ability);
                },
                b -> new Use(b.readVarLong(), b.readLong(), b.readResourceLocation(), b.readUnsignedByte(),
                        b.readUUID(), b.readUnsignedByte()));
        @Override public Type<Use> type() { return TYPE; }
    }

    static void register(IEventBus bus) {
        DawnpetalSpinCue.register(bus);
        bus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1")
                .playToServer(Use.TYPE, Use.CODEC, (use, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) receive(player, use);
                })
                .playToClient(HeartCooldown.TYPE, HeartCooldown.CODEC, (cooldown, ctx) -> {
                    Player player = ctx.player();
                    if (player.level().isClientSide) {
                        heartCooldownOwner = new WeakReference<>(player);
                        heartCooldownEnd = player.level().getGameTime()
                                + Math.clamp(cooldown.ticks, 0, DawnpetalAbility.HEART_SPIN.cooldown);
                    }
                }));
        NeoForge.EVENT_BUS.addListener(DawnpetalAbilities::tick);
        NeoForge.EVENT_BUS.addListener(DawnpetalAbilities::recordSweepDamage);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> updateHeartCooldown(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> updateHeartCooldown(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            SPINS.remove(e.getEntity());
            LAST_REQUEST.remove(e.getEntity());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            SPINS.remove(e.getEntity());
            updateHeartCooldown(e.getEntity());
        });
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
        return hasHotbar(p) && p.getMainHandItem().is(RevivalBlossoms.AXE.get());
    }

    static boolean hasHotbar(Player p) {
        return p.isAlive() && !p.isRemoved() && !p.isSpectator() && RevivalBlossoms.isWeapon(p.getMainHandItem());
    }

    static void send(Player p, long sequence, UUID weapon, DawnpetalAbility ability) {
        PacketDistributor.sendToServer(new Use(sequence, p.level().getGameTime(), p.level().dimension().location(),
                p.getInventory().selected, weapon, ability.index));
    }

    private static CompoundTag persisted(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND))
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    static boolean heartSpinCoolingDown(Player player) {
        return heartCooldownOwner.get() == player && heartCooldownEnd > player.level().getGameTime();
    }

    private static void updateHeartCooldown(Player actor) {
        if (!(actor instanceof ServerPlayer player) || player.connection == null) return;
        long now = player.serverLevel().getServer().overworld().getGameTime();
        long ticks = Math.clamp(persisted(player).getLong(HEART_SPIN_COOLDOWN) - now,
                0, DawnpetalAbility.HEART_SPIN.cooldown);
        PacketDistributor.sendToPlayer(player, new HeartCooldown((int) ticks));
    }

    private static boolean permitted(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        return hasHotbar(p) && !p.isUsingItem() && !p.isShiftKeyDown()
                && level.getWorldBorder().isWithinBounds(p.blockPosition()) && level.hasChunkAt(p.blockPosition())
                && SakuraAccess.mayInteract(p, p.blockPosition());
    }

    private static void receive(ServerPlayer p, Use u) {
        if (!p.serverLevel().getServer().isSameThread() || u.sequence <= LAST_REQUEST.getOrDefault(p, 0L)) return;
        LAST_REQUEST.put(p, u.sequence);
        ItemStack weapon = p.getMainHandItem();
        DawnpetalAbility ability = DawnpetalAbility.at(p, u.ability);
        if (ability == null || !hasHotbar(p) || u.slot != p.getInventory().selected
                || !p.level().dimension().location().equals(u.dimension)
                || !u.weapon.equals(DawnpetalVanillaReach.syncedIdentity(weapon))) return;
        long tick = p.level().getGameTime();
        if (u.tick < tick - 40 || u.tick > tick + 10) return;
        if (!permitted(p)) return;

        boolean dirk = weapon.is(RevivalBlossoms.DIRK.get());
        boolean hearts = ability == DawnpetalAbility.HEART_SPIN;
        String cooldown = hearts ? HEART_SPIN_COOLDOWN : dirk ? DIRK_THROW_COOLDOWN : AXE_COOLDOWN;
        long now = p.serverLevel().getServer().overworld().getGameTime();
        if (persisted(p).getLong(cooldown) > now || !hearts && p.getCooldowns().isOnCooldown(weapon.getItem())) return;
        Spin current = SPINS.get(p);
        if (current != null && (hearts || current.hearts)) return;

        Spin spin = null;
        if (ability == DawnpetalAbility.SPIN || hearts) {
            spin = new Spin(p, u.weapon, tick, hearts);
            SPINS.put(p, spin);
        } else {
            boolean launched = dirk ? RelicFlight.castReturnbloom(p.serverLevel(), p, InteractionHand.MAIN_HAND)
                    : RelicFlight.castDawnpetal(p.serverLevel(), p, InteractionHand.MAIN_HAND);
            if (!launched) return;
        }
        persisted(p).putLong(cooldown, now + ability.cooldown);
        if (hearts) updateHeartCooldown(p);
        else p.getCooldowns().addCooldown(weapon.getItem(), ability.cooldown);
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
            if (spin.struck && !spin.hearts) DawnpetalSwing.finishGroundEffect(p);
        } else if (elapsed >= WINDUP && elapsed < WINDUP + SWEEP_TICKS) {
            if (elapsed == WINDUP && !spin.struck) {
                spin.struck = true;
                if (spin.hearts) emitHeartSplash(p, spin.cue.heading());
            }
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
            hurtWithSpin(p, target, spin.hearts);
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

    private static void emitHeartSplash(ServerPlayer actor, double heading) {
        ServerLevel level = actor.serverLevel();
        Vec3 center = actor.position();
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(center) > 32 * 32) continue;
            int preference = ReachLife.preference(viewer, "sakura_effects", 2);
            if (preference == 0) continue;
            for (int i = 0; i < HEART_COUNT; i += preference == 1 ? 2 : 1) {
                double angle = heading + DawnpetalSector.REVOLUTION * i / HEART_COUNT;
                double x = Math.cos(angle), z = Math.sin(angle);
                // Count zero sends one particle with an exact velocity instead of random Gaussian spread.
                level.sendParticles(viewer, MagicGlowParticles.HEART.get(), false,
                        center.x + x * .75, center.y + 1 + (i % 2) * .12, center.z + z * .75,
                        0, x, .18, z, .46);
            }
        }
    }

    private static void recordSweepDamage(LivingDamageEvent.Post event) {
        SweepHit hit = SWEEP_DAMAGE.get();
        if (hit != null && event.getEntity() == hit.target && event.getSource() == hit.source
                && event.getNewDamage() > 0) hit.didDamage = true;
    }

    private static void hurtWithSpin(ServerPlayer attacker, LivingEntity target, boolean hearts) {
        SweepHit previous = SWEEP_DAMAGE.get();
        var hit = new SweepHit(attacker.damageSources().playerAttack(attacker), target);
        SWEEP_DAMAGE.set(hit);
        try {
            if (target.hurt(hit.source, 8F) && hearts && hit.didDamage && target.isAlive()
                    && target.level() == attacker.level()) {
                Vec3 movement = target.getDeltaMovement();
                target.setDeltaMovement(movement.x, HEART_SPIN_LIFT, movement.z);
                target.hasImpulse = true;
                target.hurtMarked = true;
            }
        } finally {
            if (previous == null) SWEEP_DAMAGE.remove();
            else SWEEP_DAMAGE.set(previous);
        }
    }

    static boolean isSpinDamage(DamageSource source) {
        SweepHit hit = SWEEP_DAMAGE.get();
        return hit != null && hit.source == source;
    }

    private DawnpetalAbilities() {}
}
