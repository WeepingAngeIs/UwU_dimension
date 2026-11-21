package dev.uwu.dimension;

import com.google.common.collect.ImmutableList;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

@EventBusSubscriber(modid = "uwudimension", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LanternBookModels {
    private static final ModelResourceLocation WRITTEN_BOOK =
            ModelResourceLocation.inventory(ResourceLocation.withDefaultNamespace("written_book"));
    private static final ModelResourceLocation COVER = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("uwudimension", "item/lantern_for_both_shores_cover"));

    private LanternBookModels() {}

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        event.register(COVER);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void bake(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        BakedModel cover = models.get(COVER);
        if (cover == null || cover == models.get(ModelBakery.MISSING_MODEL_VARIANT)) return;
        models.computeIfPresent(WRITTEN_BOOK, (key, original) -> new WrittenBookModel(original, cover));
    }

    private static final class WrittenBookModel extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        WrittenBookModel(BakedModel original, BakedModel cover) {
            super(original);
            // Handmade cover edges use elements, but dropped stacks and GUI depth still follow a flat book.
            BakedModel flatCover = new BakedModelWrapper<BakedModel>(cover) {
                @Override
                public boolean isGui3d() { return false; }
            };
            this.overrides = new ItemOverrides() {
                @Override
                public @Nullable BakedModel resolve(BakedModel base, ItemStack stack, @Nullable ClientLevel level,
                                                    @Nullable LivingEntity holder, int seed) {
                    BakedModel previousBase = base == WrittenBookModel.this ? original : base;
                    BakedModel resolved = original.getOverrides().resolve(previousBase, stack, level, holder, seed);
                    if (resolved != previousBase || !stack.has(DataComponents.CUSTOM_DATA)
                            || !LanternBookStacks.isLanternBook(stack)) return resolved;

                    BakedModel chosen = cover.getOverrides().resolve(cover, stack, level, holder, seed);
                    return chosen == cover ? flatCover : chosen;
                }

                @Override
                public ImmutableList<BakedOverride> getOverrides() {
                    return original.getOverrides().getOverrides();
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() { return overrides; }
    }
}
