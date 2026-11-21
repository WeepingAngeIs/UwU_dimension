package dev.uwu.lotusclientcheck;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Checks actual baked states and sprite pixels, without substituting a fake renderer. */
final class CaveFloraProbe {
    private record Plant(Block block, Set<String> textures) {}
    private record Sprites(TextureAtlasSprite original, TextureAtlasSprite cave) {}
    // Explicit inventory from the 1.21.1 placed/configured features, independent of production maps.
    private static final List<Plant> PLANTS = List.of(
            plant(Blocks.BIG_DRIPLEAF, "big_dripleaf_top", "big_dripleaf_side", "big_dripleaf_tip", "big_dripleaf_stem"),
            plant(Blocks.BIG_DRIPLEAF_STEM, "big_dripleaf_stem"),
            plant(Blocks.SMALL_DRIPLEAF, "small_dripleaf_top", "small_dripleaf_side", "small_dripleaf_stem_top",
                    "small_dripleaf_stem_bottom", "big_dripleaf_stem"),
            plant(Blocks.AZALEA, "azalea_top", "azalea_side", "azalea_plant"),
            plant(Blocks.FLOWERING_AZALEA, "flowering_azalea_top", "flowering_azalea_side", "azalea_plant"),
            plant(Blocks.AZALEA_LEAVES, "azalea_leaves"),
            plant(Blocks.FLOWERING_AZALEA_LEAVES, "flowering_azalea_leaves"),
            plant(Blocks.SPORE_BLOSSOM, "spore_blossom", "spore_blossom_base"),
            plant(Blocks.GLOW_LICHEN, "glow_lichen"),
            plant(Blocks.HANGING_ROOTS, "hanging_roots"),
            plant(Blocks.VINE, "vine"),
            plant(Blocks.OAK_LOG, "oak_log", "oak_log_top"),
            plant(Blocks.ROOTED_DIRT, "rooted_dirt"));
    private final Set<String> scenes = new HashSet<>();
    private final Map<Block, ModelData> previousCaveData = new HashMap<>();
    private final Map<String, Sprites> checkedSprites = new LinkedHashMap<>();
    private int themedScenes;
    private int ordinaryScenes;
    private int statesChecked;

    private static Plant plant(Block block, String... textures) {
        return new Plant(block, Set.of(textures));
    }

    void check(Minecraft client, BlockPos pos, ResourceKey<Biome> biome, boolean themed) {
        String scene = client.level.dimension().location() + "/" + biome.location();
        if (!scenes.add(scene)) return;
        require(client.level.getBiome(pos).is(biome), "Flora sample biome is not synchronized");
        for (Plant plant : PLANTS) {
            var seen = new HashSet<String>();
            for (var state : plant.block.getStateDefinition().getPossibleStates()) {
                statesChecked++;
                var model = client.getBlockRenderer().getBlockModel(state);
                // Also feed previously themed data into outside lookups to expose palette leakage.
                var data = model.getModelData(client.level, pos, state,
                        previousCaveData.getOrDefault(plant.block, ModelData.EMPTY));
                if (themed) previousCaveData.put(plant.block, data);
                var faces = new ArrayList<Direction>(Arrays.asList(Direction.values()));
                faces.add(null);
                for (Direction side : faces) {
                    var original = model.getQuads(state, side, RandomSource.create(137), ModelData.EMPTY, null);
                    var actual = model.getQuads(state, side, RandomSource.create(137), data, null);
                    require(original.size() == actual.size(), "Flora changed quad count: " + state);
                    for (int i = 0; i < original.size(); i++) {
                        BakedQuad before = original.get(i), after = actual.get(i);
                        checkSprite(client, plant, before.getSprite(), after.getSprite(), themed, seen);
                        require(before.getDirection() == after.getDirection() && before.isShade() == after.isShade()
                                        && before.hasAmbientOcclusion() == after.hasAmbientOcclusion(),
                                "Flora changed face orientation/shading: " + state);
                        require(after.getTintIndex() == (themed ? -1 : before.getTintIndex()),
                                "Flora retained green biome tint or altered outside tint: " + state);
                        int[] a = before.getVertices(), b = after.getVertices();
                        require(a.length == b.length, "Flora changed vertex layout");
                        for (int j = 0; j < a.length; j++) {
                            int field = j % IQuadTransformer.STRIDE;
                            if (themed && (field == IQuadTransformer.UV0 || field == IQuadTransformer.UV0 + 1)) continue;
                            require(a[j] == b[j], "Flora changed geometry/color/light outside UV: " + state + " at " + j);
                        }
                    }
                }
                checkSprite(client, plant, model.getParticleIcon(ModelData.EMPTY), model.getParticleIcon(data), themed, seen);
            }
            require(seen.equals(plant.textures), "Incomplete baked texture coverage for " + plant.block + ": " + seen);
        }
        if (themed) themedScenes++; else ordinaryScenes++;
        System.out.println("CAVE FLORA SCENE " + scene + " themed=" + themed + " plants=" + PLANTS.size());
    }

    private void checkSprite(Minecraft client, Plant plant, TextureAtlasSprite original, TextureAtlasSprite actual,
                             boolean themed, Set<String> seen) {
        String source = original.contents().name().toString();
        require(source.startsWith("minecraft:block/"), "Unexpected original flora sprite: " + source);
        String name = source.substring("minecraft:block/".length());
        require(plant.textures.contains(name), "Inventory missed a texture in " + plant.block + ": " + name);
        seen.add(name);
        String expected = themed ? "uwudimension:block/uwu_cave/flora/" + name : source;
        require(expected.equals(actual.contents().name().toString()), "Incorrect flora sprite: " + expected
                + " != " + actual.contents().name());
        if (!themed) {
            require(original == actual, "Outside terrain did not retain its original sprite");
            return;
        }
        if (checkedSprites.putIfAbsent(name, new Sprites(original, actual)) != null) return;
        var a = original.contents().getOriginalImage();
        var b = actual.contents().getOriginalImage();
        require(a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight(), "Plant dimensions changed: " + name);
        require(original.contents().width() == actual.contents().width()
                && original.contents().height() == actual.contents().height(), "Animation frame dimensions changed: " + name);
        Set<Integer> palette = new HashSet<>();
        for (String reference : List.of("moss_block", "grass_block_top")) {
            var tile = client.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.fromNamespaceAndPath(
                    "uwudimension", "block/uwu_cave/" + reference)).contents().getOriginalImage();
            for (int y = 0; y < tile.getHeight(); y++) for (int x = 0; x < tile.getWidth(); x++) {
                int c = tile.getPixelRGBA(x, y);
                if ((c >>> 24) != 0) palette.add(c & 0xFFFFFF);
            }
        }
        int changed = 0;
        for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) {
            int before = a.getPixelRGBA(x, y), after = b.getPixelRGBA(x, y);
            require((before >>> 24) == (after >>> 24), "Plant alpha/silhouette changed: " + name);
            if ((after >>> 24) == 0) continue;
            require(palette.contains(after & 0xFFFFFF), "Plant color is outside the existing cave palette: " + name);
            if (before != after) changed++;
        }
        require(changed > 0, "Plant remained vanilla colored: " + name);
    }

    void finish() {
        require(themedScenes == 2 && ordinaryScenes == 3,
                "Missing cave or outside boundary control: themed=" + themedScenes + ", outside=" + ordinaryScenes);
        require(checkedSprites.size() == 23, "Incomplete generated sprite inventory: " + checkedSprites.keySet());
        writePaletteSheet();
        System.out.println("CAVE FLORA PALETTE PASS states=" + statesChecked + " sprites=" + checkedSprites.size()
                + "; both cave biomes, three outside controls, alpha, geometry, all dripleaf states and stale model data");
    }

    private void writePaletteSheet() {
        // A CI review artifact, produced only by the hosted client fixture. Left=vanilla, right=UwU.
        int columns = 4, cellWidth = 136, cellHeight = 72;
        int rows = (checkedSprites.size() + columns - 1) / columns;
        var names = new StringBuilder("Each pair: vanilla on left, scoped UwU on right.\n");
        try (var sheet = new NativeImage(columns * cellWidth, rows * cellHeight, true)) {
            int index = 0;
            for (var entry : checkedSprites.entrySet()) {
                int startX = index % columns * cellWidth, startY = index / columns * cellHeight;
                names.append("row ").append(index / columns + 1).append(" column ").append(index % columns + 1)
                        .append(": ").append(entry.getKey()).append('\n');
                for (int side = 0; side < 2; side++) {
                    NativeImage source = (side == 0 ? entry.getValue().original : entry.getValue().cave).contents().getOriginalImage();
                    for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                        int pixel = source.getPixelRGBA(x * source.getWidth() / 64, y * source.getHeight() / 64);
                        sheet.setPixelRGBA(startX + side * 64 + x, startY + y, pixel);
                    }
                }
                index++;
            }
            sheet.writeToFile(Path.of("cave-flora-palette.png"));
            Files.writeString(Path.of("cave-flora-palette.txt"), names);
        } catch (IOException failure) {
            throw new AssertionError("Could not write cave palette review artifact", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
