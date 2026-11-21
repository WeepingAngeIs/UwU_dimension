package dev.uwu.chestcheck;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.NativeRewardChestBlockEntity;
import dev.uwu.dimension.NativeRewardChestMenu;
import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.loot.NativeRewardSite;
import dev.uwu.dimension.loot.PersonalLootStore;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Runs the real chest block, menu, scheduled ticks and outbound packets on a disposable server. */
public final class DuskChestProbe {
    private static final ResourceLocation OPEN = id("dusk_chest_open");
    private static final ResourceLocation CLOSE = id("dusk_chest_close");
    private static final BlockPos CHEST = new BlockPos(5173, 195, 5173);
    private static final ChunkPos CHEST_CHUNK = new ChunkPos(CHEST);
    private static final BlockPos LOCKED = CHEST.east(5);
    private static final BlockPos PETAL = CHEST.south(5);
    private static final BlockPos PEARL = PETAL.east(5);
    private static final Set<String> LID_FIELDS = Set.of("LidOpen", "LidStart", "LidStartTick");

    private enum Step { PREPARE, TICKING, TRACK, FIRST, BOTH, LAST, REVERSE, REOPENED, OUT_OF_RANGE, DISCONNECT, CLOSED }

    private final List<Actor> actors = new ArrayList<>();
    private Step step = Step.PREPARE;
    private long started;
    private long entered;
    private ServerLevel level;
    private Actor a;
    private Actor b;
    private Actor late;
    private NativeRewardChestBlockEntity chest;
    private NativeRewardSite site;
    private int claimedSlot;
    private long claimedRevision;

    public boolean tick(MinecraftServer server) throws ReflectiveOperationException {
        try {
            return advance(server);
        } catch (ReflectiveOperationException | RuntimeException | AssertionError failure) {
            System.err.println("DUSK CHEST CHECK FAILED in " + step);
            throw failure;
        }
    }

    private boolean advance(MinecraftServer server) throws ReflectiveOperationException {
        for (Actor actor : actors) actor.receiveChunks();
        if (level != null) require(level.getGameTime() - started < 600,
                "Chest fixture timed out in " + step + ", packets: " + actors.stream().map(Actor::describe).toList());
        switch (step) {
            case PREPARE -> {
                level = server.getLevel(ReefLife.REACH);
                require(level != null, "Sakura Reach did not load");
                started = level.getGameTime();
                level.setChunkForced(CHEST_CHUNK.x, CHEST_CHUNK.z, true);
                next(Step.TICKING);
            }
            case TICKING -> {
                if (!level.isPositionEntityTicking(CHEST)
                        || level.getChunkSource().chunkMap.getChunkToSend(CHEST_CHUNK.toLong()) == null) return false;
                UUID namespace = UUID.randomUUID();
                for (BlockPos floor : BlockPos.betweenClosed(CHEST.offset(-2, -1, -2), PEARL.offset(2, -1, 2))) {
                    level.setBlock(floor, Blocks.CALCITE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                site = place(namespace, CHEST, "dusk_reward_chest", "tide_archive", null);
                place(namespace, LOCKED, "dusk_reward_chest", "mirror_court", LOCKED.above().east(13));
                place(namespace, PETAL, "petal_reward_chest", "inverted_shrine", null);
                place(namespace, PEARL, "pearl_reward_chest", "rose_bathhouse", null);
                chest = chest(CHEST);
                a = spawn("ChestViewerA", CHEST.north());
                b = spawn("ChestViewerB", CHEST.west());
                next(Step.TRACK);
            }
            case TRACK -> {
                if (a.wire.chunkSnapshots.isEmpty() || b.wire.chunkSnapshots.isEmpty()) return false;
                var watchers = level.getChunkSource().chunkMap.getPlayers(CHEST_CHUNK, false);
                require(watchers.contains(a.player) && watchers.contains(b.player),
                        "Native ChunkMap does not recognize both viewers as active chest chunk trackers");
                move(a, LOCKED.north());
                interact(a, LOCKED);
                require(a.player.containerMenu == a.player.inventoryMenu && !tag(chest(LOCKED)).getBoolean("LidOpen"),
                        "Locked Mirror Court chest opened a menu or lid");
                for (BlockPos control : List.of(PETAL, PEARL)) {
                    move(a, control.north());
                    open(a, control);
                    require(chest(control).getUpdatePacket() == null && tag(chest(control)).isEmpty(),
                            "Non-Dusk chest advertised the moving lid");
                    a.player.closeContainer();
                }
                expectCues(a);
                expectCues(b);
                move(a, CHEST.north());
                NativeRewardChestMenu initial = open(a, CHEST);
                interact(a, CHEST);
                require(a.player.containerMenu == initial && snapshot(a).revision() == 1,
                        "Repeated interaction replaced the live menu or rerolled it");
                require(tag(chest).getBoolean("LidOpen") && near(chest.lidOpenness(0), 0F)
                                && near(chest.lidOpenness(.5F), .05F),
                        "Single viewer did not start ten-tick opening and fractional interpolation");
                expectCues(a, OPEN);
                expectCues(b, OPEN);
                next(Step.FIRST);
            }
            case FIRST -> {
                if (elapsed() < 2) return false;
                float moving = chest.lidOpenness(0);
                require(near(moving, .2F), "Lid did not advance linearly to 20%, actual " + moving);
                late = spawn("ChestObserver", CHEST.south());
                late.receiveChunks();
                open(b, CHEST);
                require(near(chest.lidOpenness(0), moving), "Second viewer restarted the opening animation");
                expectCues(a, OPEN);
                expectCues(b, OPEN);
                expectCues(late);
                require(a.wire.lidEvents.equals(List.of(1)) && b.wire.lidEvents.equals(List.of(1)),
                        "Initial in-range block-event sequence is wrong");
                claim(a, b);
                a.player.closeContainer();
                require(menu(b).stillValid(b.player) && tag(chest).getBoolean("LidOpen"),
                        "First viewer closed the last viewer's lid");
                next(Step.BOTH);
            }
            case BOTH -> {
                if (elapsed() < 10) return false;
                require(near(chest.lidOpenness(0), 1F), "Remaining viewer did not keep the lid fully open");
                expectCues(a, OPEN);
                expectCues(b, OPEN);
                expectCues(late);
                require(late.wire.chunkSnapshots.stream().anyMatch(t -> t.getBoolean("LidOpen")
                                && t.getLong("LidStartTick") == tag(chest).getLong("LidStartTick") && near(t.getFloat("LidStart"), 0)),
                        "Late chunk tracker did not receive the original in-flight opening time");
                require(a.wire.updates.stream().anyMatch(t -> t.getBoolean("LidOpen"))
                                && b.wire.updates.stream().anyMatch(t -> t.getBoolean("LidOpen")),
                        "Tracking clients did not receive authoritative lid update tags");
                b.player.closeContainer();
                require(!tag(chest).getBoolean("LidOpen") && near(chest.lidOpenness(0), 1F),
                        "Last viewer's departure did not start closing");
                expectCues(a, OPEN, CLOSE);
                expectCues(b, OPEN, CLOSE);
                expectCues(late, CLOSE);
                next(Step.LAST);
            }
            case LAST -> {
                if (elapsed() < 3) return false;
                require(near(chest.lidOpenness(0), .7F), "Closing did not reach 70% after three ticks");
                require(a.wire.lidEvents.equals(List.of(1, 0)) && b.wire.lidEvents.equals(List.of(1, 0)),
                        "Two concurrent viewers emitted duplicate or out-of-order lid events");
                require(a.wire.updates.stream().anyMatch(t -> !t.getBoolean("LidOpen") && near(t.getFloat("LidStart"), 1F)),
                        "Existing trackers missed authoritative close motion");
                NativeRewardChestMenu reopened = open(a, CHEST);
                require(reopened.getSlot(claimedSlot).getItem().isEmpty() && snapshot(a).revision() == claimedRevision,
                        "Reopening duplicated a claimed reward");
                require(near(chest.lidOpenness(0), .7F) && near(chest.lidOpenness(.5F), .75F),
                        "Fast reopening jumped instead of reversing from the current lid angle");
                expectCues(a, OPEN, CLOSE, OPEN);
                expectCues(b, OPEN, CLOSE, OPEN);
                expectCues(late, CLOSE, OPEN);
                next(Step.REVERSE);
            }
            case REVERSE -> {
                if (elapsed() < 4) return false;
                require(near(chest.lidOpenness(0), 1F), "Reversed motion did not finish opening");
                move(a, CHEST.north(9));
                next(Step.OUT_OF_RANGE);
            }
            case OUT_OF_RANGE -> {
                if (elapsed() < 6) return false;
                require(!tag(chest).getBoolean("LidOpen") && a.player.containerMenu == a.player.inventoryMenu,
                        "A viewer leaving range kept the chest open");
                expectCues(b, OPEN, CLOSE, OPEN, CLOSE);
                expectCues(late, CLOSE, OPEN, CLOSE);
                open(b, CHEST);
                expectCues(b, OPEN, CLOSE, OPEN, CLOSE, OPEN);
                expectCues(late, CLOSE, OPEN, CLOSE, OPEN);
                next(Step.REOPENED);
            }
            case REOPENED -> {
                if (elapsed() < 2) return false;
                b.player.disconnect();
                require(b.player.hasDisconnected(), "Fixture did not enter the normal disconnected player state");
                next(Step.DISCONNECT);
            }
            case DISCONNECT -> {
                if (elapsed() < 6) return false;
                require(!tag(chest).getBoolean("LidOpen"), "Ticking chest did not prune the disconnected viewer");
                expectCues(late, CLOSE, OPEN, CLOSE, OPEN, CLOSE);
                next(Step.CLOSED);
            }
            case CLOSED -> {
                if (elapsed() < 10) return false;
                require(near(chest.lidOpenness(0), 0F), "Chest did not finish closed after cleanup");
                expectCues(late, CLOSE, OPEN, CLOSE, OPEN, CLOSE);
                for (Actor actor : List.copyOf(actors)) remove(actor);
                System.out.println("DUSK CHEST SERVER PASS real authorized menus, one/two viewers, personal claim, "
                        + "single transition cues, late chunk snapshot, reversal, out-of-range and disconnected cleanup");
                return true;
            }
        }
        return false;
    }

    private NativeRewardSite place(UUID namespace, BlockPos where, String blockId, String reward, BlockPos bell)
            throws ReflectiveOperationException {
        Block block = BuiltInRegistries.BLOCK.get(id(blockId));
        require(block != Blocks.AIR, "Missing registered test chest " + blockId);
        level.setBlock(where, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH),
                Block.UPDATE_CLIENTS);
        NativeRewardChestBlockEntity entity = chest(where);
        NativeRewardSite placed = new NativeRewardSite(namespace, level.dimension().location(), where.below(2),
                id(reward), where);
        Class<?> assignmentType = Class.forName("dev.uwu.dimension.NativeRewardAssignment");
        var constructor = assignmentType.getDeclaredConstructor(NativeRewardSite.class, BlockPos.class);
        constructor.setAccessible(true);
        Object assignment = constructor.newInstance(placed, bell);
        var stage = NativeRewardChestBlockEntity.class.getDeclaredMethod("stage", assignmentType);
        stage.setAccessible(true);
        stage.invoke(entity, assignment);
        Class<?> registryType = Class.forName("dev.uwu.dimension.NativeRewardRegistry");
        var acquire = registryType.getDeclaredMethod("forNewChest", ServerLevel.class, UUID.class);
        acquire.setAccessible(true);
        Object registry = acquire.invoke(null, level, namespace);
        var assign = registryType.getDeclaredMethod("assign", assignmentType);
        assign.setAccessible(true);
        require(registry != null && Boolean.TRUE.equals(assign.invoke(registry, assignment)),
                "Could not seed the disposable world's authoritative reward assignment");
        return placed;
    }

    private void claim(Actor owner, Actor other) {
        NativeRewardChestMenu menu = menu(owner);
        var ownerBefore = snapshot(owner);
        var otherBefore = snapshot(other);
        List<ItemStack> slots = ownerBefore.slots();
        claimedSlot = -1;
        for (int index = 0; index < slots.size(); index++) if (!slots.get(index).isEmpty()) {
            claimedSlot = index;
            break;
        }
        require(claimedSlot >= 0, "Tide Archive did not roll a reward");
        ItemStack prize = slots.get(claimedSlot);
        int before = inventoryCount(owner, prize);
        menu.clicked(claimedSlot, 0, ClickType.PICKUP, owner.player);
        require(menu.getSlot(claimedSlot).getItem().isEmpty()
                        && inventoryCount(owner, prize) - before == prize.getCount(), "An actual claim failed");
        claimedRevision = snapshot(owner).revision();
        menu.clicked(claimedSlot, 0, ClickType.PICKUP, owner.player);
        require(claimedRevision == ownerBefore.revision() + 1 && snapshot(owner).revision() == claimedRevision
                        && inventoryCount(owner, prize) - before == prize.getCount(), "Repeated click paid twice");
        var otherAfter = snapshot(other);
        require(otherBefore.revision() == otherAfter.revision() && inventoryCount(other, prize) == 0,
                "First viewer claimed or changed the second viewer's loot");
        List<ItemStack> unclaimed = otherAfter.slots();
        List<ItemStack> expected = otherBefore.slots();
        for (int index = 0; index < expected.size(); index++) require(ItemStack.matches(unclaimed.get(index), expected.get(index)),
                "The second viewer's personal slot changed: " + index);
    }

    private PersonalLootStore.Snapshot snapshot(Actor actor) {
        var player = actor.player;
        return PersonalLootStore.readExisting(player.getPersistentData(), player.getUUID(), site, player.registryAccess());
    }

    private static int inventoryCount(Actor actor, ItemStack prize) {
        int count = 0;
        var inventory = actor.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, prize)) count += stack.getCount();
        }
        return count;
    }

    private NativeRewardChestBlockEntity chest(BlockPos where) {
        var entity = level.getBlockEntity(where);
        require(entity instanceof NativeRewardChestBlockEntity, "Missing native chest entity at " + where);
        return (NativeRewardChestBlockEntity) entity;
    }

    private CompoundTag tag(NativeRewardChestBlockEntity entity) {
        return entity.getUpdateTag(level.registryAccess());
    }

    private void interact(Actor actor, BlockPos where) {
        level.getBlockState(where).useWithoutItem(level, actor.player,
                new BlockHitResult(Vec3.atCenterOf(where), Direction.UP, where, false));
    }

    private NativeRewardChestMenu open(Actor actor, BlockPos where) {
        interact(actor, where);
        return menu(actor);
    }

    private static NativeRewardChestMenu menu(Actor actor) {
        require(actor.player.containerMenu instanceof NativeRewardChestMenu,
                "Actual native reward menu did not open for " + actor.player.getName().getString());
        return (NativeRewardChestMenu) actor.player.containerMenu;
    }

    private Actor spawn(String name, BlockPos feet) throws ReflectiveOperationException {
        MinecraftServer server = level.getServer();
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name),
                ClientInformation.createDefault());
        RecordingListener listener = new RecordingListener(server, player);
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        var actor = new Actor(player, listener);
        move(actor, feet);
        onlinePlayers(server).add(player);
        level.addNewPlayer(player);
        require(level.getEntity(player.getId()) == player, "Fixture player was not tracked: " + name);
        // These test players skip login/configuration. Queue the exact loaded chest chunk
        // so the native chunk sender supplies the real block-entity snapshot.
        var chunk = level.getChunkSource().getChunkNow(CHEST_CHUNK.x, CHEST_CHUNK.z);
        require(chunk != null, "Chest chunk is unavailable to fixture viewer " + name);
        if (!player.getChunkTrackingView().contains(CHEST_CHUNK)) {
            player.setChunkTrackingView(ChunkTrackingView.of(CHEST_CHUNK, 2));
        }
        listener.chunkSender.markChunkPendingToSend(chunk);
        actors.add(actor);
        return actor;
    }

    private void remove(Actor actor) throws ReflectiveOperationException {
        onlinePlayers(level.getServer()).remove(actor.player);
        level.removePlayerImmediately(actor.player, Entity.RemovalReason.DISCARDED);
        actors.remove(actor);
        actor.wire.transport.finishAndReleaseAll();
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> onlinePlayers(MinecraftServer server) throws ReflectiveOperationException {
        // The probe has no remote clients. Join the real broadcast list with fixture listeners only.
        var players = PlayerList.class.getDeclaredField("players");
        players.setAccessible(true);
        return (List<ServerPlayer>) players.get(server.getPlayerList());
    }

    private static void move(Actor actor, BlockPos feet) {
        actor.player.setPos(feet.getX() + .5, feet.getY(), feet.getZ() + .5);
    }

    private void next(Step phase) {
        step = phase;
        entered = level.getGameTime();
    }

    private long elapsed() {
        return level.getGameTime() - entered;
    }

    private static void expectCues(Actor actor, ResourceLocation... expected) {
        require(actor.wire.cues.equals(List.of(expected)), "Unexpected sound sequence: " + actor.describe());
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < .001F;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension", path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Actor(ServerPlayer player, RecordingListener wire) {
        void receiveChunks() {
            int previous = wire.batches;
            wire.chunkSender.sendNextChunks(player);
            if (wire.batches > previous) wire.chunkSender.onChunkBatchReceivedByClient(16F);
        }

        String describe() {
            return player.getName().getString() + ": sound=" + wire.cues + ", lidEvents=" + wire.lidEvents
                    + ", snapshots=" + wire.chunkSnapshots + ", updates=" + wire.updates
                    + ", batches=" + wire.batches + ", queued=" + wire.chunkSender.isPending(CHEST_CHUNK.toLong());
        }
    }

    private static final class RecordingListener extends ServerGamePacketListenerImpl {
        final List<ResourceLocation> cues = new ArrayList<>();
        final List<Integer> lidEvents = new ArrayList<>();
        final List<CompoundTag> updates = new ArrayList<>();
        final List<CompoundTag> chunkSnapshots = new ArrayList<>();
        final EmbeddedChannel transport;
        int batches;

        RecordingListener(MinecraftServer server, ServerPlayer player) {
            this(server, player, new Connection(PacketFlow.SERVERBOUND));
        }

        private RecordingListener(MinecraftServer server, ServerPlayer player, Connection connection) {
            super(server, connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
            // Run Connection.channelActive so NeoForge's per-channel protocol attributes are available.
            // The recorder still handles outbound packets without a socket or a Minecraft login.
            transport = new EmbeddedChannel(connection);
            require(connection.channel() == transport, "Fixture connection did not attach to its memory channel");
        }

        @Override public void send(Packet<?> packet, PacketSendListener listener) {
            if (packet instanceof ClientboundChunkBatchFinishedPacket) {
                ++batches;
            } else if (packet instanceof ClientboundSoundPacket sound) {
                var key = sound.getSound().value().getLocation();
                if (key.equals(OPEN) || key.equals(CLOSE)) {
                    require(sound.getSource() == SoundSource.BLOCKS && near(sound.getVolume(), .5F)
                                    && near(sound.getPitch(), 1F),
                            "Dusk chest cue uses the wrong volume, pitch or sound channel");
                    cues.add(key);
                }
            } else if (packet instanceof ClientboundBlockEventPacket event && CHEST.equals(event.getPos())
                    && event.getB0() == 1) {
                lidEvents.add(event.getB1());
            } else if (packet instanceof ClientboundBlockEntityDataPacket change && CHEST.equals(change.getPos())) {
                captureTag(change.getTag(), updates);
            } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk
                    && CHEST_CHUNK.x == chunk.getX() && CHEST_CHUNK.z == chunk.getZ()) {
                chunk.getChunkData().getBlockEntitiesTagsConsumer(chunk.getX(), chunk.getZ()).accept(
                        (pos, type, tag) -> { if (CHEST.equals(pos)) captureTag(tag, chunkSnapshots); });
            }
        }

        private static void captureTag(CompoundTag tag, List<CompoundTag> out) {
            require(tag != null && tag.getAllKeys().equals(LID_FIELDS), "Lid packet omitted motion or exposed reward data: " + tag);
            out.add(tag.copy());
        }
    }
}
