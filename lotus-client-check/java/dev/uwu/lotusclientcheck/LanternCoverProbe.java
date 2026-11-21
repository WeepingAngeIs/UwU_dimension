package dev.uwu.lotusclientcheck;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.data.ModelData;

final class LanternCoverProbe {
    private static final ResourceLocation COVER = id("item/lantern_for_both_shores_cover");
    private static final ResourceLocation ORDINARY = ResourceLocation.withDefaultNamespace("item/written_book");
    private static final List<ItemDisplayContext> VIEWS = List.of(ItemDisplayContext.GUI,
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);

    private LanternCoverProbe() {}

    static void verify(Minecraft client) throws ReflectiveOperationException {
        var create = Class.forName("dev.uwu.dimension.LanternBookStacks")
                .getDeclaredMethod("create", net.minecraft.core.HolderLookup.Provider.class);
        create.setAccessible(true);
        ItemStack original = (ItemStack) create.invoke(null, client.level.registryAccess());
        ItemStack renamed = original.copy();
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("A reader's Lantern"));
        ItemStack signed = original.copy();
        signed.remove(DataComponents.CUSTOM_DATA);

        var renderer = client.getItemRenderer();
        var marked = renderer.getModel(original, client.level, client.player, 42);
        var renamedModel = renderer.getModel(renamed, client.level, client.player, 42);
        var ordinaryModel = renderer.getModel(signed, client.level, client.player, 42);
        var sprite = marked.getParticleIcon().contents();
        require(sprite.name().equals(COVER) && !marked.isGui3d(), "Marked signed book did not select the flat cover");
        require(renamedModel.getParticleIcon().contents().name().equals(COVER),
                "Renaming the book lost its marker-based model");
        require(ordinaryModel.getParticleIcon().contents().name().equals(ORDINARY),
                "Unmarked signed book selected " + ordinaryModel.getParticleIcon().contents().name());
        require(sprite.width() == 48 && sprite.height() == 48 && sprite.getUniqueFrames().count() == 16,
                "Atlas did not bake the sixteen 48-pixel animation frames");

        for (ItemDisplayContext view : VIEWS) {
            var specialDraw = new RecordedDraw();
            var ordinaryDraw = new RecordedDraw();
            boolean leftHand = view == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    || view == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            renderer.render(original, view, leftHand, new PoseStack(), specialDraw,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, marked);
            renderer.render(signed, view, leftHand, new PoseStack(), ordinaryDraw,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ordinaryModel);
            require(specialDraw.vertices() > 8 && ordinaryDraw.vertices() > 8 && specialDraw.parts.size() >= 2,
                    "Book or normal foil emitted no usable geometry for " + view);
        }

        var shelf = BuiltInRegistries.BLOCK.get(id("lantern_storybook"));
        for (String edition : List.of("petal", "dusk")) {
            BlockState state = shelf.getStateDefinition().getPossibleStates().stream()
                    .filter(candidate -> candidate.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST
                            && candidate.toString().contains("edition=" + edition))
                    .findFirst().orElseThrow();
            var model = client.getBlockRenderer().getBlockModel(state);
            require(model.getParticleIcon().contents().name().equals(id("block/lantern_storybook_" + edition + "_binding")),
                    "Wrong shelf binding for " + edition);
            var quads = model.getQuads(state, null, RandomSource.create(42), ModelData.EMPTY, null);
            require(quads.stream().anyMatch(quad -> quad.getSprite().contents().name().equals(id("block/lantern_storybook_" + edition))),
                    "Missing bound book geometry for " + edition);
            var coverQuads = quads.stream().filter(quad -> quad.getSprite().contents().name().equals(COVER)).toList();
            require(coverQuads.size() == 4 && coverQuads.stream().allMatch(LanternCoverProbe::insideSprite),
                    "The native " + edition + " display did not bake four in-atlas cover slices on its page");
            var draw = new RecordedDraw();
            client.getBlockRenderer().renderSingleBlock(state, new PoseStack(), draw,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            require(draw.vertices() > 8, "Native block renderer skipped the " + edition + " shelf book");
        }

        System.out.println("LANTERN COVER CLIENT PASS marked, renamed and ordinary signed books;"
                + " animated 48x48 sprite, 16 frames, five foil draw contexts, Petal and Dusk shelf bindings with frilled cover art");
    }

    private static boolean insideSprite(BakedQuad quad) {
        var sprite = quad.getSprite();
        int[] vertices = quad.getVertices();
        var layout = DefaultVertexFormat.BLOCK;
        int stride = layout.getVertexSize() / Integer.BYTES;
        int uv = layout.getOffset(VertexFormatElement.UV0) / Integer.BYTES;
        for (int i = 0; i < vertices.length; i += stride) {
            float u = Float.intBitsToFloat(vertices[i + uv]);
            float v = Float.intBitsToFloat(vertices[i + uv + 1]);
            if (!(u >= sprite.getU0() && u <= sprite.getU1() && v >= sprite.getV0() && v <= sprite.getV1())) {
                return false;
            }
        }
        return vertices.length == 4 * stride;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension", path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordedDraw implements MultiBufferSource {
        private final Map<RenderType, VertexCounter> parts = new HashMap<>();

        @Override public VertexConsumer getBuffer(RenderType type) {
            return parts.computeIfAbsent(type, ignored -> new VertexCounter());
        }

        int vertices() {
            return parts.values().stream().mapToInt(part -> part.count).sum();
        }
    }

    private static final class VertexCounter implements VertexConsumer {
        private int count;

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            require(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z), "Invalid baked draw position");
            count++;
            return this;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
