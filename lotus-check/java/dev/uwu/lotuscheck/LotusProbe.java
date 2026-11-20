package dev.uwu.lotuscheck;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.RevivalBlossoms;
import dev.uwu.dimension.RevivalBloom;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Drives the release JAR through the real server damage and packet paths. */
@Mod("uwu_lotus_probe")
public final class LotusProbe {
    private static final ResourceLocation READY = ResourceLocation.fromNamespaceAndPath("uwudimension", "revival_ready");
    private static final float LETHAL = 100F;
    private static final int OVERLAP_OFFSET = 10;

    private LotusLightPlot plot;
    private RevivalWindow dirk;
    private RevivalWindow axe;
    private boolean finished;

    public LotusProbe() {
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LotusProbe::earlierRescue);
    }

    private void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (finished || server.getTickCount() < 12) return;
        try {
            ServerLevel level = server.overworld();
            if (plot == null) {
                verifyIsolation(level);
                plot = new LotusLightPlot(level);
                return;
            }

            if (dirk == null) {
                if (plot.ready()) dirk = revive(level, RevivalBlossoms.DIRK.get(), InteractionHand.MAIN_HAND, "returnbloom_dirk");
                return;
            }
            long now = level.getGameTime();
            long dirkAge = now - dirk.started;
            if (dirkAge == OVERLAP_OFFSET) {
                axe = revive(level, RevivalBlossoms.AXE.get(), InteractionHand.OFF_HAND, "dawnpetal_axe");
            }
            long axeAge = axe == null ? -1 : now - axe.started;
            plot.record(dirkAge, axeAge);
            dirk.check(level, dirkAge);
            if (axe != null && axeAge > 0) axe.check(level, axeAge);
            if (axeAge > RevivalBloom.DURATION_TICKS + 80) {
                throw new AssertionError("Block light failed to restore its baseline");
            }
            if (dirk.expired && axe != null && axe.expired && plot.restored()) {
                System.out.println("LOTUS DEDICATED SERVER PASS durationTicks=" + RevivalBloom.DURATION_TICKS
                        + " overlapTicks=" + (RevivalBloom.DURATION_TICKS - OVERLAP_OFFSET));
                finished = true;
                server.halt(false);
            }
        } catch (Throwable failure) {
            finished = true;
            System.err.println("LOTUS CHECK FAILED");
            failure.printStackTrace();
            server.halt(false);
        }
    }

    private static void verifyIsolation(ServerLevel level) throws ReflectiveOperationException {
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_SHOWDEATHMESSAGES).set(false, level.getServer());

        Actor ready = spawn(level, "ReadyOrdinary");
        ready.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RevivalBlossoms.DIRK.get()));
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(ready.player()));
        require(ready.channel().readyCount >= 1, "Equipping the ready weapon did not send readiness");
        require(RevivalBlossoms.remaining(ready.player(), ready.player().getMainHandItem()) == 0, "Ready weapon acquired a cooldown");
        assertDamageWorks(ready, "ready follower equipped");
        ready.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(ready.player()));
        require(ready.channel().readyCount >= 2, "Unequipping the ready weapon did not send readiness");
        assertDamageWorks(ready, "ready follower unequipped");
        assertDeath(ready, level.damageSources().generic(), 0, "ordinary lethal damage");
        remove(ready);

        Actor bypass = spawn(level, "BypassRefusal");
        bypass.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RevivalBlossoms.DIRK.get()));
        assertDeath(bypass, level.damageSources().genericKill(), 0, "bypassing revival eligibility");
        require(RevivalBlossoms.remaining(bypass.player(), bypass.player().getMainHandItem()) == 0,
                "Bypass refusal spent the revival charge");
        remove(bypass);

        Actor rescued = spawn(level, "EarlierRescue");
        rescued.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RevivalBlossoms.AXE.get()));
        strike(rescued, level.damageSources().generic(), LETHAL);
        require(rescued.player().getHealth() == rescued.player().getMaxHealth(), "Earlier rescue did not restore health");
        assertDamageWorks(rescued, "another mod's earlier death rescue");
        require(RevivalBlossoms.remaining(rescued.player(), rescued.player().getMainHandItem()) == 0,
                "Earlier rescue spent the revival charge");
        remove(rescued);

        Actor totem = spawn(level, "VanillaTotem");
        totem.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RevivalBlossoms.AXE.get()));
        totem.player().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        strike(totem, level.damageSources().generic(), LETHAL);
        require(totem.player().isAlive() && totem.player().getOffhandItem().isEmpty(), "Vanilla totem was not consumed");
        totem.player().removeAllEffects();
        totem.player().setAbsorptionAmount(0F);
        assertDamageWorks(totem, "vanilla totem rescue");
        require(RevivalBlossoms.remaining(totem.player(), totem.player().getMainHandItem()) == 0,
                "Totem spent the revival charge");
        remove(totem);
        System.out.println("LOTUS ISOLATION PASS readiness ordinary-death bypass-refusal earlier-rescue vanilla-totem");
    }

    private RevivalWindow revive(ServerLevel level, Item weapon, InteractionHand hand, String weaponName)
            throws ReflectiveOperationException {
        Actor actor = spawn(level, hand == InteractionHand.MAIN_HAND ? "MainHandRevive" : "OffhandRevive", plot.origin());
        ItemStack stack = new ItemStack(weapon);
        actor.player().setItemInHand(hand, stack);
        if (hand == InteractionHand.MAIN_HAND) {
            actor.player().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(RevivalBlossoms.AXE.get()));
        }
        require(RevivalBloom.DURATION_TICKS > 1 && RevivalBloom.DURATION_TICKS < RevivalBlossoms.COOLDOWN_TICKS,
                "Bloom duration must fit inside the existing shared cooldown");
        long successTick = level.getGameTime();
        strike(actor, level.damageSources().generic(), LETHAL);
        require(actor.player().getHealth() == actor.player().getMaxHealth(), "Successful revival did not fully heal");
        require(actor.channel().bloomCount == 1 && actor.channel().lastBloom.startTick() == successTick,
                "The successful revival did not send exactly one bloom at the server start tick");
        var cue = actor.channel().lastBloom;
        require(cue.owner().equals(actor.player().getUUID()) && cue.dimension().equals(level.dimension().location())
                        && cue.origin().equals(actor.player().position()),
                "The bloom was not centered on the revived player");
        require(RevivalBlossoms.remaining(actor.player(), stack) == RevivalBlossoms.COOLDOWN_TICKS,
                "Successful revival did not commit the existing shared cooldown");
        assertProtected(actor, level.damageSources().generic());
        assertProtected(actor, level.damageSources().fall());
        return new RevivalWindow(actor, weaponName, successTick);
    }

    private static final class RevivalWindow {
        final Actor actor;
        final String weapon;
        final long started;
        boolean expired;

        RevivalWindow(Actor actor, String weapon, long started) {
            this.actor = actor;
            this.weapon = weapon;
            this.started = started;
        }

        void check(ServerLevel level, long age) {
            if (expired) return;
            int duration = RevivalBloom.DURATION_TICKS;
            require(age > 0 && age <= duration, "Missed the exact server tick boundary for the lotus");
            if (age < duration) {
                assertProtected(actor, level.damageSources().generic());
                if (age == duration - 1) assertProtected(actor, level.damageSources().genericKill());
                return;
            }
            assertDamageWorks(actor, "first tick after the bloom");
            require(actor.channel().bloomCount == 1, "An incoming hit restarted the lotus");
            require(RevivalBlossoms.remaining(actor.player(), actor.player().getOffhandItem())
                            == RevivalBlossoms.COOLDOWN_TICKS - duration,
                    "The shared cooldown diverged from the existing cooldown");
            assertDeath(actor, level.damageSources().generic(), 1, "shared-cooldown refusal");
            remove(actor);
            expired = true;
            System.out.println("LOTUS WINDOW PASS weapon=" + weapon + " protectedTicks=" + duration
                    + " firstDamageTick=" + age + " blooms=1");
        }
    }

    private static void earlierRescue(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && "EarlierRescue".equals(player.getGameProfile().getName())) {
            player.setHealth(player.getMaxHealth());
            event.setCanceled(true);
        }
    }

    private static Actor spawn(ServerLevel level, String name) throws ReflectiveOperationException {
        return spawn(level, name, null);
    }

    private static Actor spawn(ServerLevel level, String name, BlockPos location) throws ReflectiveOperationException {
        MinecraftServer server = level.getServer();
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name),
                ClientInformation.createDefault());
        RecordingListener channel = new RecordingListener(server, player);
        var spawnProtection = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        spawnProtection.setAccessible(true);
        spawnProtection.setInt(player, 0);
        if (location != null) {
            player.setPos(location.getX() + .5, location.getY(), location.getZ() + .5);
            player.setNoGravity(true);
        }
        // Attach to real tracking so sendToPlayersTrackingEntityAndSelf takes its ordinary path.
        level.addNewPlayer(player);
        require(level.getEntity(player.getId()) == player, "Fixture player was not loaded in the world");
        return new Actor(player, channel);
    }

    private static void remove(Actor actor) {
        actor.player().serverLevel().removePlayerImmediately(actor.player(), Entity.RemovalReason.DISCARDED);
    }

    private static boolean strike(Actor actor, DamageSource source, float amount) {
        // Remove the vanilla hurt cooldown so it cannot be mistaken for bloom immunity.
        actor.player().invulnerableTime = 0;
        return actor.player().hurt(source, amount);
    }

    private static void assertProtected(Actor actor, DamageSource source) {
        float health = actor.player().getMaxHealth();
        require(!strike(actor, source, LETHAL) && actor.player().getHealth() == health,
                "Damage hurt a revived player during the bloom: " + source.getMsgId());
        require(actor.channel().bloomCount == 1, "Damage during protection started another bloom");
    }

    private static void assertDamageWorks(Actor actor, String label) {
        float health = actor.player().getMaxHealth();
        int bloomCount = actor.channel().bloomCount;
        actor.player().setHealth(health);
        require(strike(actor, actor.player().damageSources().generic(), 2F)
                        && actor.player().getHealth() == health - 2F,
                "Unexpected protection for " + label);
        require(actor.channel().bloomCount == bloomCount, "A nonlethal hit sent a bloom: " + label);
    }

    private static void assertDeath(Actor actor, DamageSource source, int blooms, String label) {
        require(strike(actor, source, LETHAL) && actor.player().isDeadOrDying(), "Expected a death for " + label);
        require(actor.channel().bloomCount == blooms, "An unsuccessful revive sent a bloom: " + label);
        // Immediately rearm this test entity to expose any protection keyed to the refused branch.
        assertDamageWorks(actor, label);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Actor(ServerPlayer player, RecordingListener channel) {}

    private static final class RecordingListener extends ServerGamePacketListenerImpl {
        private final UUID actor;
        int readyCount;
        int bloomCount;
        RevivalBloom.Cue lastBloom;

        RecordingListener(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
            actor = player.getUUID();
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            if (packet instanceof ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(part -> send(part, null));
                return;
            }
            if (packet instanceof ClientboundCustomPayloadPacket payload) {
                if (payload.payload().type().id().equals(READY)) readyCount++;
                if (payload.payload() instanceof RevivalBloom.Cue bloom && bloom.owner().equals(actor)) {
                    bloomCount++;
                    lastBloom = bloom;
                }
            }
        }
    }
}
