package dev.uwu.dimension;

import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class DawnpetalAbilityClient {
    private static LocalPlayer actor;
    private static UUID equippedId;
    private static int equippedSlot = -1;
    private static DawnpetalAbility active = DawnpetalAbility.SPIN;
    private static boolean explicitUse;
    private static long sequence;
    private static int hintTicks;

    private static boolean refresh(Minecraft mc) {
        LocalPlayer p = mc.player;
        UUID id = p == null || !DawnpetalAbilities.equipped(p) ? null
                : DawnpetalVanillaReach.syncedIdentity(p.getMainHandItem());
        int slot = id == null ? -1 : p.getInventory().selected;
        if (actor != p || !java.util.Objects.equals(equippedId, id) || equippedSlot != slot) {
            active = DawnpetalAbility.SPIN;
            actor = p;
            equippedId = id;
            equippedSlot = slot;
            hintTicks = id == null ? 0 : 90;
        }
        return id != null;
    }

    public static boolean handleHotbar(Minecraft mc, KeyMapping key) {
        if (mc.screen != null || !refresh(mc) || (mc.player.isCreative()
                && (mc.options.keyLoadHotbarActivator.isDown() || mc.options.keySaveHotbarActivator.isDown()))) return false;
        for (int i = 0; i < mc.options.keyHotbarSlots.length; i++) {
            if (mc.options.keyHotbarSlots[i] != key) continue;
            if (i >= 4) { active = DawnpetalAbility.SPIN; return false; }
            DawnpetalAbility choice = DawnpetalAbility.at(i);
            if (choice != null) {
                active = choice;
                hintTicks = 90;
            } else {
                // An empty-slot press never leaves an unseen Throw armed.
                active = DawnpetalAbility.SPIN;
            }
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        refresh(mc);
        if (hintTicks > 0) hintTicks--;
        if (mc.screen != null || !mc.isWindowActive()) active = DawnpetalAbility.SPIN;
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (event.getScrollDeltaY() != 0) active = DawnpetalAbility.SPIN;
    }

    public static void withQueuedUse(Runnable use) {
        boolean previous = explicitUse;
        explicitUse = true;
        try { use.run(); } finally { explicitUse = previous; }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.screen != null || !refresh(mc)) return;
        if (mc.player.isShiftKeyDown()) { active = DawnpetalAbility.SPIN; return; }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.getHand() != InteractionHand.MAIN_HAND || !explicitUse) return;
        DawnpetalAbility move = active;
        // Throw is opt-in every time, never inherited by a newly equipped/returned axe.
        if (move == DawnpetalAbility.THROW) active = DawnpetalAbility.SPIN;
        if (mc.player.isUsingItem() || mc.player.getCooldowns().isOnCooldown(RevivalBlossoms.AXE.get())) return;
        DawnpetalAbilities.send(mc.player, ++sequence, equippedId, move);
    }

    @SubscribeEvent
    public static void render(RenderGuiLayerEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName()) || mc.options.hideGui || mc.screen != null || !refresh(mc)) return;
        var gui = event.getGuiGraphics();
        int center = gui.guiWidth() / 2, left = center - 40, top = gui.guiHeight() - 80;
        boolean cooldown = mc.player.getCooldowns().isOnCooldown(RevivalBlossoms.AXE.get());
        for (int i = 0; i < 4; i++) {
            DawnpetalAbility move = DawnpetalAbility.at(i);
            int x = left + i * 20;
            gui.fill(x, top, x + 20, top + 19, active == move ? 0xfff597c4 : move == null ? 0xaa382d39 : 0xcc745269);
            gui.fill(x + 1, top + 1, x + 19, top + 18, cooldown || move == null ? 0xee29232a : 0xf038243c);
            String symbol = move == DawnpetalAbility.SPIN ? "↻" : move == DawnpetalAbility.THROW ? "➶" : "";
            gui.drawCenteredString(mc.font, symbol, x + 10, top + 5, cooldown ? 0xffb5a8b5 : 0xffffdbf0);
            gui.drawCenteredString(mc.font, mc.options.keyHotbarSlots[i].getTranslatedKeyMessage(), x + 10, top - 10,
                    move == null ? 0xffa699a3 : 0xfff8eaf3);
        }
        var label = Component.translatable("uwudimension.ability." + active.name().toLowerCase(java.util.Locale.ROOT));
        gui.drawCenteredString(mc.font, label, left + active.index * 20 + 10, top - 21, 0xffffdbf0);
        if (hintTicks > 0) {
            int alpha = Math.min(255, 35 + hintTicks * 13);
            gui.drawCenteredString(mc.font, Component.translatable("uwudimension.ability.use", mc.options.keyUse.getTranslatedKeyMessage()),
                    center, top + 21, (alpha << 24) | 0xfff1fa);
        }
    }

    private DawnpetalAbilityClient() {}
}
