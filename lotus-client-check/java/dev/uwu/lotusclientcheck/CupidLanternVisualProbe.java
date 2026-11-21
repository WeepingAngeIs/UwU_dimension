package dev.uwu.lotusclientcheck;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Actual client pixels, including a 16 GUI-pixel comparison of the exact shipped portraits. */
@EventBusSubscriber(modid = "uwu_lotus_client_check", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CupidLanternVisualProbe {
    private static final Path OUTPUT = Path.of(System.getProperty("uwu.check.evidence", "../cupid-lantern-evidence"));
    private static final List<String> POSES = List.of("", "_draw0", "_draw1", "_deep", "_draw2");
    private static final Set<String> PENDING = new LinkedHashSet<>();
    private static final Set<String> SAVED = new LinkedHashSet<>();
    private static ItemStack classic, smooth;
    private static boolean iconsPending, iconsDrawn, iconsSaved;
    private static Throwable failure;

    private CupidLanternVisualProbe() {}

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        if (!Boolean.getBoolean("uwu.check.cupidLantern")) return;
        for (String suffix : POSES) {
            event.register(model("uwudimension", "cupid_bow" + suffix));
            event.register(model("uwudimension", "smooth_cupid_bow" + suffix));
            event.register(model("uwu_lotus_client_check", "playtest9_smooth_cupid_bow" + suffix));
        }
    }

    static boolean icons(ItemStack original, ItemStack detailed) {
        rethrow();
        if (iconsSaved) return true;
        classic = original;
        smooth = detailed;
        iconsPending = true;
        return false;
    }

    public static void request(String name) {
        rethrow();
        if (!name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid evidence name");
        if (!SAVED.contains(name)) PENDING.add(name);
    }

    static void rethrow() {
        if (failure != null) throw new AssertionError("Could not capture rendered evidence", failure);
    }

    static void gui(RenderGuiEvent.Post event) {
        if (!iconsPending) return;
        try {
            var client = Minecraft.getInstance();
            var gui = event.getGuiGraphics();
            gui.fill(8, 8, 388, 258, 0xFF24202B);
            gui.drawString(client.font, "Original", 18, 15, 0xFFFFC6DC, false);
            gui.drawString(client.font, "Playtest 9", 138, 15, 0xFFFFC6DC, false);
            gui.drawString(client.font, "Revised", 258, 15, 0xFFFFC6DC, false);
            for (int row = 0; row < POSES.size(); row++) {
                String suffix = POSES.get(row);
                int y = 38 + row * 44;
                BakedModel[] models = {
                    client.getModelManager().getModel(model("uwudimension", "cupid_bow" + suffix)),
                    client.getModelManager().getModel(model("uwu_lotus_client_check", "playtest9_smooth_cupid_bow" + suffix)),
                    client.getModelManager().getModel(model("uwudimension", "smooth_cupid_bow" + suffix))
                };
                for (int col = 0; col < 3; col++) {
                    int x = 18 + col * 120;
                    ItemStack stack = col == 0 ? classic : smooth;
                    icon(gui, stack, models[col], x, y, 1);
                    icon(gui, stack, models[col], x + 34, y, 2);
                    gui.drawString(client.font, suffix.isEmpty() ? "idle" : suffix.substring(1), x, y + 23,
                            0xFFE2D7E8, false);
                }
            }
            gui.flush();
            iconsDrawn = true;
        } catch (Throwable problem) {
            failure = problem;
            iconsPending = false;
        }
    }

    private static void icon(GuiGraphics gui, ItemStack stack, BakedModel model, int x, int y, int scale) {
        var poses = gui.pose();
        poses.pushPose();
        try {
            poses.translate(x + 8 * scale, y + 8 * scale, 150);
            poses.scale(16 * scale, -16 * scale, 16 * scale);
            if (!model.usesBlockLight()) Lighting.setupForFlatItems();
            Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.GUI, false, poses,
                    gui.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
            gui.flush();
        } finally {
            poses.popPose();
            Lighting.setupFor3DItems();
        }
    }

    static void frame(RenderFrameEvent.Post event) {
        try {
            if (iconsPending && iconsDrawn) {
                Files.createDirectories(OUTPUT);
                var client = Minecraft.getInstance();
                try (NativeImage pixels = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    pixels.writeToFile(OUTPUT.resolve("hotbar-original-playtest9-revised.png"));
                    double scale = client.getWindow().getGuiScale();
                    double original = diagonalBias(pixels, 18, 38, scale);
                    double before = diagonalBias(pixels, 138, 38, scale);
                    double revised = diagonalBias(pixels, 258, 38, scale);
                    String metrics = "original=" + original + " playtest9=" + before + " revised=" + revised;
                    Files.writeString(OUTPUT.resolve("hotbar-silhouette.txt"), metrics + "\n");
                    require(Math.abs(revised - before) > .25,
                            "The actual hotbar silhouette did not change after reversing its outward curve: " + metrics);
                    // The centroid is a diagnostic, not a proof of curvature: the painted bow/string
                    // can be offset within its slot. Inspect the retained three-way rendered comparison.
                    System.out.println("CUPID HOTBAR RENDER PASS actual 16-pixel comparison captured: " + metrics);
                }
                iconsSaved = true;
                iconsPending = false;
                iconsDrawn = false;
            }
            if (!PENDING.isEmpty()) {
                Files.createDirectories(OUTPUT);
                try (NativeImage pixels = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                    for (String name : List.copyOf(PENDING)) {
                        pixels.writeToFile(OUTPUT.resolve(name + ".png"));
                        var client = Minecraft.getInstance();
                        var player = client.player;
                        Files.writeString(OUTPUT.resolve("frames.tsv"), name + "\t"
                                        + (player == null ? "no-player" : player.tickCount + "\t" + player.getTicksUsingItem()
                                        + "\t" + player.getUsedItemHand()) + "\t" + client.options.getCameraType() + "\n",
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        SAVED.add(name);
                    }
                }
                PENDING.clear();
            }
        } catch (Throwable problem) {
            failure = problem;
            iconsPending = false;
            PENDING.clear();
        }
    }

    // Measure real bright bow pixels relative to the rising diagonal. Negative points upper-left.
    private static double diagonalBias(NativeImage pixels, int x, int y, double scale) {
        double sum = 0;
        int count = 0;
        int size = (int) Math.round(16 * scale);
        for (int dy = 0; dy < size; dy++) for (int dx = 0; dx < size; dx++) {
            int color = pixels.getPixelRGBA((int) Math.round(x * scale) + dx, (int) Math.round(y * scale) + dy);
            int r = color & 255, g = (color >>> 8) & 255, b = (color >>> 16) & 255;
            if (Math.max(r, Math.max(g, b)) <= 100 || r < g * .85) continue;
            sum += (dx + .5) / scale + (dy + .5) / scale - 16;
            count++;
        }
        require(count >= 6, "Missing actual hotbar pixels at " + x + "," + y);
        return sum / count;
    }

    private static ModelResourceLocation model(String namespace, String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(namespace, "item/" + name));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
