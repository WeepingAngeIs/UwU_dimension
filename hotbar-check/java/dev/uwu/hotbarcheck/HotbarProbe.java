package dev.uwu.hotbarcheck;

import dev.uwu.dimension.RelicFlight;
import dev.uwu.dimension.RevivalBlossoms;
import dev.uwu.dimension.SharedAbilityHotbar;
import dev.uwu.hotbarcheck.mixin.MouseCallbacks;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.lwjgl.glfw.GLFW;

/** Drives the normal input callbacks and watches actual integrated-server gameplay and HUD rendering. */
@Mod("uwu_hotbar_check")
public final class HotbarProbe {
    private enum Stage {
        LOAD, EQUIP, RAPID, HIGH_SLOT, BACK, SCROLLED, DROP_KEY, DROP,
        REEQUIP, SELECT_THROW, EMPTY, TURN, THROW_KEY, FLIGHT, DONE
    }

    private static final UUID DIRK = UUID.fromString("206e7d9c-e43b-4568-990a-6f789760f17e");
    private static volatile Stage stage = Stage.LOAD;
    private static boolean opening, observedEmptyHand;
    private static int began, highSlot = 4, iconFrames;
    private static int beforeSlot, beforeIcons, frameCells;
    private static ItemStack[] beforeFrame;
    private static volatile Throwable serverFailure;
    private static volatile ItemEntity dropped;
    private static volatile int turns, launches;

    public HotbarProbe() {
        NeoForge.EVENT_BUS.addListener(HotbarProbe::tick);
        NeoForge.EVENT_BUS.addListener(HotbarProbe::beforeFrame);
        NeoForge.EVENT_BUS.addListener(HotbarProbe::afterFrame);
        NeoForge.EVENT_BUS.addListener(HotbarProbe::joined);
    }

    private static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (!opening && client.screen instanceof TitleScreen title) {
            opening = true;
            client.createWorldOpenFlows().createFreshLevel("HotbarCheck",
                    new LevelSettings("Shared hotbar check", GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(152045L, false, false), WorldPresets::createNormalWorldDimensions, title);
        } else if (opening && client.player == null && client.screen != null) {
            for (var widget : List.copyOf(client.screen.children())) {
                if (widget instanceof Button button && (button.getMessage().getString().equals("Proceed")
                        || button.getMessage().getString().equals("I know what I'm doing!"))) {
                    button.onPress();
                    break;
                }
            }
        }
        if (stage == Stage.DONE) {
            client.stop();
            return;
        }
        var player = client.player;
        if (player == null || client.screen != null) return;
        try {
            if (serverFailure != null) throw new AssertionError("Fixture server action failed", serverFailure);
            if (stage != Stage.LOAD) {
                require(player.tickCount - began < (stage == Stage.FLIGHT ? 280 : 120),
                        "Timed out in " + stage + ", slot=" + player.getInventory().selected
                                + ", icon frames=" + iconFrames + ", casts=" + launches + ", primes=" + turns);
            }
            Inventory inventory = player.getInventory();
            switch (stage) {
                case LOAD -> {
                    if (player.tickCount < 45) return;
                    client.setWindowActive(true);
                    serverAction(client, actor -> {
                        actor.getInventory().selected = 0;
                        actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, newDirk());
                        for (int slot = 1; slot <= 8; slot++) {
                            actor.getInventory().setItem(slot, new ItemStack(Items.STICK, slot));
                        }
                        actor.containerMenu.broadcastChanges();
                    });
                    enter(Stage.EQUIP, player.tickCount);
                }
                case EQUIP -> {
                    if (!hasDirk(player.getMainHandItem()) || iconFrames < 2) return;
                    require(SharedAbilityHotbar.visibleFor(player), "Held Dirk has no ability row");
                    requireCell(0);
                    for (int repeat = 0; repeat < 4; repeat++) {
                        for (int slot = 0; slot < 4; slot++) number(client, slot);
                    }
                    enter(Stage.RAPID, player.tickCount);
                }
                case RAPID -> {
                    require(inventory.selected == 0 && hasDirk(player.getMainHandItem()),
                            "An ability key switched the real held inventory slot");
                    if (player.tickCount - began < 6) return;
                    number(client, highSlot);
                    enter(Stage.HIGH_SLOT, player.tickCount);
                }
                case HIGH_SLOT -> {
                    require(inventory.selected == highSlot && inventory.getSelected().getCount() == highSlot,
                            "Vanilla number key " + (highSlot + 1) + " did not select its own slot");
                    require(!SharedAbilityHotbar.visibleFor(player), "Ability row stayed over ordinary item");
                    number(client, 0);
                    enter(Stage.BACK, player.tickCount);
                }
                case BACK -> {
                    require(inventory.selected == 0 && hasDirk(player.getMainHandItem()),
                            "Vanilla key 1 stayed reserved after leaving the ability weapon");
                    if (++highSlot <= 8) {
                        number(client, highSlot);
                        enter(Stage.HIGH_SLOT, player.tickCount);
                        return;
                    }
                    mouse(client).hotbarCheck$scroll(window(client), 0, 1);
                    enter(Stage.SCROLLED, player.tickCount);
                }
                case SCROLLED -> {
                    require(inventory.selected == 8 && !SharedAbilityHotbar.visibleFor(player),
                            "Vanilla scroll did not leave the first slot while the ability row was open");
                    number(client, 0);
                    enter(Stage.DROP_KEY, player.tickCount);
                }
                case DROP_KEY -> {
                    require(hasDirk(player.getMainHandItem()) && SharedAbilityHotbar.visibleFor(player),
                            "Test Dirk missing before Q");
                    key(client, GLFW.GLFW_KEY_Q);
                    enter(Stage.DROP, player.tickCount);
                }
                case DROP -> {
                    if (dropped == null || !player.getMainHandItem().isEmpty()) return;
                    require(!SharedAbilityHotbar.visibleFor(player), "Dropping Dirk left an active ability row");
                    System.out.println("HOTBAR INPUT PASS rapid 1–4, each of 5–9, normal wheel, vanilla Q drop");
                    serverAction(client, actor -> {
                        dropped.discard();
                        actor.getInventory().setItem(0, newDirk());
                        actor.containerMenu.broadcastChanges();
                    });
                    enter(Stage.REEQUIP, player.tickCount);
                }
                case REEQUIP -> {
                    if (!hasDirk(player.getMainHandItem())) return;
                    require(inventory.selected == 0, "Server returned fixture to unexpected slot");
                    requireCell(0);
                    number(client, 1);
                    enter(Stage.SELECT_THROW, player.tickCount);
                }
                case SELECT_THROW -> {
                    require(inventory.selected == 0, "Throw selection changed the real hotbar slot");
                    requireCell(1);
                    number(client, 3);
                    enter(Stage.EMPTY, player.tickCount);
                }
                case EMPTY -> {
                    require(inventory.selected == 0, "Empty ability cell selected inventory slot");
                    requireCell(0);
                    require(turns == 0 && launches == 0, "Ability key alone invoked a server ability");
                    enter(Stage.TURN, player.tickCount);
                    rightClick(client);
                }
                case TURN -> {
                    if (turns == 0) return;
                    require(turns == 1 && launches == 0 && hasDirk(player.getMainHandItem()),
                            "Right-click after empty cell fired anything other than Turn");
                    // The next legitimate click should not be throttled by the vanilla right-click delay.
                    if (player.tickCount - began < 5) return;
                    number(client, 1);
                    enter(Stage.THROW_KEY, player.tickCount);
                }
                case THROW_KEY -> {
                    requireCell(1);
                    require(inventory.selected == 0 && launches == 0, "Selecting Throw used it immediately");
                    enter(Stage.FLIGHT, player.tickCount);
                    rightClick(client);
                }
                case FLIGHT -> {
                    if (player.getMainHandItem().isEmpty()) {
                        observedEmptyHand = true;
                        require(!SharedAbilityHotbar.visibleFor(player), "Held-weapon UI remained during flight");
                    }
                    if (launches == 0 || !observedEmptyHand || !hasDirk(player.getMainHandItem())) return;
                    require(launches == 1 && turns == 1 && inventory.selected == 0 && countDirks(inventory) == 1,
                            "Thrown Dirk did not return once and only once to its own held slot");
                    requireCell(0);
                    require(iconFrames >= 4, "Missing live held-item icon observations");
                    System.out.println("HOTBAR DISPLAY PASS current held item, unchanged inventory through HUD frames=" + iconFrames);
                    System.out.println("DIRK THROW PASS empty cell restored Turn, real right-click launched one Dirk,"
                            + " held slot became empty and the exact Dirk returned to it");
                    System.out.println("HOTBAR CLIENT PASS");
                    stage = Stage.DONE;
                }
                case DONE -> throw new AssertionError("Unexpected stage " + stage);
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void beforeFrame(RenderFrameEvent.Pre event) {
        var player = Minecraft.getInstance().player;
        beforeFrame = null;
        frameCells = 0;
        if (player == null || stage == Stage.LOAD || stage == Stage.DONE || !hasDirk(player.getMainHandItem())) return;
        Inventory inventory = player.getInventory();
        beforeSlot = inventory.selected;
        beforeIcons = iconFrames;
        beforeFrame = new ItemStack[inventory.getContainerSize()];
        for (int slot = 0; slot < beforeFrame.length; slot++) beforeFrame[slot] = inventory.getItem(slot).copy();
    }

    private static void afterFrame(RenderFrameEvent.Post event) {
        var player = Minecraft.getInstance().player;
        if (player == null || beforeFrame == null || iconFrames == beforeIcons || stage == Stage.DONE) return;
        try {
            Inventory inventory = player.getInventory();
            require(frameCells == 0b1111, "HUD did not replace exactly the first four hotbar cells: " + frameCells);
            require(beforeSlot == inventory.selected, "HUD changed selected inventory slot");
            for (int slot = 0; slot < beforeFrame.length; slot++) {
                require(ItemStack.matches(beforeFrame[slot], inventory.getItem(slot)), "HUD mutated inventory slot " + slot);
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    public static void drawnIcon(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        if (player == null || stage == Stage.LOAD || stage == Stage.DONE || !hasDirk(player.getMainHandItem())) return;
        if (!ItemStack.matches(stack, player.getMainHandItem())) {
            fail(new AssertionError("HUD substituted a stack for the actual held Dirk"));
        } else {
            iconFrames++;
        }
    }

    public static void drawnCell(int index) {
        if (beforeFrame != null) frameCells |= 1 << index;
    }

    public static void armedTurn() {
        if (stage == Stage.TURN) turns++;
    }

    private static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (stage == Stage.DROP && event.getEntity() instanceof ItemEntity item && hasDirk(item.getItem())) {
            dropped = item;
        }
        if (stage == Stage.FLIGHT && event.getEntity() instanceof RelicFlight flight
                && flight.physical() && hasDirk(flight.getItem())) {
            launches++;
        }
    }

    private static ItemStack newDirk() {
        var stack = new ItemStack(RevivalBlossoms.DIRK.get());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID("uwu_revival_id", DIRK));
        return stack;
    }

    private static boolean hasDirk(ItemStack stack) {
        if (!stack.is(RevivalBlossoms.DIRK.get())) return false;
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID("uwu_revival_id") && DIRK.equals(tag.getUUID("uwu_revival_id"));
    }

    private static int countDirks(Inventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (hasDirk(inventory.getItem(slot))) count += inventory.getItem(slot).getCount();
        }
        return count;
    }

    private static void requireCell(int expected) {
        int actual = SharedAbilityHotbar.selectedCell();
        require(actual == expected, "Expected ability cell " + expected + " but got " + actual);
    }

    private static void number(Minecraft client, int slot) {
        key(client, GLFW.GLFW_KEY_1 + slot);
    }

    private static void key(Minecraft client, int key) {
        client.keyboardHandler.keyPress(window(client), key, 0, GLFW.GLFW_PRESS, 0);
        client.keyboardHandler.keyPress(window(client), key, 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void rightClick(Minecraft client) {
        mouse(client).hotbarCheck$press(window(client), GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_PRESS, 0);
        mouse(client).hotbarCheck$press(window(client), GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_RELEASE, 0);
    }

    private static long window(Minecraft client) {
        return client.getWindow().getWindow();
    }

    private static MouseCallbacks mouse(Minecraft client) {
        return (MouseCallbacks) (Object) client.mouseHandler;
    }

    private static void serverAction(Minecraft client, Consumer<ServerPlayer> action) {
        var server = client.getSingleplayerServer();
        UUID id = client.player.getUUID();
        server.execute(() -> {
            try {
                action.accept(server.getPlayerList().getPlayer(id));
            } catch (Throwable failure) {
                serverFailure = failure;
            }
        });
    }

    private static void enter(Stage next, int tick) {
        began = tick;
        stage = next;
    }

    private static void require(boolean ok, String problem) {
        if (!ok) throw new AssertionError(problem);
    }

    private static void fail(Throwable failure) {
        System.err.println("HOTBAR CLIENT CHECK FAILED");
        failure.printStackTrace();
        stage = Stage.DONE;
    }
}
