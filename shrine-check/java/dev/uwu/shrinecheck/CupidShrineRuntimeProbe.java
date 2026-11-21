package dev.uwu.shrinecheck;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.CupidShrine;
import dev.uwu.dimension.CupidShrineVisits;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Real loaded shrine, world event and native Player effects in the disposable hosted world. */
public final class CupidShrineRuntimeProbe {
    private static final ResourceKey<MobEffect> EFFECT = ResourceKey.create(Registries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("uwudimension", "cupid_grace"));
    private static final float START_HEALTH = 10F;

    private final ServerLevel level;
    private final BlockPos anchor;
    private final List<Visitor> visitors = new ArrayList<>();
    private List<ServerPlayer> online;
    private Map<UUID, ServerPlayer> byId;
    private Holder<MobEffect> grace;
    private GameRules.BooleanValue naturalRegen;
    private boolean oldNaturalRegen;
    private boolean done;
    private long firstPulse = -1;
    private long secondPulse;
    private Visitor horizontal, outer, upper, above, lower, below, ordinary, spectator;

    public CupidShrineRuntimeProbe(ServerLevel level, BlockPos anchor) {
        this.level = level;
        this.anchor = anchor.immutable();
    }

    public boolean tick() throws ReflectiveOperationException {
        if (done) return true;
        try {
            return advance();
        } catch (ReflectiveOperationException | RuntimeException | AssertionError failure) {
            clean();
            throw failure;
        }
    }

    private boolean advance() throws ReflectiveOperationException {
        long now = level.getGameTime();
        if (firstPulse < 0) {
            require(level.getBlockState(anchor).is(CupidShrine.BLOCK.get())
                            && level.getBlockEntity(anchor) != null && level.isPositionEntityTicking(anchor),
                    "Runtime probe needs the generated, entity-ticking shrine at " + anchor);
            if (100 - now % 100 < 14) return false;
            prepare(now);
            return false;
        }
        require(now <= secondPulse + 10, "Missed shrine pulse " + now + " after " + secondPulse);

        // Exactly like a connected player's action loop, one native player tick per world tick.
        // The in-process connection is intentionally never installed in the server I/O loop.
        for (Visitor visitor : visitors) {
            visitor.player.doTick();
            visitor.player.setPos(visitor.feet.x, visitor.feet.y, visitor.feet.z);
            visitor.player.setDeltaMovement(Vec3.ZERO);
        }

        if (now == firstPulse - 8) {
            ordinary.player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, false, true));
        } else if (now == firstPulse - 1) {
            for (Visitor visitor : visitors) expectNoGrace(visitor);
        } else if (now == firstPulse) {
            for (Visitor visitor : List.of(horizontal, upper, lower)) expectDose(visitor);
            for (Visitor visitor : List.of(outer, above, below, ordinary, spectator)) expectNoGrace(visitor);
            var nativeEffect = ordinary.player.getEffect(MobEffects.REGENERATION);
            require(nativeEffect != null && nativeEffect.getAmplifier() == 1 && nativeEffect.getDuration() >= 30
                            && nativeEffect.getDuration() <= 33,
                    "Shrine replaced, shortened, or amplified an existing native Regeneration II");
            // The two active effects reach their native due ticks together. Grace must yield that
            // tick to ordinary Regeneration even though Grace was granted first.
            lower.player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 25, 1, false, false, true));
            horizontal.move(4.125, 0, 0);
        } else if (now == firstPulse + 5) {
            for (Visitor visitor : List.of(horizontal, upper, lower)) {
                require(visitor.player.getHealth() == START_HEALTH + 1F,
                        visitor.name + " did not receive exactly one native 1 HP heal: " + visitor.player.getHealth());
            }
            for (Visitor visitor : List.of(outer, above, below, spectator)) expectUntouched(visitor);
            outer.move(-4.0, 0, 0);
        } else if (now == firstPulse + 60) {
            for (Visitor visitor : visitors) expectNoGrace(visitor);
            require(horizontal.player.getHealth() == START_HEALTH + 1F && upper.player.getHealth() == START_HEALTH + 1F
                            && lower.player.getHealth() == START_HEALTH + 1F,
                    "First doses repeated healing during the same pulse");
            require(!ordinary.player.hasEffect(MobEffects.REGENERATION)
                            && ordinary.player.getHealth() == START_HEALTH + 1F,
                    "Ordinary Regeneration did not finish with its original native healing cadence");
            expectUntouched(outer);
            horizontal.move(4.0, 0, 0);
        } else if (now == secondPulse - 1) {
            for (Visitor visitor : visitors) expectNoGrace(visitor);
        } else if (now == secondPulse) {
            for (Visitor visitor : List.of(horizontal, outer, upper, lower, ordinary)) expectDose(visitor);
            for (Visitor visitor : List.of(above, below, spectator)) expectNoGrace(visitor);
        } else if (now == secondPulse + 5) {
            require(outer.player.getHealth() == START_HEALTH + 1F && ordinary.player.getHealth() == START_HEALTH + 2F
                            && upper.player.getHealth() == START_HEALTH + 2F
                            && lower.player.getHealth() == START_HEALTH + 2F
                            && horizontal.player.getHealth() == START_HEALTH + 2F,
                    "Eligible visitors did not resume one independent heal per pulse");
            for (Visitor visitor : List.of(above, below, spectator)) expectUntouched(visitor);
            clean();
            done = true;
            System.out.println("CUPID SHRINE RUNTIME PASS radius=4 vertical=-.5..1.25 pulse=100 dose=51"
                    + " confirmed=true ordinaryRegenerationPriority=true noDoubleHealing=true"
                    + " independentVisitors=true exitAndReentry=true");
        }
        return done;
    }

    private void prepare(long now) throws ReflectiveOperationException {
        grace = BuiltInRegistries.MOB_EFFECT.getHolderOrThrow(EFFECT);
        online = onlinePlayers(level.getServer().getPlayerList());
        byId = playersByUuid(level.getServer().getPlayerList());
        naturalRegen = level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION);
        oldNaturalRegen = naturalRegen.get();
        naturalRegen.set(false, level.getServer());
        horizontal = spawn("Horizontal", 4.0, 0, 0, GameType.SURVIVAL);
        outer = spawn("Outer", -4.125, 0, 0, GameType.SURVIVAL);
        upper = spawn("Upper", 0, 1.25, 2.75, GameType.SURVIVAL);
        above = spawn("Above", 0, 1.375, -2.75, GameType.SURVIVAL);
        lower = spawn("Lower", 1.75, -.5, -1.5, GameType.SURVIVAL);
        below = spawn("Below", -1.75, -.625, -1.5, GameType.SURVIVAL);
        ordinary = spawn("Ordinary", -1.75, .25, 1.5, GameType.SURVIVAL);
        spectator = spawn("Spectator", 1.75, .25, 1.75, GameType.SPECTATOR);
        var closest = CupidShrineVisits.class.getDeclaredMethod("closest", Level.class, Vec3.class,
                int.class, double.class, double.class);
        closest.setAccessible(true);
        for (Visitor visitor : List.of(horizontal, upper, lower, ordinary, spectator)) {
            require(anchor.equals(closest.invoke(null, level, visitor.feet, 4, -.5, 1.25)),
                    visitor.name + " did not resolve the actual loaded shrine at " + anchor);
        }
        for (Visitor visitor : List.of(outer, above, below)) {
            require(closest.invoke(null, level, visitor.feet, 4, -.5, 1.25) == null,
                    visitor.name + " incorrectly resolved a shrine outside its field");
        }
        firstPulse = (now / 100 + 1) * 100;
        secondPulse = firstPulse + 100;
    }

    private Visitor spawn(String name, double x, double y, double z, GameType mode) {
        var server = level.getServer();
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "Shrine" + name),
                ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new ServerGamePacketListenerImpl(server, connection, player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        var visitor = new Visitor(name, player, new EmbeddedChannel(connection));
        visitors.add(visitor);
        player.setGameMode(mode);
        player.setInvulnerable(true);
        player.setNoGravity(true);
        player.setHealth(START_HEALTH);
        visitor.move(x, y, z);
        online.add(player);
        byId.put(player.getUUID(), player);
        level.addNewPlayer(player);
        require(level.players().contains(player), "Actual shrine tick cannot find fixture visitor " + name);
        return visitor;
    }

    private void expectDose(Visitor visitor) {
        var dose = visitor.player.getEffect(grace);
        // The event grants 51 ticks; the in-process player's native doTick above consumes the first.
        require(dose != null && dose.getAmplifier() == 0 && dose.getDuration() == 50 && !dose.isVisible()
                        && dose.showIcon(), visitor.name + " did not get the intended native Cupid Grace dose: " + dose);
    }

    private void expectNoGrace(Visitor visitor) {
        require(!visitor.player.hasEffect(grace), visitor.name + " received an unexpected shrine dose");
    }

    private void expectUntouched(Visitor visitor) {
        expectNoGrace(visitor);
        require(visitor.player.getHealth() == START_HEALTH, visitor.name + " received unexpected healing");
    }

    private void clean() {
        for (Visitor visitor : visitors) {
            var player = visitor.player;
            online.remove(player);
            byId.remove(player.getUUID(), player);
            if (level.players().contains(player)) level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            visitor.channel.finishAndReleaseAll();
        }
        visitors.clear();
        if (naturalRegen != null) {
            naturalRegen.set(oldNaturalRegen, level.getServer());
            naturalRegen = null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> onlinePlayers(PlayerList list) throws ReflectiveOperationException {
        var field = PlayerList.class.getDeclaredField("players");
        field.setAccessible(true);
        return (List<ServerPlayer>) field.get(list);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> playersByUuid(PlayerList list) throws ReflectiveOperationException {
        var field = PlayerList.class.getDeclaredField("playersByUUID");
        field.setAccessible(true);
        return (Map<UUID, ServerPlayer>) field.get(list);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private final class Visitor {
        final String name;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        Vec3 feet;

        Visitor(String name, ServerPlayer player, EmbeddedChannel channel) {
            this.name = name;
            this.player = player;
            this.channel = channel;
        }

        void move(double x, double y, double z) {
            feet = Vec3.atBottomCenterOf(anchor).add(x, y, z);
            player.setPos(feet.x, feet.y, feet.z);
        }
    }
}
