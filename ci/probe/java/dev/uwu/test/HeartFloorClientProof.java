package dev.uwu.test;

import com.mojang.blaze3d.platform.NativeImage;
import dev.uwu.dimension.HeartFloor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

final class HeartFloorClientProof {
    static void verify(Minecraft client) {
        var original = ResourceLocation.fromNamespaceAndPath("uwudimension", "textures/block/heartsill.png");
        try (var stream = client.getResourceManager().open(original)) {
            var bytes = stream.readAllBytes();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!hash.equals("77ad1e05563a7c47a9918b9959dcb0606ad3f2a0cc301516197b314316a4f725"))
                throw new AssertionError("The v1.0 Heartsill texture changed");
            try (var reference = NativeImage.read(new ByteArrayInputStream(bytes))) {
                checkFamilies(client, reference);
            }
        } catch (IOException | NoSuchAlgorithmException failure) { throw new AssertionError("Heartsill resources", failure); }
        System.out.println("HEARTSILL CLIENT PASS | original v1 pixel file intact; 16 heart palettes; all creative entries, item models, carpet weaves and slab models present");
    }

    private static void checkFamilies(Minecraft client, NativeImage original) throws IOException {
        CreativeModeTabs.tryRebuildTabContents(client.level.enabledFeatures(), false, client.level.registryAccess());
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.COLORED_BLOCKS);
        var missing = client.getModelManager().getMissingModel();
        Set<Integer> palettes = new HashSet<>();
        for (DyeColor color : DyeColor.values()) {
            var full = HeartFloor.full(color);
            var slab = HeartFloor.slab(color);
            var rug = HeartFloor.carpet(color);
            for (var block : List.of(full, slab, rug)) {
                var stack = new ItemStack(block);
                if (!tab.contains(stack) || client.getItemRenderer().getItemModelShaper().getItemModel(stack) == missing)
                    throw new AssertionError("Missing creative entry/item model " + block);
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    var model = client.getBlockRenderer().getBlockModel(state);
                    var texture = model.getParticleIcon().contents().name();
                    var expected = BuiltInRegistries.BLOCK.getKey(block == slab ? full : block).withPrefix("block/");
                    if (model == missing || !texture.equals(expected)) throw new AssertionError("Missing appearance for " + state);
                }
            }
            var stone = BuiltInRegistries.BLOCK.getKey(full).withPrefix("textures/block/").withSuffix(".png");
            var weave = BuiltInRegistries.BLOCK.getKey(rug).withPrefix("textures/block/").withSuffix(".png");
            try (var stoneStream = client.getResourceManager().open(stone); var textileStream = client.getResourceManager().open(weave);
                    var tile = NativeImage.read(stoneStream); var carpet = NativeImage.read(textileStream)) {
                int heart = tile.getPixelRGBA(7,8);
                if (tile.getWidth()!=16 || tile.getHeight()!=16 || carpet.getWidth()!=16 || carpet.getHeight()!=16 || !palettes.add(heart))
                    throw new AssertionError("Missing palette " + color);
                int textured = 0;
                for (int y=0; y<16; y++) for (int x=0; x<16; x++) {
                    boolean motif = original.getPixelRGBA(x,y) == original.getPixelRGBA(7,8);
                    if (carpet.getPixelRGBA(x,y) != tile.getPixelRGBA(x,y)) textured++;
                    if ((tile.getPixelRGBA(x,y) == heart) != motif)
                        throw new AssertionError("Heart silhouette or textile weave: " + color + " " + x + "," + y);
                }
                if (textured < 250) throw new AssertionError("Missing woven finish: " + color);
            }
        }
    }
}
