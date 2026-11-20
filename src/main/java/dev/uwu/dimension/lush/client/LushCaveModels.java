package dev.uwu.dimension.lush.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.uwu.dimension.ReefLife;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LushCaveModels {
    private static final ResourceKey<Biome> GROTTO = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("uwudimension", "lantern_grotto"));
    private static final ModelProperty<Surface> SURFACE = new ModelProperty<>();

    private LushCaveModels() {}

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        var atlas = event.getTextureGetter();
        vines(event.getModels(), atlas, Blocks.CAVE_VINES, "cave_vines");
        vines(event.getModels(), atlas, Blocks.CAVE_VINES_PLANT, "cave_vines_plant");
        geode(event, Blocks.SMOOTH_BASALT, "smooth_basalt", "outer_shell");
        geode(event, Blocks.CALCITE, "calcite", "middle_shell");
        geode(event, Blocks.AMETHYST_BLOCK, "amethyst_block", "inner_lining");
        geode(event, Blocks.BUDDING_AMETHYST, "budding_amethyst", "budding_lining");
        geode(event, Blocks.SMALL_AMETHYST_BUD, "small_amethyst_bud", "small_bud");
        geode(event, Blocks.MEDIUM_AMETHYST_BUD, "medium_amethyst_bud", "medium_bud");
        geode(event, Blocks.LARGE_AMETHYST_BUD, "large_amethyst_bud", "large_bud");
        geode(event, Blocks.AMETHYST_CLUSTER, "amethyst_cluster", "cluster");
    }

    private static void vines(Map<ModelResourceLocation, BakedModel> models, Function<Material, TextureAtlasSprite> atlas,
                              Block owner, String name) {
        Surface bare = surface(atlas, name, name, false);
        Surface fruit = surface(atlas, name + "_lit", name + "_lit", true);
        for (BlockState state : owner.getStateDefinition().getPossibleStates()) {
            Surface selected = state.getValue(CaveVines.BERRIES) ? fruit : bare;
            if (selected != null) {
                models.computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                        (key, previous) -> new ScopedModel(previous, owner, selected, null));
            }
        }
    }

    private static void geode(ModelEvent.ModifyBakingResult event, Block owner, String original, String role) {
        Surface pink = surface(event.getTextureGetter(), original, "geode/pink_" + role, false);
        Surface silver = surface(event.getTextureGetter(), original, "geode/silver_" + role, false);
        if (pink == null || silver == null) return;
        for (BlockState state : owner.getStateDefinition().getPossibleStates()) {
            event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                    (key, previous) -> new ScopedModel(previous, owner, pink, silver));
        }
    }

    private static @Nullable Surface surface(Function<Material, TextureAtlasSprite> atlas, String original, String name, boolean fruit) {
        TextureAtlasSprite particle = sprite(atlas, name);
        TextureAtlasSprite base = fruit ? sprite(atlas, name + "_foliage") : particle;
        TextureAtlasSprite glow = fruit ? sprite(atlas, name + "_glow") : null;
        if (particle == null || base == null || fruit && glow == null) return null;
        return new Surface(ResourceLocation.withDefaultNamespace("block/" + original), particle, base, glow);
    }

    private static @Nullable TextureAtlasSprite sprite(Function<Material, TextureAtlasSprite> atlas, String name) {
        var id = ResourceLocation.fromNamespaceAndPath("uwudimension", "block/uwu_cave/" + name);
        var sprite = atlas.apply(new Material(TextureAtlas.LOCATION_BLOCKS, id));
        return sprite != null && id.equals(sprite.contents().name()) ? sprite : null;
    }

    private static final class ScopedModel extends BakedModelWrapper<BakedModel> {
        private final Block owner;
        private final Surface primary;
        private final @Nullable Surface silver;

        ScopedModel(BakedModel previous, Block owner, Surface primary, @Nullable Surface silver) {
            super(previous);
            this.owner = owner;
            this.primary = primary;
            this.silver = silver;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter view, BlockPos pos, BlockState state, ModelData input) {
            ModelData inherited = super.getModelData(view, pos, state, input);
            // A chained renderer may feed its previous result back into a new block or region lookup.
            if (inherited.get(SURFACE) != null) inherited = inherited.derive().with(SURFACE, null).build();
            var level = LushRenderView.owner(view);
            if (level == null || !level.dimension().equals(ReefLife.REACH) || !state.is(owner)) return inherited;
            Surface chosen;
            if (silver == null) {
                var biome = LushRenderView.biome(view, level, pos);
                if (!biome.is(Biomes.LUSH_CAVES) && !biome.is(GROTTO)) return inherited;
                chosen = primary;
            } else {
                // The origin biome belongs to the recorded feature. Its footprint may cross a biome or chunk edge.
                String theme = GeodeClientIndex.theme(level, pos, state);
                if ("pink".equals(theme)) chosen = primary;
                else if ("silver".equals(theme)) chosen = silver;
                else return inherited;
            }
            return inherited == ModelData.EMPTY ? chosen.onlyData() : inherited.derive().with(SURFACE, chosen).build();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                        ModelData data, @Nullable RenderType layer) {
            List<BakedQuad> quads = super.getQuads(state, side, random, data, layer);
            Surface surface = data.get(SURFACE);
            if (state == null || !state.is(owner) || surface == null
                    || (surface != primary && surface != silver) || quads.isEmpty()) return quads;
            ArrayList<BakedQuad> result = null;
            for (int i = 0; i < quads.size(); i++) {
                BakedQuad quad = quads.get(i);
                if (surface.matches(quad.getSprite())) {
                    if (result == null) {
                        result = new ArrayList<>(quads.size() + 1);
                        result.addAll(quads.subList(0, i));
                    }
                    result.addAll(surface.replacements(quad));
                } else if (result != null) {
                    result.add(quad);
                }
            }
            return result == null ? quads : result;
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            TextureAtlasSprite original = super.getParticleIcon(data);
            Surface surface = data.get(SURFACE);
            return surface != null && (surface == primary || surface == silver) && surface.matches(original) ? surface.particle : original;
        }
    }

    private static final class Surface {
        final ResourceLocation original;
        final TextureAtlasSprite particle;
        final TextureAtlasSprite base;
        final @Nullable TextureAtlasSprite glow;
        private final ModelData data;
        // Bake-lifetime, thread-safe, identity keyed. A dynamic resource-pack model cannot grow this without bound.
        private final Cache<BakedQuad, List<BakedQuad>> cache = CacheBuilder.newBuilder().weakKeys().maximumSize(512).build();

        Surface(ResourceLocation original, TextureAtlasSprite particle, TextureAtlasSprite base, @Nullable TextureAtlasSprite glow) {
            this.original = original;
            this.particle = particle;
            this.base = base;
            this.glow = glow;
            this.data = ModelData.of(SURFACE, this);
        }

        ModelData onlyData() { return data; }

        boolean matches(TextureAtlasSprite sprite) {
            return TextureAtlas.LOCATION_BLOCKS.equals(sprite.atlasLocation()) && original.equals(sprite.contents().name());
        }

        List<BakedQuad> replacements(BakedQuad quad) {
            List<BakedQuad> hit = cache.getIfPresent(quad);
            if (hit != null) return hit;
            BakedQuad diffuse = remap(quad, base, false);
            List<BakedQuad> made = glow == null ? List.of(diffuse) : List.of(diffuse, remap(quad, glow, true));
            cache.put(quad, made);
            return made;
        }
    }

    private static BakedQuad remap(BakedQuad quad, TextureAtlasSprite target, boolean glow) {
        int[] vertices = quad.getVertices().clone();
        TextureAtlasSprite from = quad.getSprite();
        for (int v = 0; v < 4; v++) {
            int vertex = v * IQuadTransformer.STRIDE;
            int u = vertex + IQuadTransformer.UV0;
            vertices[u] = Float.floatToRawIntBits(target.getU(from.getUOffset(Float.intBitsToFloat(vertices[u]))));
            vertices[u + 1] = Float.floatToRawIntBits(target.getV(from.getVOffset(Float.intBitsToFloat(vertices[u + 1]))));
            if (glow) vertices[vertex + IQuadTransformer.UV2] = LightTexture.FULL_BRIGHT;
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), target,
                !glow && quad.isShade(), !glow && quad.hasAmbientOcclusion());
    }
}
