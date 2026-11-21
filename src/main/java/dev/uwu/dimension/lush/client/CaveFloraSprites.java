package dev.uwu.dimension.lush.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;
import org.slf4j.Logger;

/**
 * Creates separate cave sprites from the active resource pack's original plant silhouettes.
 * The original atlas entries are never edited. Every resulting shade comes from the shipped
 * pink cave grass or light-blue cave moss, and alpha, dimensions and animation stay intact.
 */
@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CaveFloraSprites implements SpriteSource {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final MapCodec<CaveFloraSprites> CODEC = MapCodec.unit(new CaveFloraSprites());
    private static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);
    private enum Tone { PINK, BLUE, FLOWERS }
    private static final Map<String, Tone> TEXTURES = Map.ofEntries(
            Map.entry("big_dripleaf_top", Tone.PINK),
            Map.entry("big_dripleaf_side", Tone.PINK),
            Map.entry("big_dripleaf_tip", Tone.PINK),
            Map.entry("big_dripleaf_stem", Tone.BLUE),
            Map.entry("small_dripleaf_top", Tone.BLUE),
            Map.entry("small_dripleaf_side", Tone.BLUE),
            Map.entry("small_dripleaf_stem_top", Tone.PINK),
            Map.entry("small_dripleaf_stem_bottom", Tone.PINK),
            Map.entry("azalea_top", Tone.BLUE),
            Map.entry("azalea_side", Tone.BLUE),
            Map.entry("azalea_plant", Tone.PINK),
            Map.entry("flowering_azalea_top", Tone.FLOWERS),
            Map.entry("flowering_azalea_side", Tone.FLOWERS),
            Map.entry("azalea_leaves", Tone.BLUE),
            Map.entry("flowering_azalea_leaves", Tone.FLOWERS),
            Map.entry("spore_blossom", Tone.PINK),
            Map.entry("spore_blossom_base", Tone.BLUE),
            Map.entry("glow_lichen", Tone.BLUE),
            Map.entry("hanging_roots", Tone.PINK),
            Map.entry("vine", Tone.BLUE),
            Map.entry("oak_log", Tone.PINK),
            Map.entry("oak_log_top", Tone.PINK),
            Map.entry("rooted_dirt", Tone.PINK));

    private CaveFloraSprites() {}

    @SubscribeEvent
    public static void register(RegisterSpriteSourceTypesEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath("uwudimension", "cave_flora"), TYPE);
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }

    @Override
    public void run(ResourceManager manager, Output output) {
        final int[] pink;
        final int[] blue;
        try {
            pink = palette(manager, "grass_block_top");
            blue = palette(manager, "moss_block");
        } catch (IOException failure) {
            LOGGER.error("Could not read the existing UwU cave palette", failure);
            return;
        }
        TEXTURES.forEach((name, tone) -> {
            var target = ResourceLocation.fromNamespaceAndPath("uwudimension", "block/uwu_cave/flora/" + name);
            var override = manager.getResource(TEXTURE_ID_CONVERTER.idToFile(target));
            if (override.isPresent()) {
                // Let an explicit resource-pack artwork override replace our generated copy.
                output.add(target, loader -> loader.loadSprite(target, override.get()));
                return;
            }
            var source = ResourceLocation.withDefaultNamespace("block/" + name);
            var resource = manager.getResource(TEXTURE_ID_CONVERTER.idToFile(source));
            if (resource.isEmpty()) {
                LOGGER.warn("Missing source for UwU cave plant sprite: {}", source);
                return;
            }
            output.add(target, loader -> {
                // loadSprite opens a new NativeImage, distinct from the original vanilla sprite.
                var contents = loader.loadSprite(target, resource.get());
                if (contents != null) {
                    try {
                        recolor(contents.getOriginalImage(), tone, pink, blue);
                    } catch (RuntimeException failure) {
                        contents.close();
                        throw failure;
                    }
                }
                return contents;
            });
        });
    }

    private static int[] palette(ResourceManager manager, String name) throws IOException {
        var path = ResourceLocation.fromNamespaceAndPath("uwudimension", "textures/block/uwu_cave/" + name + ".png");
        var colors = new TreeSet<Integer>(Comparator.comparingInt(CaveFloraSprites::luminance).thenComparingInt(c -> c));
        try (var input = manager.open(path); var image = NativeImage.read(input)) {
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getPixelRGBA(x, y);
                if ((color >>> 24) != 0) colors.add(color & 0xFFFFFF);
            }
        }
        if (colors.isEmpty()) throw new IOException("Empty UwU cave palette: " + path);
        return colors.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void recolor(NativeImage image, Tone tone, int[] pink, int[] blue) {
        int low = 25500, high = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int color = image.getPixelRGBA(x, y);
            if ((color >>> 24) == 0) continue;
            low = Math.min(low, luminance(color));
            high = Math.max(high, luminance(color));
        }
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int color = image.getPixelRGBA(x, y);
            if ((color >>> 24) == 0) continue;
            int red = color & 255, green = color >>> 8 & 255, blueChannel = color >>> 16 & 255;
            boolean flower = tone == Tone.FLOWERS && red > green + 10 && blueChannel > green + 5;
            int[] shades = tone == Tone.PINK || flower ? pink : blue;
            int index = high == low ? shades.length / 2
                    : (luminance(color) - low) * (shades.length - 1) / (high - low);
            image.setPixelRGBA(x, y, (color & 0xFF000000) | shades[index]);
        }
    }

    // NativeImage uses ABGR integers on this Minecraft version.
    private static int luminance(int color) {
        return 21 * (color & 255) + 72 * (color >>> 8 & 255) + 7 * (color >>> 16 & 255);
    }
}
