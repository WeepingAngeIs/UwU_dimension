package dev.uwu.dimension.client;

import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.lush.client.LushRenderView;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;

/** Terrain-mesh materials. The owning chunk region, including a Sodium slice, supplies the level. */
@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ReachLiquidArt {
    private static final ResourceLocation WATER_STILL = vanilla("water_still");
    private static final ResourceLocation WATER_FLOW = vanilla("water_flow");
    private static final ResourceLocation WATER_OVERLAY = vanilla("water_overlay");
    private static final ResourceLocation LAVA_STILL = vanilla("lava_still");
    private static final ResourceLocation LAVA_FLOW = vanilla("lava_flow");
    private static final ResourceLocation REACH_STILL = reach(WATER_STILL);
    private static final ResourceLocation REACH_FLOW = reach(WATER_FLOW);
    private static final ResourceLocation REACH_OVERLAY = reach(WATER_OVERLAY);
    private static volatile Palette palette = new Palette(null, null);

    private ReachLiquidArt() {}

    private record Palette(@Nullable Family water, @Nullable Family lava) {}

    private record Family(TextureAtlasSprite originalStill, TextureAtlasSprite originalFlow,
                          @Nullable TextureAtlasSprite originalOverlay, TextureAtlasSprite still,
                          TextureAtlasSprite flow, @Nullable TextureAtlasSprite overlay) {
        private boolean matches(TextureAtlasSprite[] original) {
            return original.length == 3 && original[0] == originalStill && original[1] == originalFlow
                    && original[2] == originalOverlay;
        }
    }

    private static ResourceLocation vanilla(String name) {
        return ResourceLocation.withDefaultNamespace("block/" + name);
    }

    private static ResourceLocation reach(ResourceLocation original) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension",
                "block/uwu_cave/" + original.getPath().substring("block/".length()));
    }

    private static @Nullable TextureAtlasSprite sprite(TextureAtlas atlas, ResourceLocation name) {
        TextureAtlasSprite result = atlas.getSprite(name);
        return result != null && name.equals(result.contents().name()) ? result : null;
    }

    private static @Nullable Family family(TextureAtlas atlas, ResourceLocation stillId, ResourceLocation flowId,
                                           @Nullable ResourceLocation overlayId) {
        var originalStill = sprite(atlas, stillId);
        var originalFlow = sprite(atlas, flowId);
        var originalOverlay = overlayId == null ? null : sprite(atlas, overlayId);
        var still = sprite(atlas, reach(stillId));
        var flow = sprite(atlas, reach(flowId));
        var overlay = overlayId == null ? null : sprite(atlas, reach(overlayId));
        if (originalStill == null || originalFlow == null || still == null || flow == null
                || overlayId != null && (originalOverlay == null || overlay == null)) return null;
        return new Family(originalStill, originalFlow, originalOverlay, still, flow, overlay);
    }

    @SubscribeEvent
    public static void atlasReloaded(TextureAtlasStitchedEvent event) {
        var atlas = event.getAtlas();
        if (!TextureAtlas.LOCATION_BLOCKS.equals(atlas.location())) return;
        palette = new Palette(family(atlas, WATER_STILL, WATER_FLOW, WATER_OVERLAY),
                family(atlas, LAVA_STILL, LAVA_FLOW, null));
    }

    private static boolean reachMesh(BlockAndTintGetter view) {
        if (!(view instanceof LushRenderView region)) return false;
        var owner = region.uwu$lushOwner();
        return owner != null && ReefLife.REACH.equals(owner.dimension());
    }

    public static boolean water(FluidState state) {
        var type = state.getType();
        return type == Fluids.WATER || type == Fluids.FLOWING_WATER;
    }

    public static TextureAtlasSprite[] sprites(TextureAtlasSprite[] original, BlockAndTintGetter view, FluidState state) {
        if (!reachMesh(view)) return original;
        var current = palette;
        Family selected;
        if (water(state)) {
            selected = current.water();
        } else if (state.getType() == Fluids.LAVA || state.getType() == Fluids.FLOWING_LAVA) {
            selected = current.lava();
        } else {
            return original;
        }
        if (selected == null || !selected.matches(original)) return original;
        // Never mutate an array returned by NeoForge or another renderer.
        return new TextureAtlasSprite[] { selected.still(), selected.flow(), selected.overlay() };
    }

    private static boolean named(@Nullable TextureAtlasSprite sprite, ResourceLocation name) {
        return sprite != null && TextureAtlas.LOCATION_BLOCKS.equals(sprite.atlasLocation()) && name.equals(sprite.contents().name());
    }

    // Inspect the sprite actually in the draw, even if the atlas has since reloaded.
    public static boolean waterFace(BlockAndTintGetter view, @Nullable TextureAtlasSprite face) {
        return reachMesh(view) && (named(face, REACH_STILL) || named(face, REACH_FLOW) || named(face, REACH_OVERLAY));
    }

    public static int waterTint(int original, BlockAndTintGetter view, FluidState state, TextureAtlasSprite[] actual) {
        if (!water(state) || !reachMesh(view) || actual.length != 3) return original;
        return named(actual[0], REACH_STILL) && named(actual[1], REACH_FLOW) && named(actual[2], REACH_OVERLAY)
                ? original | 0x00FFFFFF : original;
    }
}
