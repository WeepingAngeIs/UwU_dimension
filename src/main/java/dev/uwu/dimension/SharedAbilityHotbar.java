package dev.uwu.dimension;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/** Displays and selects a held weapon's skills without modifying its inventory slots. */
@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class SharedAbilityHotbar {
    public static final int SKILL_CELLS = 4;

    private enum Action {
        CONTEXTUAL("", ""), DRAW("draw", "➶"), THROW("throw", "➶"), SUMMON("summon", "+"),
        LASH("lash", "~"), RAY("ray", "*"), CAST("cast", "*"), FIRE("fire", "+");

        final Component label;
        final String icon;

        Action(String name, String icon) {
            label = Component.translatable("uwudimension.ability." + name);
            this.icon = icon;
        }
    }

    private static LocalPlayer actor;
    private static Item equipped;
    private static UUID equippedId;
    private static int equippedSlot = -1;
    private static int selectedCell;
    private static boolean explicitUse;
    private static long sequence;

    private SharedAbilityHotbar() {}

    private static Action action(ItemStack stack) {
        Item item = stack.getItem();
        if (stack.is(RevivalBlossoms.AXE.get()) || stack.is(RevivalBlossoms.DIRK.get())) return Action.CONTEXTUAL;
        if (item instanceof Arsenal.RelicItem relic) {
            return switch (relic.relic) {
                case CUPID_BOW -> null;
                case RIPPLE_BOW -> Action.DRAW;
                case RIBBONHARE_SCEPTER -> Action.SUMMON;
                case AXOLOTL_WHIP, FROST_WHIP, RIFT_WHIP, SATIN_WHIP -> Action.LASH;
                case BLUE_HOUR_WAND -> Action.RAY;
                case BLOSSOM_GREATSWORD, BELLHEART_AXE, LOCKKEEPER_AXE, VESPER_SCYTHE,
                     ROSE_DAGGER, LANTERN_SPEAR, TIDELACE_TRIDENT -> Action.THROW;
            };
        }
        if (item instanceof CounterArsenal.CounterItem || item instanceof GlitchBow) return Action.CAST;
        if (item instanceof HeartLauncher.LauncherItem) return Action.FIRE;
        return null;
    }

    private static Action currentAction(Minecraft client, Player player) {
        return player == null || player != client.player || !player.isAlive() || player.isSpectator()
                || client.screen != null || client.getOverlay() != null ? null : action(player.getMainHandItem());
    }

    public static boolean visibleFor(Player player) {
        return currentAction(Minecraft.getInstance(), player) != null;
    }

    public static int selectedCell() {
        return selectedCell;
    }

    private static Action refresh(Minecraft client) {
        LocalPlayer player = client.player;
        Action action = currentAction(client, player);
        Item item = action == null ? null : player.getMainHandItem().getItem();
        UUID id = action == Action.CONTEXTUAL ? DawnpetalVanillaReach.syncedIdentity(player.getMainHandItem()) : null;
        int slot = action == null ? -1 : player.getInventory().selected;
        if (player != actor || item != equipped || !Objects.equals(id, equippedId) || slot != equippedSlot) {
            actor = player;
            equipped = item;
            equippedId = id;
            equippedSlot = slot;
            selectedCell = 0;
        }
        return action;
    }

    private static boolean creativeSave(Minecraft client) {
        return client.player.isCreative()
                && (client.options.keyLoadHotbarActivator.isDown() || client.options.keySaveHotbarActivator.isDown());
    }

    public static void handleHotbar(Minecraft client) {
        Action action = refresh(client);
        if (action == null || creativeSave(client)) return;
        // Take every click for this tick before the vanilla or Spell Engine slot loops.
        // In a mixed batch, finish draining ability clicks before selecting an inventory slot.
        int inventorySelection = -1;
        for (int index = 0; index < client.options.keyHotbarSlots.length; index++) {
            boolean pressed = false;
            while (client.options.keyHotbarSlots[index].consumeClick()) pressed = true;
            if (!pressed) continue;
            if (index < SKILL_CELLS) {
                selectedCell = action == Action.CONTEXTUAL && DawnpetalAbility.at(client.player, index) != null ? index : 0;
            } else {
                inventorySelection = index;
            }
        }
        if (inventorySelection >= 0) {
            client.player.getInventory().selected = inventorySelection;
            refresh(client);
        }
    }

    public static boolean ownsMappedKey(KeyMapping mapping) {
        Minecraft client = Minecraft.getInstance();
        if (!visibleFor(client.player) || creativeSave(client)) return false;
        if (mapping == client.options.keyUse) return true;
        for (KeyMapping hotbar : client.options.keyHotbarSlots) {
            if (mapping == hotbar) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        refresh(client);
        if (!client.isWindowActive()) selectedCell = 0;
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (event.getScrollDeltaY() != 0) selectedCell = 0;
    }

    public static void withQueuedUse(Runnable use) {
        boolean previous = explicitUse;
        explicitUse = true;
        try {
            use.run();
        } finally {
            explicitUse = previous;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft client = Minecraft.getInstance();
        if (!event.isUseItem() || refresh(client) != Action.CONTEXTUAL) return;
        if (client.player.isShiftKeyDown()) {
            selectedCell = 0;
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!explicitUse || event.getHand() != InteractionHand.MAIN_HAND || equippedId == null
                || !DawnpetalAbilities.hasHotbar(client.player)) return;
        DawnpetalAbility move = DawnpetalAbility.at(client.player, selectedCell);
        if (move == DawnpetalAbility.THROW) selectedCell = 0;
        if (move == null || client.player.isUsingItem() || onCooldown(client.player, move)) return;
        DawnpetalAbilities.send(client.player, ++sequence, equippedId, move);
    }

    private static boolean onCooldown(Player player, DawnpetalAbility ability) {
        return ability == DawnpetalAbility.HEART_SPIN ? DawnpetalAbilities.heartSpinCoolingDown(player)
                : player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem());
    }

    public static boolean drawSlot(GuiGraphics gui, Player player, int index, int iconX, int iconY) {
        if (index < 0 || index >= SKILL_CELLS) return false;
        Minecraft client = Minecraft.getInstance();
        Action action = refresh(client);
        if (action == null || player != client.player) return false;
        var ability = action == Action.CONTEXTUAL ? DawnpetalAbility.at(player, index) : null;
        boolean filled = action == Action.CONTEXTUAL ? ability != null : index == 0;
        boolean cooldown = filled && onCooldown(player, ability);
        boolean selected = filled && selectedCell == index;
        int x = iconX - 2, y = iconY - 2;
        gui.fill(x, y, x + 20, y + 20, selected ? 0xfff597c4 : 0xff745269);
        gui.fill(x + 1, y + 1, x + 19, y + 19, cooldown || !filled ? 0xff29232a : 0xff38243c);
        if (filled) {
            String icon = ability == null ? action.icon : switch (ability) {
                case SPIN -> "↻";
                case THROW -> "➶";
                case HEART_SPIN -> "♥";
            };
            gui.drawCenteredString(client.font, icon, x + 7, y + 4, cooldown ? 0xffb5a8b5 : 0xffffdbf0);
        }
        // Show the configured key in each cell, keeping long rebound names out of adjacent slots.
        Component key = client.options.keyHotbarSlots[index].getTranslatedKeyMessage();
        int keyWidth = client.font.width(key);
        if (keyWidth <= 10) {
            gui.drawString(client.font, key, x + 18 - keyWidth, y + 10, 0xfff8eaf3);
        }
        return true;
    }

    public static boolean showsWeaponIndicator(Player player) {
        return visibleFor(player) && player.getInventory().selected < SKILL_CELLS;
    }

    public static void renderWeaponIndicator(GuiGraphics gui, Player player) {
        if (!showsWeaponIndicator(player)) return;
        int x = gui.guiWidth() / 2 - 112, y = gui.guiHeight() - 21;
        gui.fill(x, y, x + 20, y + 20, 0xfff597c4);
        gui.fill(x + 1, y + 1, x + 19, y + 19, 0xff38243c);
        gui.renderItem(player.getMainHandItem(), x + 2, y + 2);
    }

    public static void renderSkillLabel(GuiGraphics gui, Player player) {
        Minecraft client = Minecraft.getInstance();
        Action action = refresh(client);
        if (action == null || player != client.player) return;
        Component label = action.label;
        if (action == Action.CONTEXTUAL) {
            var ability = DawnpetalAbility.at(player, selectedCell);
            if (ability == null) return;
            label = Component.translatable("uwudimension.ability." + ability.name().toLowerCase(Locale.ROOT));
        }
        // Place the action label over the ability cells, above the XP and health rows.
        Component use = Component.translatable("uwudimension.ability.hotbar_hint", label,
                client.options.keyUse.getTranslatedKeyMessage());
        gui.drawCenteredString(client.font, use, gui.guiWidth() / 2 - 50, gui.guiHeight() - 70, 0xffffdbf0);
    }
}
