package dev.uwu.test;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.People;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import noobanidus.mods.lootr.common.api.LootrAPI;
import noobanidus.mods.lootr.common.block.entity.LootrChestBlockEntity;
import noobanidus.mods.lootr.common.data.LootrSavedData;

public final class ChestProof {
    // Test-only connected server profiles with in-memory transports. No client process or account is used.
    static final class Visitor implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        Visitor(ServerLevel level, BlockPos near, String name, UUID id) {
            var profile = new GameProfile(id, name);
            player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
            var wire = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(wire);
            new ServerGamePacketListenerImpl(level.getServer(), wire, player, CommonListenerCookie.createInitial(profile, false)) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet, PacketSendListener callback) {}
            };
            require(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "Test profile must be in survival");
            player.setPos(near.getX() + 0.5, near.getY(), near.getZ() + 1.5);
        }
        @Override public void close() {
            player.closeContainer();
            channel.finishAndReleaseAll();
        }
    }

    public static void run(ServerLevel level, BlockPos generatedChest) throws Exception {
        require(level.getBlockEntity(generatedChest) instanceof LootrChestBlockEntity, "Generated block entity is not a Lootr chest");
        var chest = (LootrChestBlockEntity)level.getBlockEntity(generatedChest);
        require(chest.getInfoLootTable() != null && chest.getInfoLootTable().location().getNamespace().equals("uwudimension"),
                "Generated chest has no dimension loot table");
        require(!chest.hasBeenOpened(), "Fresh structure chest has already been opened");
        try (var a = new Visitor(level, generatedChest, "Reed", UUID.fromString("04786b9d-dfef-4c58-a786-7ff7847dc01a"));
             var b = new Visitor(level, generatedChest, "Vale", UUID.fromString("5719caa1-792e-4ee1-86ad-aca6ec27de26"))) {
            var data = (LootrSavedData)LootrAPI.getData(chest);
            require(data.getInventory(a.player.getUUID()) == null && data.getInventory(b.player.getUUID()) == null,
                    "Generated chest already had either player's inventory");
            var first = open(level, generatedChest, a.player);
            var firstRoll = contents(first);
            require(firstRelic(first)>=0, "Player A received no chest supplies");
            int taken = firstRelic(first);
            a.player.containerMenu.clicked(taken, 0, ClickType.PICKUP, a.player);
            require(first.getItem(taken).isEmpty(), "Player A failed to take reward");
            var afterTake = contents(first);
            a.player.closeContainer();
            require(data.getInventory(b.player.getUUID()) == null, "Player A's visit rolled B's loot early");
            var second = open(level, generatedChest, b.player);
            require(firstRelic(second)>=0, "Player B received no chest supplies");
            var secondRoll = contents(second);
            int marked = firstRelic(second);
            b.player.containerMenu.clicked(marked, 0, ClickType.PICKUP, b.player);
            require(second.getItem(marked).isEmpty(), "Player B failed to take reward");
            // Leave a distinctive item for B and verify A's contents are untouched.
            second.setItem(marked, new ItemStack(Items.POTATO, 13));
            second.setChanged();
            var afterMarker = contents(second);
            b.player.closeContainer();
            require(contents(open(level, generatedChest, a.player)).equals(afterTake), "A's reopened inventory changed");
            a.player.closeContainer();
            require(contents(open(level, generatedChest, b.player)).equals(afterMarker), "B's reopened inventory changed");
            b.player.closeContainer();
            require(chest.getActualOpeners().containsAll(List.of(a.player.getUUID(), b.player.getUUID())), "Missing physical opener records");

            // Exercise the actual Lootr save and load methods and Minecraft's compressed file format.
            Path saved = Files.createTempDirectory("heartwater-lootr").resolve("chest.dat");
            data.setDirty();
            data.save(saved.toFile(), level.registryAccess());
            net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
            var persisted = NbtIo.readCompressed(saved, NbtAccounter.unlimitedHeap()).getCompound("data");
            var reloaded = LootrSavedData.load(persisted, level.registryAccess());
            require(contents(reloaded.getInventory(a.player.getUUID())).equals(afterTake), "A changed across disk round trip");
            require(contents(reloaded.getInventory(b.player.getUUID())).equals(afterMarker), "B changed across disk round trip");
            // A test copy of SavedData was written. Allow ordinary world saving to run too.
            data.setDirty();
            System.out.println("LOOTR TWO-PLAYER PASS at " + generatedChest + " table=" + chest.getInfoLootTable().location()
                    + " serverProfileA=" + a.player.getUUID() + " initial=" + firstRoll + " serverProfileB="
                    + b.player.getUUID() + " initial=" + secondRoll + " independent edits, reopen and disk reload verified");
            factions(level, a.player, b.player);
        }
    }

    static Container open(ServerLevel level, BlockPos pos, ServerPlayer visitor) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        level.getBlockState(pos).useWithoutItem(level, visitor, hit);
        require(visitor.containerMenu instanceof ChestMenu, "Lootr did not open a chest menu for " + visitor.getName().getString());
        return ((ChestMenu)visitor.containerMenu).getContainer();
    }

    private static int firstRelic(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (!container.getItem(slot).isEmpty()) return slot;
        }
        return -1;
    }
    private static boolean hasRelic(Container container) { return firstRelic(container) >= 0; }

    private static List<String> contents(Container container) {
        require(container != null, "Missing stored inventory");
        var items = new ArrayList<String>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            var stack = container.getItem(i);
            if (!stack.isEmpty()) items.add(i + "=" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + "*" + stack.getCount());
        }
        return items;
    }

    private static void factions(ServerLevel level, ServerPlayer a, ServerPlayer b) {
        Keeper dusk = People.DUSK_KEEPER.get().create(level);
        Keeper petal = People.PETAL_KEEPER.get().create(level);
        require(dusk != null && petal != null, "Unable to create keepers");
        dusk.setPos(a.position());
        petal.setPos(a.position());
        // The preceding randomized chest may put an emerald in the selected hotbar slot.
        // Begin the hostility probe with empty hands; visible money intentionally creates a truce.
        a.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        a.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        dusk.setTarget(a);
        require(dusk.getTarget() == a, "Dusk Keeper did not accept hostile target");
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 4));
        dusk.mobInteract(a, InteractionHand.MAIN_HAND);
        require(dusk.getTarget() == null && a.containerMenu instanceof MerchantMenu, "Emerald failed to calm trader and open shop");
        MerchantMenu trade = (MerchantMenu)a.containerMenu;
        require(dusk.getOffers().size() == 4, "Unexpected trade lineup");
        trade.getSlot(0).set(new ItemStack(Items.EMERALD));
        trade.slotsChanged(trade.getSlot(0).container);
        require(trade.getSlot(2).getItem().is(Items.BLACK_CANDLE), "No first offer result");
        trade.clicked(2, 0, ClickType.PICKUP, a);
        require(trade.getCarried().is(Items.BLACK_CANDLE) && trade.getSlot(0).getItem().isEmpty(), "Emerald purchase failed");
        a.closeContainer();
        a.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        dusk.setTarget(a);
        require(dusk.getTarget() == null, "Dusk Keeper attacked immediately after trade");
        a.setShiftKeyDown(true);
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 4));
        dusk.mobInteract(a, InteractionHand.MAIN_HAND);
        require(a.getMainHandItem().isEmpty(), "Recruitment did not charge four emeralds");
        dusk.mobInteract(a, InteractionHand.MAIN_HAND);
        CompoundTag keeper = new CompoundTag();
        dusk.addAdditionalSaveData(keeper);
        require(keeper.hasUUID("Companion") && keeper.getUUID("Companion").equals(a.getUUID()) && keeper.getBoolean("Settled"),
                "Recruit and settle did not persist the owner's UUID and home");
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 4));
        petal.mobInteract(a, InteractionHand.MAIN_HAND);
        require(a.getMainHandItem().getCount() == 4, "Opposite faction accepted allegiance");
        petal.setTarget(b);
        require(petal.getTarget() == null, "Friendly faction targeted visitor");
        b.setShiftKeyDown(true);
        b.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 4));
        petal.mobInteract(b, InteractionHand.MAIN_HAND);
        require(b.getMainHandItem().isEmpty(), "Second player could not choose Petal faction");
        System.out.println("KEEPER FACTION PASS: hostility, emerald truce, four-offer merchant, one-emerald candle purchase, grace after shop close, opposing oaths, recruitment and settled-owner save");
    }

    private static void require(boolean check, String message) {
        if (!check) throw new IllegalStateException(message);
    }
}
