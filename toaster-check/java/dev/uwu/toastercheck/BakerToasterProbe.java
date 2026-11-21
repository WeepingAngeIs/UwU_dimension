package dev.uwu.toastercheck;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.ReefLife;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Follows the actual kitchen plans and native block ticks in a disposable game server. */
public final class BakerToasterProbe {
    private static final ResourceLocation TOASTER = id("baker_toaster");
    private static final ResourceLocation BREAD = id("uwu_bread");
    private static final ResourceLocation DING = id("toaster_ding");
    private static final String TOKEN = "UwUGeneratedToast";
    private static final int REJECTED_RETRY_TICKS = 200;

    private enum Phase { PLAN, LOAD, INELIGIBLE, REWARD, STALE_RELOAD, DONE }

    private final Set<ChunkPos> newlyGeneratedReach = ConcurrentHashMap.newKeySet();
    private Phase phase = Phase.PLAN;
    private ServerLevel level;
    private List<BlossomVillage.Plan> plans = List.of();
    private int kitchenIndex, tick, phaseStarted, attempts, rejectedAt;
    private BlockPos position, smoker;
    private ChunkPos kitchenChunk, smokerChunk;
    private CompoundTag readySnapshot;
    private UUID token;
    private ItemEntity published;
    private RecordingListener first, second;

    public BakerToasterProbe() {
        NeoForge.EVENT_BUS.addListener(this::trackFreshChunk);
        NeoForge.EVENT_BUS.addListener(this::trackBread);
    }

    public ServerLevel level() {
        return level;
    }

    public List<BlossomVillage.Plan> plans() {
        return plans;
    }

    public boolean tick(MinecraftServer server) throws IOException, ReflectiveOperationException {
        ++tick;
        switch (phase) {
            case PLAN -> {
                level = server.getLevel(ReefLife.REACH);
                require(level != null, "Sakura Reach did not load");
                plans = List.of(BlossomVillage.first(level, false), BlossomVillage.first(level, true));
                loadKitchen();
            }
            case LOAD -> {
                if (!level.isPositionEntityTicking(position) || !level.isPositionEntityTicking(smoker)) {
                    require(elapsed() < 500, "Native Baker kitchen chunks failed to start ticking at " + position);
                    return false;
                }
                verifyKitchen();
                Direction intoRoom = level.getBlockState(position).getValue(HorizontalDirectionalBlock.FACING);
                List<BlockPos> stands = standingSpots(intoRoom);
                first = spawn("BakerFixtureOne", stands.get(0), GameType.CREATIVE);
                second = spawn("BakerFixtureTwo", stands.get(1), GameType.SPECTATOR);
                require(!first.player.getUUID().equals(second.player.getUUID()), "Expected two distinct players");
                next(Phase.INELIGIBLE);
            }
            case INELIGIBLE -> {
                if (elapsed() < 30) return false;
                require(attempts == 0 && first.dings == 0 && second.dings == 0 && snapshot().hasUUID(TOKEN),
                        "Creative or spectator presence consumed a generated Baker reward");
                first.player.setGameMode(GameType.SURVIVAL);
                second.player.setGameMode(GameType.ADVENTURE);
                next(Phase.REWARD);
            }
            case REWARD -> {
                if (kitchenIndex == 0 && rejectedAt != 0 && elapsedSinceReject() < REJECTED_RETRY_TICKS) {
                    require(attempts == 1 && first.dings == 0 && second.dings == 0
                                    && snapshot().hasUUID(TOKEN) && Files.isRegularFile(receipt("ready"))
                                    && !Files.exists(receipt("spent")),
                            "A rejected item join did not quietly restore the ready receipt");
                    return false;
                }
                if (published == null) {
                    require(elapsed() < 280, "No real bread item was accepted at " + position
                            + ", attempts=" + attempts + ", rejectTick=" + rejectedAt
                            + ", player distances=" + first.player.distanceToSqr(Vec3.atCenterOf(position)) + "/"
                            + second.player.distanceToSqr(Vec3.atCenterOf(position)));
                    return false;
                }
                require(attempts == expectedAttempts() && level.getEntity(published.getUUID()) == published,
                        "Native tick did not publish exactly one real bread item at " + position);
                verifyDings();
                verifySpentReceipt();
                restoreStaleSnapshot();
                next(Phase.STALE_RELOAD);
            }
            case STALE_RELOAD -> {
                if (elapsed() < 30) return false;
                require(!snapshot().hasUUID(TOKEN) && attempts == expectedAttempts(),
                        "A stale saved toaster respawned a reward or retained its spent token");
                verifyDings();
                verifySpentReceipt();
                System.out.println("BAKER KITCHEN PASS " + faction() + " generated at " + position
                        + ", real smoker, two eligible players, one bread, native dings, persisted spent receipt"
                        + (kitchenIndex == 0 ? ", rejected spawn retried" : ""));
                cleanKitchen();
                ++kitchenIndex;
                if (kitchenIndex < plans.size()) {
                    loadKitchen();
                } else {
                    next(Phase.DONE);
                    System.out.println("BAKER TOASTER SERVER PASS authentic Petal and Dusk Baker kitchens,"
                            + " eligibility, two listeners, rejected-spawn retry and stale-NBT receipt reload");
                }
            }
            case DONE -> { return true; }
        }
        return phase == Phase.DONE;
    }

    private void loadKitchen() {
        BlossomVillage.Plan plan = plans.get(kitchenIndex);
        BlossomVillage.House kitchen = plan.houses().stream().filter(home -> home.job() == Blocks.SMOKER)
                .findFirst().orElseThrow();
        position = plan.site().pos(kitchen.x() + kitchen.width() - 2, kitchen.floor() + 2,
                kitchen.z() + kitchen.side() * (kitchen.depth() - 1));
        smoker = plan.workstation(kitchen);
        kitchenChunk = new ChunkPos(position);
        smokerChunk = new ChunkPos(smoker);
        level.setChunkForced(kitchenChunk.x, kitchenChunk.z, true);
        if (!smokerChunk.equals(kitchenChunk)) level.setChunkForced(smokerChunk.x, smokerChunk.z, true);
        next(Phase.LOAD);
    }

    private void verifyKitchen() throws IOException {
        var plan = plans.get(kitchenIndex);
        var kitchen = plan.houses().stream().filter(home -> home.job() == Blocks.SMOKER)
                .findFirst().orElseThrow();
        var state = level.getBlockState(position);
        require(newlyGeneratedReach.contains(kitchenChunk), "No native fresh chunk generation witness at " + position);
        require(level.getBlockState(smoker).is(Blocks.SMOKER), "No actual Baker smoker at " + smoker);
        require(BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(TOASTER),
                faction() + " generated " + state + " instead of a toaster at " + position);
        var localFacing = kitchen.side() > 0 ? Direction.NORTH : Direction.SOUTH;
        var local = localFacing.getNormal();
        var worldFacing = Direction.fromDelta(plan.site().wx(local.getX(), local.getZ()) - plan.site().x(), 0,
                plan.site().wz(local.getX(), local.getZ()) - plan.site().z());
        require(state.getValue(HorizontalDirectionalBlock.FACING) == worldFacing,
                "Generated toaster slot faces away from the kitchen at " + position);
        var worktop = level.getBlockState(position.below());
        var silkwood = BuiltInRegistries.BLOCK.get(id("silkwood_planks"));
        require(plan.dusk() ? worktop.is(Blocks.DARK_OAK_PLANKS)
                        : worktop.is(Blocks.CHERRY_PLANKS) || worktop.is(silkwood),
                "Generated toaster has the wrong kitchen worktop at " + position);
        readySnapshot = snapshot();
        require(readySnapshot.hasUUID(TOKEN), "Generated kitchen lacks authentic initial block-entity token");
        token = readySnapshot.getUUID(TOKEN);
        require(Files.isRegularFile(receipt("ready")) && !Files.exists(receipt("spent")),
                "Fresh kitchen did not publish its on-disk ready receipt");
        System.out.println("BAKER KITCHEN FOUND " + faction() + " " + position + " smoker=" + smoker);
    }

    private void trackFreshChunk(ChunkEvent.Load event) {
        if (event.isNewChunk() && event.getLevel() instanceof ServerLevel world
                && world.dimension().equals(ReefLife.REACH)) {
            newlyGeneratedReach.add(event.getChunk().getPos());
        }
    }

    private void trackBread(EntityJoinLevelEvent event) {
        if (phase != Phase.INELIGIBLE && phase != Phase.REWARD && phase != Phase.STALE_RELOAD) return;
        if (event.getLevel() != level || !(event.getEntity() instanceof ItemEntity item)
                || !BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).equals(BREAD)
                || item.position().distanceToSqr(Vec3.atCenterOf(position)) > 1) return;
        ++attempts;
        require(!event.loadedFromDisk() && item.getItem().getCount() == 1 && item.hasPickUpDelay()
                        && item.getDeltaMovement().y > .2,
                "Baker did not pop a new single bread with an upward motion and pickup delay");
        if (phase == Phase.REWARD && kitchenIndex == 0 && attempts == 1) {
            rejectedAt = tick;
            event.setCanceled(true);
        } else {
            published = item;
        }
    }

    private CompoundTag snapshot() {
        BlockEntity toaster = level.getBlockEntity(position);
        require(toaster != null && BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(toaster.getType()).equals(TOASTER),
                "No generated Baker block entity at " + position);
        return toaster.saveWithFullMetadata(level.registryAccess());
    }

    private Path receipt(String suffix) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/uwudimension_baker_toasters/sakura_reach")
                .resolve(position.getX() + "_" + position.getY() + "_" + position.getZ() + "." + suffix);
    }

    private void verifySpentReceipt() throws IOException {
        require(!Files.exists(receipt("ready")) && Files.isRegularFile(receipt("spent")),
                "Baker did not durably consume the ready receipt at " + position);
        var persisted = NbtIo.readCompressed(receipt("spent"), NbtAccounter.unlimitedHeap());
        require(persisted.getInt("Version") == 1 && persisted.getLong("Position") == position.asLong()
                        && persisted.getString("Dimension").equals(ReefLife.REACH.location().toString())
                        && persisted.getUUID("Token").equals(token),
                "Claim file does not contain the original generated position and token");
    }

    private void restoreStaleSnapshot() throws ReflectiveOperationException {
        var worldClaims = Class.forName("dev.uwu.dimension.ToastClaims").getDeclaredField("WORLDS");
        worldClaims.setAccessible(true);
        require(((Map<?, ?>) worldClaims.get(null)).remove(level.getServer()) != null,
                "Fixture did not drop the loaded toaster claim store before replay");
        var reloaded = BlockEntity.loadStatic(position, level.getBlockState(position), readySnapshot.copy(),
                level.registryAccess());
        require(reloaded != null, "Could not deserialize the stale generated block entity");
        // Swap the object while keeping the block. Removing the block itself would retire a claim.
        level.setBlockEntity(reloaded);
        require(level.getBlockEntity(position) == reloaded && snapshot().getUUID(TOKEN).equals(token),
                "Actual loaded fixture entity did not carry the saved stale token");
    }

    private List<BlockPos> standingSpots(Direction intoRoom) {
        var stands = new java.util.ArrayList<BlockPos>();
        BlockPos center = position.below();
        BlockPos preferred = center.relative(intoRoom, 2);
        for (BlockPos feet : BlockPos.betweenClosed(center.offset(-3, 0, -3), center.offset(3, 0, 3))) {
            Vec3 at = Vec3.atBottomCenterOf(feet);
            if (at.distanceToSqr(Vec3.atCenterOf(position)) > 16
                    || !level.isPositionEntityTicking(feet)) continue;
            var body = new AABB(at.x - .3, at.y, at.z - .3, at.x + .3, at.y + 1.8, at.z + .3);
            if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                    && level.noCollision(body)) stands.add(feet.immutable());
        }
        stands.sort(Comparator.comparingDouble(feet -> feet.distSqr(preferred)));
        require(stands.size() >= 2, "Kitchen lacks two collision-free nearby spaces at " + position);
        return stands;
    }

    private RecordingListener spawn(String name, BlockPos feet, GameType gameMode) throws ReflectiveOperationException {
        MinecraftServer server = level.getServer();
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name),
                ClientInformation.createDefault());
        var listener = new RecordingListener(server, player, position);
        player.moveTo(feet.getX() + .5, feet.getY(), feet.getZ() + .5, 0, 0);
        player.setGameMode(gameMode);
        player.setNoGravity(true);
        onlinePlayers(server).add(player);
        level.addNewPlayer(player);
        require(level.getEntity(player.getId()) == player && !player.hasDisconnected() && !player.isFakePlayer()
                        && player.distanceToSqr(Vec3.atCenterOf(position)) <= 16,
                "Independent real server actor failed to reach the toaster for " + name);
        return listener;
    }

    private void verifyDings() {
        require(first.dings == 1 && second.dings == 1,
                "One Baker ding must reach both nearby players, got " + first.dings + "/" + second.dings);
    }

    private void cleanKitchen() throws ReflectiveOperationException {
        var players = onlinePlayers(level.getServer());
        for (var actor : List.of(first, second)) {
            players.remove(actor.player);
            level.removePlayerImmediately(actor.player, Entity.RemovalReason.DISCARDED);
            actor.transport.finishAndReleaseAll();
        }
        published.discard();
        level.setChunkForced(kitchenChunk.x, kitchenChunk.z, false);
        if (!smokerChunk.equals(kitchenChunk)) level.setChunkForced(smokerChunk.x, smokerChunk.z, false);
        attempts = rejectedAt = 0;
        published = null;
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> onlinePlayers(MinecraftServer server) throws ReflectiveOperationException {
        var players = PlayerList.class.getDeclaredField("players");
        players.setAccessible(true);
        return (List<ServerPlayer>) players.get(server.getPlayerList());
    }

    private int expectedAttempts() {
        return kitchenIndex == 0 ? 2 : 1;
    }

    private int elapsed() {
        return tick - phaseStarted;
    }

    private int elapsedSinceReject() {
        return tick - rejectedAt;
    }

    private String faction() {
        return plans.get(kitchenIndex).dusk() ? "Dusk" : "Petal";
    }

    private void next(Phase next) {
        phase = next;
        phaseStarted = tick;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension", path);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static final class RecordingListener extends ServerGamePacketListenerImpl {
        private final EmbeddedChannel transport;
        private final BlockPos kitchen;
        private int dings;

        RecordingListener(MinecraftServer server, ServerPlayer player, BlockPos kitchen) {
            this(server, new Connection(PacketFlow.SERVERBOUND), player, kitchen);
        }

        private RecordingListener(MinecraftServer server, Connection connection, ServerPlayer player, BlockPos kitchen) {
            super(server, connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
            this.kitchen = kitchen;
            transport = new EmbeddedChannel(connection);
            require(connection.channel() == transport, "Fixture player memory channel failed");
        }

        @Override public void send(Packet<?> packet, PacketSendListener listener) {
            if (!(packet instanceof ClientboundSoundPacket sound) || !sound.getSound().value().getLocation().equals(DING)) return;
            require(sound.getSource() == SoundSource.BLOCKS && Math.abs(sound.getVolume() - .55F) < .001F
                            && Math.abs(sound.getPitch() - 1F) < .001F
                            && Vec3.atCenterOf(kitchen).distanceToSqr(new Vec3(sound.getX(), sound.getY(), sound.getZ())) < .1,
                    "Baker ding used wrong sound source, volume, pitch or position");
            ++dings;
        }
    }
}
