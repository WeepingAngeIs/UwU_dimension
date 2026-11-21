package dev.uwu.lanterncheck;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.ReefLife;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Verifies the native interaction on untouched, newly generated shelves in both village variants. */
public final class LanternReadingHouseProbe {
    private static final ResourceLocation DISPLAY = id("lantern_storybook");
    private static final ResourceLocation OPEN = id("open_lantern_storybook");
    private static final int TICKET_RADIUS = 2;
    private final Set<ChunkPos> freshChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> readingChunks = new HashSet<>();
    private final List<Shelf> shelves = new ArrayList<>();
    private long requestedAt = -1;

    public LanternReadingHouseProbe() {
        NeoForge.EVENT_BUS.addListener(this::chunkLoaded);
    }

    private void chunkLoaded(ChunkEvent.Load event) {
        if (event.isNewChunk() && event.getLevel() instanceof ServerLevel world
                && world.dimension().equals(ReefLife.REACH)) {
            freshChunks.add(event.getChunk().getPos());
        }
    }

    public boolean tick(ServerLevel level, List<BlossomVillage.Plan> plans) throws ReflectiveOperationException {
        if (requestedAt < 0) {
            for (var plan : plans) findShelves(plan);
            require(shelves.stream().anyMatch(Shelf::dusk) && shelves.stream().anyMatch(shelf -> !shelf.dusk()),
                    "The surveyed villages did not each have a style-zero wall-bed reading house");
            requestedAt = level.getGameTime();
            for (ChunkPos chunk : readingChunks) {
                level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, TICKET_RADIUS, chunk, true);
            }
            return false;
        }
        for (ChunkPos chunk : readingChunks) {
            if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
                require(level.getGameTime() - requestedAt < 1200, "Timed out waiting for reading house chunk " + chunk);
                return false;
            }
            require(freshChunks.contains(chunk), "Reading house was not witnessed generating: " + chunk);
        }

        WrittenBookContent story = originalStory(level);
        var reader = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "LanternReader"),
                ClientInformation.createDefault());
        var wire = new ReaderConnection(level, reader, new Connection(PacketFlow.SERVERBOUND));
        try {
            for (Shelf shelf : shelves) checkReading(level, reader, wire, shelf, story);
        } finally {
            wire.transport.finishAndReleaseAll();
        }
        for (ChunkPos chunk : readingChunks) {
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, TICKET_RADIUS, chunk, true);
        }
        System.out.println("LANTERN READING HOUSES PASS Petal and Dusk; generated shelves=" + shelves.size()
                + ", chunks=" + readingChunks.size() + ", unchanged pages=" + story.pages().size());
        return true;
    }

    private void findShelves(BlossomVillage.Plan plan) {
        Direction readerSide = Direction.EAST;
        for (int turn = 0; turn < plan.site().turns(); turn++) readerSide = readerSide.getClockWise();
        for (var house : plan.houses()) {
            if (house.style() != 0 || !house.wallBeds()) continue;
            BlockPos pos = plan.site().pos(house.x() - house.width() + 1, house.floor() + 2,
                    house.z() + house.depth() - 1);
            shelves.add(new Shelf(pos, plan.dusk(), readerSide));
            readingChunks.add(new ChunkPos(pos));
        }
    }

    private static WrittenBookContent originalStory(ServerLevel level) throws ReflectiveOperationException {
        var method = Class.forName("dev.uwu.dimension.LanternBookStacks")
                .getDeclaredMethod("create", HolderLookup.Provider.class);
        method.setAccessible(true);
        ItemStack original = (ItemStack) method.invoke(null, level.registryAccess());
        WrittenBookContent story = original.get(DataComponents.WRITTEN_BOOK_CONTENT);
        require(story != null && story.title().raw().equals("A Lantern for Both Shores")
                        && story.author().equals("The Petal Cartographers") && story.pages().size() == 53,
                "The bundled story title, author or chapters changed");
        return story;
    }

    private static void checkReading(ServerLevel level, ServerPlayer reader, ReaderConnection wire, Shelf shelf,
                                     WrittenBookContent original) throws ReflectiveOperationException {
        BlockPos pos = shelf.pos();
        var state = level.getBlockState(pos);
        var edition = shelf.dusk() ? "dusk" : "petal";
        require(BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(DISPLAY)
                        && state.toString().contains("edition=" + edition)
                        && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == shelf.readerSide()
                        && level.getBlockState(pos.below()).is(Blocks.BOOKSHELF)
                        && level.getBlockEntity(pos) != null,
                "Missing generated " + edition + " reading display at " + pos + ": " + state);
        require(level.getBlockEntity(pos).getType() == BuiltInRegistries.BLOCK_ENTITY_TYPE.get(DISPLAY),
                "Reading shelf has no native storybook block entity at " + pos);

        Vec3 feet = Vec3.atBottomCenterOf(pos.relative(shelf.readerSide())).add(0, -1, 0);
        reader.setPos(feet.x, feet.y, feet.z);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), shelf.readerSide(), pos, false);
        wire.story = null;
        var result = reader.gameMode.useItemOn(reader, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        require(result.consumesAction() && wire.story != null, "Vanilla interaction did not open " + edition + " shelf");
        var accessor = wire.story.getClass().getDeclaredMethod("content");
        accessor.setAccessible(true);
        require(original.equals(accessor.invoke(wire.story)), "Shelf opened altered written content in " + edition);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension", path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Shelf(BlockPos pos, boolean dusk, Direction readerSide) {}

    private static final class ReaderConnection extends ServerGamePacketListenerImpl {
        private final EmbeddedChannel transport;
        private CustomPacketPayload story;

        ReaderConnection(ServerLevel level, ServerPlayer player, Connection connection) {
            super(level.getServer(), connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
            transport = new EmbeddedChannel(connection);
        }

        @Override public void send(Packet<?> packet, PacketSendListener listener) {
            if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload().type().id().equals(OPEN)) {
                story = custom.payload();
            }
        }
    }
}
