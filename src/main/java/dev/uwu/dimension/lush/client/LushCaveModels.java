package dev.uwu.dimension.lush.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.SakuraMaterials;
import java.util.ArrayList;
import java.util.HashMap;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
    private static final ModelProperty<Palette> PALETTE = new ModelProperty<>();

    private LushCaveModels() {}

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        var atlas = event.getTextureGetter();
        vines(event.getModels(), atlas, Blocks.CAVE_VINES, "cave_vines");
        vines(event.getModels(), atlas, Blocks.CAVE_VINES_PLANT, "cave_vines_plant");
        var moss = caveTexture(atlas, "minecraft:block/moss_block", "moss_block", false);
        terrain(event, Blocks.MOSS_BLOCK, moss);
        terrain(event, Blocks.MOSS_CARPET, moss);
        terrain(event, Blocks.SHORT_GRASS, caveTexture(atlas, "minecraft:block/short_grass", "short_grass", true));
        terrain(event, Blocks.TALL_GRASS,
                caveTexture(atlas, "minecraft:block/tall_grass_bottom", "tall_grass_bottom", true),
                caveTexture(atlas, "minecraft:block/tall_grass_top", "tall_grass_top", true));
        terrain(event, Blocks.GRASS_BLOCK,
                caveTexture(atlas, "minecraft:block/grass_block_top", "grass_block_top", true),
                caveTexture(atlas, "minecraft:block/grass_block_side", "grass_block_side", true),
                caveTexture(atlas, "minecraft:block/grass_block_side_overlay", "grass_block_side_overlay", true));
        terrain(event, SakuraMaterials.GRASS.get(),
                caveTexture(atlas, "uwudimension:block/sakura_grass_top", "grass_block_top", true),
                caveTexture(atlas, "uwudimension:block/sakura_grass_overlay", "grass_block_side_overlay", true));
        // Inventory of the Reach's vanilla lush-cave and lantern-grotto feature outputs.
        // Cover every texture used by every state, including tilted leaves, both small
        // dripleaf halves and azalea's inherited central plant texture.
        flora(event, Blocks.BIG_DRIPLEAF, "big_dripleaf_top", "big_dripleaf_side", "big_dripleaf_tip", "big_dripleaf_stem");
        flora(event, Blocks.BIG_DRIPLEAF_STEM, "big_dripleaf_stem");
        flora(event, Blocks.SMALL_DRIPLEAF, "small_dripleaf_top", "small_dripleaf_side", "small_dripleaf_stem_top",
                "small_dripleaf_stem_bottom", "big_dripleaf_stem");
        flora(event, Blocks.AZALEA, "azalea_top", "azalea_side", "azalea_plant");
        flora(event, Blocks.FLOWERING_AZALEA, "flowering_azalea_top", "flowering_azalea_side", "azalea_plant");
        flora(event, Blocks.AZALEA_LEAVES, "azalea_leaves");
        flora(event, Blocks.FLOWERING_AZALEA_LEAVES, "flowering_azalea_leaves");
        flora(event, Blocks.SPORE_BLOSSOM, "spore_blossom", "spore_blossom_base");
        flora(event, Blocks.GLOW_LICHEN, "glow_lichen");
        flora(event, Blocks.HANGING_ROOTS, "hanging_roots");
        flora(event, Blocks.VINE, "vine");
        flora(event, Blocks.OAK_LOG, "oak_log", "oak_log_top");
        flora(event, Blocks.ROOTED_DIRT, "rooted_dirt");
        geode(event, Blocks.SMOOTH_BASALT, "smooth_basalt", "outer_shell");
        geode(event, Blocks.CALCITE, "calcite", "middle_shell");
        geode(event, Blocks.AMETHYST_BLOCK, "amethyst_block", "inner_lining");
        geode(event, Blocks.BUDDING_AMETHYST, "budding_amethyst", "budding_lining");
        geode(event, Blocks.SMALL_AMETHYST_BUD, "small_amethyst_bud", "small_bud");
        geode(event, Blocks.MEDIUM_AMETHYST_BUD, "medium_amethyst_bud", "medium_bud");
        geode(event, Blocks.LARGE_AMETHYST_BUD, "large_amethyst_bud", "large_bud");
        geode(event, Blocks.AMETHYST_CLUSTER, "amethyst_cluster", "cluster");
    }

    private static void flora(ModelEvent.ModifyBakingResult event, Block owner, String... textures) {
        Surface[] faces = new Surface[textures.length];
        for (int i = 0; i < textures.length; i++) {
            faces[i] = caveTexture(event.getTextureGetter(), "minecraft:block/" + textures[i],
                    "flora/" + textures[i], true);
        }
        terrain(event, owner, faces);
    }

    private static void vines(Map<ModelResourceLocation, BakedModel> models, Function<Material, TextureAtlasSprite> atlas,
                              Block owner, String name) {
        Palette bare = Palette.of(surface(atlas, name, name, false));
        Palette fruit = Palette.of(surface(atlas, name + "_lit", name + "_lit", true));
        for (BlockState state : owner.getStateDefinition().getPossibleStates()) {
            Palette selected = state.getValue(CaveVines.BERRIES) ? fruit : bare;
            if (selected != null) {
                models.computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                        (key, previous) -> new ScopedModel(previous, owner, selected, null));
            }
        }
    }

    private static void terrain(ModelEvent.ModifyBakingResult event, Block owner, Surface... faces) {
        Palette palette = Palette.of(faces);
        if (palette == null) return;
        for (BlockState state : owner.getStateDefinition().getPossibleStates()) {
            if (state.hasProperty(BlockStateProperties.SNOWY) && state.getValue(BlockStateProperties.SNOWY)) continue;
            event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                    (key, previous) -> new ScopedModel(previous, owner, palette, null));
        }
    }

    private static @Nullable Surface caveTexture(Function<Material, TextureAtlasSprite> atlas, String original,
                                                 String target, boolean untinted) {
        var tile = sprite(atlas, target);
        return tile == null ? null : new Surface(ResourceLocation.parse(original), tile, tile, null, untinted);
    }

    private static void geode(ModelEvent.ModifyBakingResult event, Block owner, String original, String role) {
        Palette pink = Palette.of(surface(event.getTextureGetter(), original, "geode/pink_" + role, false));
        Palette silver = Palette.of(surface(event.getTextureGetter(), original, "geode/silver_" + role, false));
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
        return new Surface(ResourceLocation.withDefaultNamespace("block/" + original), particle, base, glow, false);
    }

    private static @Nullable TextureAtlasSprite sprite(Function<Material, TextureAtlasSprite> atlas, String name) {
        var id = ResourceLocation.fromNamespaceAndPath("uwudimension", "block/uwu_cave/" + name);
        var sprite = atlas.apply(new Material(TextureAtlas.LOCATION_BLOCKS, id));
        return sprite != null && id.equals(sprite.contents().name()) ? sprite : null;
    }

    private static final class ScopedModel extends BakedModelWrapper<BakedModel> {
        private final Block owner;
        private final Palette primary;
        private final @Nullable Palette silver;

        ScopedModel(BakedModel previous, Block owner, Palette primary, @Nullable Palette silver) {
            super(previous);
            this.owner = owner;
            this.primary = primary;
            this.silver = silver;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter view, BlockPos pos, BlockState state, ModelData input) {
            ModelData inherited = super.getModelData(view, pos, state, input);
            // A chained renderer may feed its previous result back into a new block or region lookup.
            if (inherited.get(PALETTE) != null) inherited = inherited.derive().with(PALETTE, null).build();
            var level = LushRenderView.owner(view);
            if (level == null || !level.dimension().equals(ReefLife.REACH) || !state.is(owner)) return inherited;
            Palette chosen;
            if (silver == null) {
                if (!LushRenderView.isCaveBiome(LushRenderView.biome(view, level, pos))) return inherited;
                chosen = primary;
            } else {
                // The origin biome belongs to the recorded feature. Its footprint may cross a biome or chunk edge.
                String theme = GeodeClientIndex.theme(level, pos, state);
                if ("pink".equals(theme)) chosen = primary;
                else if ("silver".equals(theme)) chosen = silver;
                else return inherited;
            }
            return inherited == ModelData.EMPTY ? chosen.data : inherited.derive().with(PALETTE, chosen).build();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                        ModelData data, @Nullable RenderType layer) {
            List<BakedQuad> quads = super.getQuads(state, side, random, data, layer);
            Palette palette = data.get(PALETTE);
            if (state == null || !state.is(owner) || palette == null
                    || (palette != primary && palette != silver) || quads.isEmpty()) return quads;
            ArrayList<BakedQuad> result = null;
            for (int i = 0; i < quads.size(); i++) {
                BakedQuad quad = quads.get(i);
                Surface surface = palette.surface(quad.getSprite());
                if (surface != null) {
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
            Palette palette = data.get(PALETTE);
            if (palette == null || (palette != primary && palette != silver)) return original;
            Surface surface = palette.surface(original);
            return surface == null ? original : surface.particle;
        }
    }

    private static final class Palette {
        private final Map<ResourceLocation, Surface> faces;
        private final ModelData data;

        private Palette(Map<ResourceLocation, Surface> faces) {
            this.faces = Map.copyOf(faces);
            this.data = ModelData.of(PALETTE, this);
        }

        static @Nullable Palette of(Surface... surfaces) {
            var faces = new HashMap<ResourceLocation, Surface>();
            for (Surface surface : surfaces) {
                if (surface == null) return null;
                faces.put(surface.original, surface);
            }
            return new Palette(faces);
        }

        @Nullable Surface surface(TextureAtlasSprite original) {
            if (!TextureAtlas.LOCATION_BLOCKS.equals(original.atlasLocation())) return null;
            return faces.get(original.contents().name());
        }
    }

    private static final class Surface {
        final ResourceLocation original;
        final TextureAtlasSprite particle;
        final TextureAtlasSprite base;
        final @Nullable TextureAtlasSprite glow;
        final boolean untinted;
        // Bake-lifetime, thread-safe, identity keyed. A dynamic resource-pack model cannot grow this without bound.
        private final Cache<BakedQuad, List<BakedQuad>> cache = CacheBuilder.newBuilder().weakKeys().maximumSize(512).build();

        Surface(ResourceLocation original, TextureAtlasSprite particle, TextureAtlasSprite base,
                @Nullable TextureAtlasSprite glow, boolean untinted) {
            this.original = original;
            this.particle = particle;
            this.base = base;
            this.glow = glow;
            this.untinted = untinted;
        }

        List<BakedQuad> replacements(BakedQuad quad) {
            List<BakedQuad> hit = cache.getIfPresent(quad);
            if (hit != null) return hit;
            int tint = untinted ? -1 : quad.getTintIndex();
            BakedQuad diffuse = remap(quad, base, false, tint);
            List<BakedQuad> made = glow == null ? List.of(diffuse) : List.of(diffuse, remap(quad, glow, true, tint));
            cache.put(quad, made);
            return made;
        }
    }

    private static BakedQuad remap(BakedQuad quad, TextureAtlasSprite target, boolean glow, int tint) {
        int[] vertices = quad.getVertices().clone();
        TextureAtlasSprite from = quad.getSprite();
        for (int v = 0; v < 4; v++) {
            int vertex = v * IQuadTransformer.STRIDE;
            int u = vertex + IQuadTransformer.UV0;
            vertices[u] = Float.floatToRawIntBits(target.getU(from.getUOffset(Float.intBitsToFloat(vertices[u]))));
            vertices[u + 1] = Float.floatToRawIntBits(target.getV(from.getVOffset(Float.intBitsToFloat(vertices[u + 1]))));
            if (glow) vertices[vertex + IQuadTransformer.UV2] = LightTexture.FULL_BRIGHT;
        }
        return new BakedQuad(vertices, tint, quad.getDirection(), target,
                !glow && quad.isShade(), !glow && quad.hasAmbientOcclusion());
    }
}
