package dev.uwu.hotbarcheck;

import dev.uwu.dimension.RelicFlight;
import dev.uwu.dimension.DawnpetalSpinCue;
import dev.uwu.dimension.RevivalBlossoms;
import dev.uwu.dimension.SharedAbilityHotbar;
import dev.uwu.hotbarcheck.mixin.MouseCallbacks;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.client.input.SpellHotbar;
import org.lwjgl.glfw.GLFW;

/** Drives the normal input callbacks and watches actual integrated-server gameplay and HUD rendering. */
@Mod("uwu_hotbar_check")
public final class HotbarProbe {
    private enum Stage {
        LOAD, CUPID_CONTROLS, TOOL_CONTROLS, ORDINARY_CONTROLS, EQUIP, RAPID, HIGH_SLOT, BACK, SCROLLED, DROP_KEY, DROP,
        REEQUIP, SELECT_THROW, EMPTY, TURN, THROW_KEY, FLIGHT,
        AXE_SELECT, AXE_LAYOUT, AXE_SPIN, AXE_READY, AXE_THROW, AXE_FLIGHT, DONE
    }

    private static final UUID DIRK = UUID.fromString("206e7d9c-e43b-4568-990a-6f789760f17e");
    private static final UUID AXE = UUID.fromString("67abe257-dc53-4709-a098-bb51da5261cd");
    private static volatile Stage stage = Stage.LOAD;
    private static boolean opening, observedEmptyHand, fifthCellAxe;
    private static int began, highSlot = 4, iconFrames;
    private static int axeFrames;
    private static int suppressedSpellKeys;
    private static int beforeSlot, beforeIcons, frameCells;
    private static ItemStack[] beforeFrame;
    private static volatile Throwable serverFailure;
    private static volatile ItemEntity dropped;
    private static volatile int turns, launches;
    private static volatile int axeLaunches;
    private static boolean axeLeftHeldSlot;
    private static volatile Vec3 lanePosition;
    private static volatile RelicFlight observedFlight;
    private static volatile boolean serverSawEmpty, serverSawLanding;
    private static volatile String flightProgress = "unlaunched";
    private static int useEvents, sent;
    private static volatile int received;

    public HotbarProbe() {
        NeoForge.EVENT_BUS.addListener(HotbarProbe::tick);
        NeoForge.EVENT_BUS.addListener(HotbarProbe::beforeFrame);
        NeoForge.EVENT_BUS.addListener(HotbarProbe::afterFrame);
        NeoForge.EVENT_BUS.addListener(HotbarProbe::joined);
        NeoForge.EVENT_BUS.addListener(HotbarProbe::serverTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, HotbarProbe::useEvent);
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
                require(player.tickCount - began < (stage == Stage.FLIGHT || stage == Stage.AXE_FLIGHT ? 280 : 120),
                        "Timed out in " + stage + ", slot=" + player.getInventory().selected
                                + ", icon frames=" + iconFrames + ", casts=" + launches + ", primes=" + turns
                                + ", use events/sent/received=" + useEvents + "/" + sent + "/" + received
                                + ", observed empty client/server=" + (observedEmptyHand || axeLeftHeldSlot)
                                + "/" + serverSawEmpty + ", landed=" + serverSawLanding + ", " + flightProgress);
            }
            Inventory inventory = player.getInventory();
            switch (stage) {
                case LOAD -> {
                    if (player.tickCount < 45) return;
                    require(ModList.get().isLoaded("spell_engine") && ModList.get().isLoaded("spell_power"),
                            "Spell Engine conflict fixture did not load");
                    client.setWindowActive(true);
                    serverAction(client, actor -> {
                        prepareLandingLane(actor);
                        actor.getInventory().selected = 0;
                        actor.getInventory().setItem(0, uwuItem("cupid_bow"));
                        actor.getInventory().setItem(1, uwuItem("bell_hammer"));
                        actor.getInventory().setItem(2, new ItemStack(Items.DIAMOND_SWORD));
                        actor.containerMenu.broadcastChanges();
                    });
                    enter(Stage.CUPID_CONTROLS, player.tickCount);
                }
                case CUPID_CONTROLS -> {
                    if (!isUwu(player.getMainHandItem(), "cupid_bow")) return;
                    require(player.getMainHandItem().getMaxDamage() == 6666, "Cupid durability differs from 6666");
                    requireOrdinaryKeys(client, player);
                    number(client, 1);
                    enter(Stage.TOOL_CONTROLS, player.tickCount);
                }
                case TOOL_CONTROLS -> {
                    require(inventory.selected == 1, "Cupid Bow intercepted number key 2");
                    if (!isUwu(player.getMainHandItem(), "bell_hammer")) return;
                    requireOrdinaryKeys(client, player);
                    number(client, 2);
                    enter(Stage.ORDINARY_CONTROLS, player.tickCount);
                }
                case ORDINARY_CONTROLS -> {
                    require(inventory.selected == 2 && player.getMainHandItem().is(Items.DIAMOND_SWORD),
                            "Bell Hammer intercepted number key 3");
                    requireOrdinaryKeys(client, player);
                    System.out.println("HOTBAR EXCLUSIONS PASS Cupid, mining tool and ordinary weapon keep normal keys");
                    serverAction(client, actor -> {
                        actor.getInventory().selected = 0;
                        actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, newDirk());
                        for (int slot = 1; slot <= 8; slot++) {
                            actor.getInventory().setItem(slot, new ItemStack(Items.STICK, slot));
                        }
                        actor.containerMenu.broadcastChanges();
                    });
                    number(client, 0);
                    enter(Stage.EQUIP, player.tickCount);
                }
                case EQUIP -> {
                    if (!hasDirk(player.getMainHandItem()) || iconFrames < 2 || SpellHotbar.INSTANCE.slots.isEmpty()
                            || lanePosition == null || player.distanceToSqr(lanePosition) > .04 || player.getXRot() < 75) return;
                    require(SharedAbilityHotbar.visibleFor(player), "Held Dirk has no ability row");
                    requireCell(0);
                    for (int repeat = 0; repeat < 4; repeat++) {
                        for (int slot = 0; slot < 4; slot++) number(client, slot);
                    }
                    // Spell Engine also polls held keys outside the vanilla click queue.
                    for (int slot = 0; slot < 4; slot++) keyAction(client, GLFW.GLFW_KEY_1 + slot, GLFW.GLFW_PRESS);
                    enter(Stage.RAPID, player.tickCount);
                }
                case RAPID -> {
                    require(inventory.selected == 0 && hasDirk(player.getMainHandItem()),
                            "An ability key switched the real held inventory slot");
                    if (player.tickCount - began == 2) {
                        for (int slot = 0; slot < 4; slot++) keyAction(client, GLFW.GLFW_KEY_1 + slot, GLFW.GLFW_RELEASE);
                        require(suppressedSpellKeys > 0, "Spell Engine did not poll any held mapped ability key");
                    }
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
                    if (player.getAttackStrengthScale(0) < 1F) return;
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
                    if (player.getAttackStrengthScale(0) < 1F) return;
                    resetFlightObservation();
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
                    require(serverSawEmpty && serverSawLanding, "Dirk returned without observed custody and landing");
                    require(sent == 2 && received == 2, "Two mapped mouse clicks did not each send exactly one ability");
                    requireCell(0);
                    require(iconFrames >= 4, "Missing live held-item icon observations");
                    System.out.println("HOTBAR DISPLAY PASS current held item, unchanged inventory through HUD frames=" + iconFrames);
                    System.out.println("HOTBAR SPELL ENGINE PASS bound spell slots loaded and shared keys blocked="
                            + suppressedSpellKeys + " while physical inventory, Turn and Throw checks passed");
                    System.out.println("DIRK THROW PASS empty cell restored Turn, real right-click launched one Dirk,"
                            + " held slot became empty and the exact Dirk returned to it");
                    serverAction(client, actor -> {
                        var axe = new ItemStack(RevivalBlossoms.AXE.get());
                        CustomData.update(DataComponents.CUSTOM_DATA, axe, tag -> tag.putUUID("uwu_revival_id", AXE));
                        actor.getInventory().setItem(4, axe);
                        actor.containerMenu.broadcastChanges();
                    });
                    enter(Stage.AXE_SELECT, player.tickCount);
                }
                case AXE_SELECT -> {
                    if (!hasAxe(inventory.getItem(4))) return;
                    number(client, 4);
                    enter(Stage.AXE_LAYOUT, player.tickCount);
                }
                case AXE_LAYOUT -> {
                    require(inventory.selected == 4 && hasAxe(player.getMainHandItem()), "Key 5 did not equip the Axe");
                    require(SharedAbilityHotbar.visibleFor(player) && !SharedAbilityHotbar.showsWeaponIndicator(player),
                            "Slot 5 should show the skill row and native held item cell");
                    requireCell(0);
                    if (axeFrames < 2 || player.getAttackStrengthScale(0) < 1F) return;
                    number(client, 0);
                    enter(Stage.AXE_SPIN, player.tickCount);
                    rightClick(client);
                }
                case AXE_SPIN -> {
                    if (DawnpetalSpinCue.frame(player, 0F, true) == null) return;
                    require(inventory.selected == 4 && hasAxe(player.getMainHandItem()) && axeLaunches == 0,
                            "Slot 1 resolved to anything other than the held Axe's Spin");
                    enter(Stage.AXE_READY, player.tickCount);
                }
                case AXE_READY -> {
                    if (player.tickCount - began < 80 || player.getCooldowns().isOnCooldown(RevivalBlossoms.AXE.get())) return;
                    number(client, 1);
                    enter(Stage.AXE_THROW, player.tickCount);
                }
                case AXE_THROW -> {
                    require(inventory.selected == 4 && axeLaunches == 0, "Slot 2 switched or used the Axe before right-click");
                    requireCell(1);
                    if (player.getAttackStrengthScale(0) < 1F) return;
                    resetFlightObservation();
                    enter(Stage.AXE_FLIGHT, player.tickCount);
                    rightClick(client);
                }
                case AXE_FLIGHT -> {
                    if (player.getMainHandItem().isEmpty()) axeLeftHeldSlot = true;
                    if (!axeLeftHeldSlot || axeLaunches == 0 || !hasAxe(player.getMainHandItem())) return;
                    require(inventory.selected == 4 && axeLaunches == 1 && serverSawEmpty && serverSawLanding,
                            "Axe did not land and return to its original slot");
                    System.out.println("HOTBAR AXE PASS ordinary cell 5 and four ability cells without extra icon; real Spin and Throw");
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
        fifthCellAxe = false;
        if (player == null || stage == Stage.LOAD || stage == Stage.DONE
                || !hasDirk(player.getMainHandItem()) && !hasAxe(player.getMainHandItem())) return;
        Inventory inventory = player.getInventory();
        beforeSlot = inventory.selected;
        beforeIcons = iconFrames;
        beforeFrame = new ItemStack[inventory.getContainerSize()];
        for (int slot = 0; slot < beforeFrame.length; slot++) beforeFrame[slot] = inventory.getItem(slot).copy();
    }

    private static void afterFrame(RenderFrameEvent.Post event) {
        var player = Minecraft.getInstance().player;
        if (player == null || beforeFrame == null || stage == Stage.DONE) return;
        boolean axe = hasAxe(player.getMainHandItem()) && beforeSlot == 4;
        if (!axe && iconFrames == beforeIcons) return;
        try {
            Inventory inventory = player.getInventory();
            require(frameCells == 0b1111, "HUD did not replace exactly the first four hotbar cells: " + frameCells);
            if (axe) {
                require(fifthCellAxe, "Slot 5 did not draw the Axe in its native hotbar cell");
                axeFrames++;
            }
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
        if (player != null && player.getInventory().selected == 4 && hasAxe(player.getMainHandItem())) {
            fail(new AssertionError("Slot 5 already has its own Axe icon"));
            return;
        }
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

    public static void drawnVanillaFifth(ItemStack stack) {
        if (beforeFrame != null && beforeSlot == 4 && hasAxe(stack)) fifthCellAxe = true;
    }

    public static void suppressedSpellKey() {
        if (stage == Stage.RAPID) suppressedSpellKeys++;
    }

    public static void armedTurn() {
        if (stage == Stage.TURN) turns++;
    }

    private static void useEvent(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && (stage == Stage.TURN || stage == Stage.FLIGHT)) useEvents++;
    }

    public static void sentUse() {
        if (stage == Stage.TURN || stage == Stage.FLIGHT) sent++;
    }

    public static void receivedUse() {
        if (stage == Stage.TURN || stage == Stage.FLIGHT) received++;
    }

    private static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (stage == Stage.DROP && event.getEntity() instanceof ItemEntity item && hasDirk(item.getItem())) {
            dropped = item;
        }
        if (stage == Stage.FLIGHT && event.getEntity() instanceof RelicFlight flight
                && flight.physical() && hasDirk(flight.getItem())) {
            launches++;
            observedFlight = flight;
        }
        if (stage == Stage.AXE_FLIGHT && event.getEntity() instanceof RelicFlight flight
                && flight.physical() && hasAxe(flight.getItem())) {
            axeLaunches++;
            observedFlight = flight;
        }
    }

    private static void serverTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || (stage != Stage.FLIGHT && stage != Stage.AXE_FLIGHT)) return;
        RelicFlight flight = observedFlight;
        if (flight == null) return;
        serverSawEmpty |= player.getMainHandItem().isEmpty();
        serverSawLanding |= flight.landed();
        flightProgress = "server slot=" + player.getInventory().selected + ", stack=" + player.getMainHandItem()
                + ", pitch=" + player.getXRot() + ", flight ticks=" + flight.tickCount + ", returning=" + flight.returning()
                + ", alive=" + flight.isAlive() + ", distance=" + Math.round(player.distanceTo(flight));
    }

    private static void resetFlightObservation() {
        observedFlight = null;
        serverSawEmpty = false;
        serverSawLanding = false;
        flightProgress = "unlaunched";
    }

    private static ItemStack uwuItem(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension", name)));
    }

    private static boolean isUwu(ItemStack stack, String name) {
        return stack.is(uwuItem(name).getItem());
    }

    private static void requireOrdinaryKeys(Minecraft client, net.minecraft.world.entity.player.Player player) {
        require(!SharedAbilityHotbar.visibleFor(player), "Ability row appeared for " + player.getMainHandItem());
        require(!SharedAbilityHotbar.ownsMappedKey(client.options.keyUse), "Mod intercepted normal item use");
        for (var key : client.options.keyHotbarSlots) {
            require(!SharedAbilityHotbar.ownsMappedKey(key), "Mod intercepted a normal number key");
        }
    }

    private static void prepareLandingLane(ServerPlayer player) {
        // Keep the real projectile inside the ticking area. Aim at a solid nearby floor,
        // so natural terrain and the world's random spawn facing cannot affect custody.
        BlockPos floor = player.blockPosition().above(16);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos ground = floor.offset(x, 0, z);
                player.serverLevel().setBlockAndUpdate(ground, Blocks.DEEPSLATE.defaultBlockState());
                player.serverLevel().setBlockAndUpdate(ground.above(), Blocks.AIR.defaultBlockState());
                player.serverLevel().setBlockAndUpdate(ground.above(2), Blocks.AIR.defaultBlockState());
            }
        }
        lanePosition = Vec3.atBottomCenterOf(floor.above());
        require(player.teleportTo(player.serverLevel(), lanePosition.x, lanePosition.y, lanePosition.z,
                Set.of(), 0F, 82F), "Could not position player and aim at the fixture landing floor");
        player.fallDistance = 0;
    }

    private static ItemStack newDirk() {
        var stack = new ItemStack(RevivalBlossoms.DIRK.get());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID("uwu_revival_id", DIRK));
        // Force genuine competing Spell Engine slots using stock spells in the disposable world.
        stack.set(SpellDataComponents.SPELL_CONTAINER, new SpellContainer(SpellContainer.ContentType.ANY, "", "", 5,
                List.of("rpg_series:swift_strikes", "rpg_series:cleave", "rpg_series:swipe",
                        "rpg_series:thrust", "rpg_series:whirlwind")));
        return stack;
    }

    private static boolean hasDirk(ItemStack stack) {
        return stack.is(RevivalBlossoms.DIRK.get()) && hasIdentity(stack, DIRK);
    }

    private static boolean hasAxe(ItemStack stack) {
        return stack.is(RevivalBlossoms.AXE.get()) && hasIdentity(stack, AXE);
    }

    private static boolean hasIdentity(ItemStack stack, UUID identity) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID("uwu_revival_id") && identity.equals(tag.getUUID("uwu_revival_id"));
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
        keyAction(client, key, GLFW.GLFW_PRESS);
        keyAction(client, key, GLFW.GLFW_RELEASE);
    }

    private static void keyAction(Minecraft client, int key, int action) {
        client.keyboardHandler.keyPress(window(client), key, 0, action, 0);
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
