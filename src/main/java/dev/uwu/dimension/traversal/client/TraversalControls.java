package dev.uwu.dimension.traversal.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.traversal.TraversalPackets;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** The client owner registers the bindings and polls at tick end. Native input capture is traversal-local. */
@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class TraversalControls {
    public static final KeyMapping SKILL = mapping("skill");
    public static final KeyMapping FORM = mapping("form");

    private record Equipment(LocalPlayer player, ClientLevel level, ClientPacketListener connection, int slot, ItemStack stack) {
        boolean matches(Equipment current) {
            return current != null && player == current.player && level == current.level && connection == current.connection
                    && slot == current.slot && stack == current.stack;
        }
    }

    private static Equipment pending;
    private static boolean pendingForm;
    private static boolean pendingSkill;

    private TraversalControls() {}

    private static KeyMapping mapping(String action) {
        return new KeyMapping("key.uwudimension.traversal." + action,
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.uwudimension.traversal");
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SKILL);
        event.register(FORM);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        boolean down = event.getAction() != 0;
        capture(down && FORM.matches(event.getKey(), event.getScanCode()), down && SKILL.matches(event.getKey(), event.getScanCode()));
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Post event) {
        boolean down = event.getAction() != 0;
        capture(down && FORM.matchesMouse(event.getButton()), down && SKILL.matchesMouse(event.getButton()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreen(ScreenEvent.Opening event) {
        // Minecraft.setScreen releases every native mapping when it opens the new screen.
        if (event.getNewScreen() != null) clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static Equipment equipment() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null || player.level() != mc.level || mc.getConnection() == null
                || mc.screen != null || !player.isAlive() || player.isSpectator()
                || player.isUsingItem() || player.isHandsBusy()) return null;
        return new Equipment(player, mc.level, mc.getConnection(), player.getInventory().selected, player.getMainHandItem());
    }

    private static void capture(boolean formEvent, boolean skillEvent) {
        // These callbacks run immediately after native KeyMapping.click. The counters themselves have no origin.
        boolean toggle = consume(FORM) && formEvent;
        boolean activate = consume(SKILL) && skillEvent;
        if (!toggle && !activate && pending == null) return;
        Equipment current = equipment();
        if (pending != null && !pending.matches(current)) clear();
        if (current == null || (!toggle && !activate)) return;
        var held = current.stack;
        boolean whip = held.is(Arsenal.WEAPONS.get(Arsenal.Relic.FROST_WHIP).get());
        pendingForm |= toggle && whip;
        pendingSkill |= activate && (whip || held.is(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get()));
        if (pendingForm || pendingSkill) pending = current;
    }

    public static void poll() {
        poll(action -> PacketDistributor.sendToServer(new TraversalPackets.Request(action)));
    }

    static void poll(Consumer<TraversalPackets.Action> send) {
        consume(FORM);
        consume(SKILL);
        if (pending == null) return;
        boolean same = pending.matches(equipment());
        boolean toggle = same && pendingForm;
        boolean activate = same && pendingSkill;
        clear();
        if (toggle) send.accept(TraversalPackets.Action.TOGGLE);
        if (activate) send.accept(TraversalPackets.Action.ACTIVATE);
    }

    static void discardCaptured() {
        clear();
        consume(FORM);
        consume(SKILL);
    }

    private static void clear() {
        pending = null;
        pendingForm = false;
        pendingSkill = false;
    }

    private static boolean consume(KeyMapping key) {
        boolean clicked = false;
        while (key.consumeClick()) clicked = true;
        return clicked;
    }
}
