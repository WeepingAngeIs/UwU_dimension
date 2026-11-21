package dev.uwu.lotusclientcheck;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Drives the real held-book use path in the hosted disposable client, including ordinary books. */
final class LanternOpeningProbe {
    private enum Kind { NATURAL, RENAMED_OFF_HAND, RESIZE_SKIP, CANCEL, REOPEN_CLICK, ORDINARY }
    private record Trial(Kind kind, InteractionHand hand) {}
    private static final List<Trial> TRIALS = List.of(
            new Trial(Kind.NATURAL, InteractionHand.MAIN_HAND),
            new Trial(Kind.RENAMED_OFF_HAND, InteractionHand.OFF_HAND),
            new Trial(Kind.RESIZE_SKIP, InteractionHand.MAIN_HAND),
            new Trial(Kind.CANCEL, InteractionHand.OFF_HAND),
            new Trial(Kind.REOPEN_CLICK, InteractionHand.OFF_HAND),
            new Trial(Kind.ORDINARY, InteractionHand.MAIN_HAND));

    private ItemStack original;
    private ItemStack expected;
    private int trialIndex;
    private boolean requested;
    private boolean used;
    private boolean observedIntro;
    private boolean triedNavigation;
    private boolean resized;
    private boolean skipped;
    private int canceledAt = -1;
    private int readerAt = -1;
    private int openingSide;
    private int clientTicks;
    private long deadline;
    private final Set<String> screenshots = new HashSet<>();
    private volatile Throwable serverFailure;

    boolean tick(Minecraft client) throws ReflectiveOperationException {
        if (client.player == null || client.level == null || client.gameMode == null) return false;
        clientTicks++;
        if (serverFailure != null) throw new AssertionError("Could not prepare Lantern book hands", serverFailure);
        if (original == null) {
            var create = Class.forName("dev.uwu.dimension.LanternBookStacks")
                    .getDeclaredMethod("create", net.minecraft.core.HolderLookup.Provider.class);
            create.setAccessible(true);
            original = (ItemStack) create.invoke(null, client.level.registryAccess());
            require(original.get(DataComponents.WRITTEN_BOOK_CONTENT).getPages(false).size() == 53,
                    "Fixture did not load the shipped 53-page story");
            LanternCoverProbe.verify(client);
        }
        if (trialIndex >= TRIALS.size()) {
            System.out.println("LANTERN OPENING CLIENT PASS held use, both hands, renamed marker, lift/open/three decorative leaves,"
                    + " page one throughout, normal next/back navigation, resize, keyboard/click skip, cancel/reopen and ordinary-book bypass");
            return true;
        }

        Trial trial = TRIALS.get(trialIndex);
        if (!requested) {
            requested = true;
            deadline = System.nanoTime() + 25_000_000_000L;
            client.setScreen(null);
            expected = original.copy();
            if (trial.kind == Kind.RENAMED_OFF_HAND) expected.set(DataComponents.CUSTOM_NAME, Component.literal("A reader's Lantern"));
            if (trial.kind == Kind.ORDINARY) expected.remove(DataComponents.CUSTOM_DATA);
            ItemStack prepared = expected.copy();
            var server = client.getSingleplayerServer();
            var playerId = client.player.getUUID();
            server.execute(() -> {
                try {
                    var actor = server.getPlayerList().getPlayer(playerId);
                    require(actor != null, "Missing fixture reader");
                    actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    actor.setItemInHand(trial.hand, prepared);
                } catch (Throwable failure) {
                    serverFailure = failure;
                }
            });
            return false;
        }
        require(System.nanoTime() < deadline, "Timed out in Lantern opening trial " + trial);
        if (canceledAt >= 0) {
            require(client.screen == null, "Canceled opening reopened the story later");
            if (clientTicks - canceledAt >= 60) advance(client, trial);
            return false;
        }

        ItemStack held = client.player.getItemInHand(trial.hand);
        if (!ItemStack.isSameItemSameComponents(held, expected)) return false;
        if (!used) {
            var other = trial.hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (!client.player.getItemInHand(other).isEmpty()) return false;
            used = true;
            require(client.gameMode.useItem(client.player, trial.hand).consumesAction(), "Native book use failed for " + trial);
            return false;
        }

        Screen screen = client.screen;
        if (screen == null) return false; // Server book-open packet may still be in flight.
        if (screen.getClass().getName().equals("dev.uwu.dimension.LanternBookOpeningScreen")) {
            require(trial.kind != Kind.ORDINARY, "Ordinary written book entered the special opening");
            observedIntro = true;
            int age = intField(screen, "age");
            int side = intField(screen, "side");
            if (trial.kind == Kind.NATURAL) openingSide = side;
            if (trial.kind == Kind.RENAMED_OFF_HAND) require(side == -openingSide, "Off-hand book lifted from the wrong side");
            BookViewScreen reader = (BookViewScreen) field(screen, "reader").get(screen);
            require(screen.isPauseScreen() == reader.isPauseScreen(), "Opening changed the native reader's pause behavior");
            require(page(reader) == 0, "Decorative flutter advanced the actual story page");
            require(ItemStack.isSameItemSameComponents(held, expected), "Opening changed the signed story or marker");

            if (!triedNavigation && age >= 3) {
                triedNavigation = true;
                screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
                screen.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
                screen.mouseScrolled(0, 0, 0, -1);
                require(client.screen == screen && page(reader) == 0, "Intro navigation leaked into story pages");
            }
            if (trial.kind == Kind.NATURAL) {
                if (age >= 5 && age < 12) capture("lantern-lift");
                if (age >= 18 && age < 26) capture("lantern-cover-open");
                if (age >= 29 && age < 44) capture("lantern-decorative-flutter");
                if (age >= 47) capture("lantern-settle-page-one");
            }
            if (trial.kind == Kind.RENAMED_OFF_HAND && age >= 18) capture("lantern-left-hand-renamed");
            if (trial.kind == Kind.RESIZE_SKIP && age >= 16 && !resized) {
                resized = true;
                int width = screen.width, height = screen.height;
                screen.resize(client, width + 24, height + 12);
                screen.resize(client, width, height);
                require(intField(screen, "age") == age && page(reader) == 0, "Resize restarted the flourish or advanced the story");
            }
            if (trial.kind == Kind.RESIZE_SKIP && age >= 20 && !skipped) {
                skipped = true;
                screen.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
            }
            if (trial.kind == Kind.REOPEN_CLICK && age >= 8 && !skipped) {
                skipped = true;
                screen.mouseClicked(screen.width * .5, screen.height * .5, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            }
            if (trial.kind == Kind.CANCEL && age >= 16) {
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(client.screen == null, "Escape did not cancel the opening");
                canceledAt = clientTicks;
            }
            return false;
        }

        require(screen.getClass() == BookViewScreen.class, "Unexpected book screen " + screen.getClass());
        BookViewScreen reader = (BookViewScreen) screen;
        require(observedIntro || trial.kind == Kind.ORDINARY, "Marked book skipped its opening entirely");
        require(page(reader) == 0, "Book did not settle on actual story page one");
        if (readerAt < 0) {
            readerAt = clientTicks;
            capture(trial.kind == Kind.ORDINARY ? "ordinary-book-native-reader" : "lantern-story-page-one-" + trial.kind.name().toLowerCase());
            return false;
        }
        // The native reader pauses singleplayer entity ticks, but client ticks and frames continue.
        if (clientTicks - readerAt < 3) return false;
        reader.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
        require(page(reader) == 1, "Native next-page navigation failed");
        reader.keyPressed(GLFW.GLFW_KEY_PAGE_UP, 0, 0);
        require(page(reader) == 0, "Native previous-page navigation failed");
        advance(client, trial);
        return false;
    }

    private void capture(String name) {
        if (screenshots.add(name)) CupidLanternVisualProbe.request(name);
    }

    private void advance(Minecraft client, Trial trial) {
        System.out.println("LANTERN OPENING TRIAL PASS " + trial);
        client.setScreen(null);
        trialIndex++;
        requested = false;
        used = false;
        observedIntro = false;
        triedNavigation = false;
        resized = false;
        skipped = false;
        canceledAt = -1;
        readerAt = -1;
    }

    private static int page(BookViewScreen reader) throws ReflectiveOperationException {
        return intField(reader, "currentPage");
    }

    private static int intField(Object target, String name) throws ReflectiveOperationException {
        return field(target, name).getInt(target);
    }

    private static Field field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
