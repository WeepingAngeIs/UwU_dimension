package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.client.DawnpetalSpinClient;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/** A cosmetic correction around the shipped mesh's grip; never participates in bow gameplay. */
public final class CupidBowHeldVisual {
    private static final double DIAGONAL = 1 / Math.sqrt(2);
    // The mesh was rotated around (.5, .5): its original grip is (.3125, .5, .5).
    private static final double GRIP_X = -.1875 * DIAGONAL;
    private static final double GRIP_Y = .1875 * DIAGONAL;
    private static final double BASE_CLEARANCE = 1.0 / 32;
    private static final double GROWTH_CLEARANCE = 1.0 / 8;

    private CupidBowHeldVisual() {}

    public static BakedModel wrap(BakedModel model, LivingEntity owner, ItemStack stack,
                                  ItemDisplayContext context) {
        if (!held(context) || !stack.is(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get())
                || !Arsenal.hasSmoothCupidStyle(stack)) return model;
        float scale = CupidBowVisualConfig.heldScale();
        // PlayerAnimator may use a third-person display context during its actual first-person pass.
        boolean firstPersonPass = context.firstPerson()
                || owner instanceof Player player && DawnpetalSpinClient.animatorFirstPersonPass(player);
        return new BakedModelWrapper<BakedModel>(model) {
            @Override
            public BakedModel applyTransform(ItemDisplayContext view, PoseStack pose, boolean left) {
                BakedModel selected = super.applyTransform(view, pose, left);
                // ItemRenderer centers by (-.5,-.5,-.5) immediately after this callback.
                // Keep the exact painted grip stationary while scaling, including in the off hand.
                // Never translate toward/away from the camera in either first-person path.
                double forward = firstPersonPass || view.firstPerson() ? 0
                        : BASE_CLEARANCE + Math.max(0, scale - 1) * GROWTH_CLEARANCE;
                // Local forward is from string to limb. Applying after the selected hand mount
                // mirrors it correctly for both arms instead of shifting along the screen's Z axis.
                pose.translate(GRIP_X - DIAGONAL * forward, GRIP_Y + DIAGONAL * forward, 0);
                pose.scale(scale, scale, scale);
                pose.translate(-GRIP_X, -GRIP_Y, 0);
                return selected;
            }
        };
    }

    private static boolean held(ItemDisplayContext context) {
        return context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}
