package dev.uwu.dimension;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * A short presentation before the existing reader. The decorative sheets have no story index,
 * content, links, or input handlers. The native reader remains on its constructor's first page.
 */
final class LanternBookOpeningScreen extends Screen {
    private static final ResourceLocation COVER =
            ResourceLocation.fromNamespaceAndPath("uwudimension", "item/lantern_for_both_shores_cover");
    static final int LIFT_TICKS = 12;
    static final int COVER_TICKS = 14;
    static final int FLUTTER_TICKS = 18;
    static final int SETTLE_TICKS = 8;
    static final int DURATION_TICKS = LIFT_TICKS + COVER_TICKS + FLUTTER_TICKS + SETTLE_TICKS;
    // The shipped 48px portrait has a tilted body around (21,21.5), plus lace/ribbons.
    // Correct the portrait inside its own cover frame; the paper and hinge stay upright.
    private static final float PORTRAIT_ROLL = 12.5F;
    private static final float SPINE_X = -15F;
    private static final int COVER_WIDTH = 29;
    private static final int PAGE_WIDTH = 26;

    private final LocalPlayer owner;
    private final ClientLevel level;
    private final InteractionHand hand;
    private final ItemStack book;
    private final int side;
    private final BookViewScreen reader;
    private int age;

    LanternBookOpeningScreen(Minecraft client, ItemStack book, InteractionHand hand) {
        super(book.getHoverName());
        this.owner = client.player;
        this.level = client.level;
        this.hand = hand;
        this.book = book.copy();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? owner.getMainArm() : owner.getMainArm().getOpposite();
        side = arm == HumanoidArm.RIGHT ? 1 : -1;
        var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        reader = new BookViewScreen(new BookViewScreen.BookAccess(content.getPages(client.isTextFilteringEnabled())));
    }

    @Override
    protected void init() {
        // Resize/re-init changes layout only; it must not restart the opening or advance the story.
        reader.init(minecraft, width, height);
    }

    boolean matches(ItemStack stack, InteractionHand hand) {
        return this.hand == hand && LanternBookStacks.isLanternBook(stack)
                && ItemStack.isSameItemSameComponents(book, stack);
    }

    private boolean stillHolding() {
        return minecraft.player == owner && minecraft.level == level && owner.isAlive()
                && matches(owner.getItemInHand(hand), hand);
    }

    @Override
    public void tick() {
        if (!stillHolding()) {
            onClose();
            return;
        }
        if (++age >= DURATION_TICKS) finish();
    }

    private void finish() {
        if (minecraft.screen != this) return;
        if (stillHolding()) minecraft.setScreen(reader);
        else onClose();
    }

    @Override
    public boolean isPauseScreen() {
        // GUI ticks continue while a singleplayer world is paused, just as in the native reader.
        // Keep the same combat/world-time behavior throughout the presentation.
        return reader.isPauseScreen();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            finish();
        }
        // Navigation pressed during the flourish is consumed, never queued onto the real page.
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) finish();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return true; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float time = Math.min(age + partialTick, DURATION_TICKS);
        float lift = ease(time / LIFT_TICKS);
        float cover = ease((time - LIFT_TICKS) / COVER_TICKS);
        float settle = ease((time - LIFT_TICKS - COVER_TICKS - FLUTTER_TICKS) / SETTLE_TICKS);
        graphics.fill(0, 0, width, height, ((int) (lift * 92) << 24));

        if (settle > 0) {
            graphics.setColor(1, 1, 1, settle);
            // This preview is always the first native page. No preview widget ever receives input.
            reader.render(graphics, -1, -1, partialTick);
            graphics.flush();
            graphics.setColor(1, 1, 1, 1);
        }

        float target = Math.min(192F, Math.min(width * .44F, height * .72F));
        float size = Mth.lerp(lift, Math.min(target * .58F, 78F), target);
        float centerX = Mth.lerp(lift, width * (.5F + side * .30F), width * .5F);
        float centerY = Mth.lerp(lift, height + size * .15F, Math.min(height * .48F, 102F));
        var poses = graphics.pose();
        poses.pushPose();
        poses.translate(centerX, centerY, 220);
        poses.scale(size / 48F, size / 48F, size / 48F);
        poses.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(lift, side * 8F, 0F)));
        graphics.setColor(1, 1, 1, 1 - settle);

        if (cover > 0) {
            // Blank cream leaves underneath the authored cover; no story text or page numbers.
            page(graphics, 0, 0xFFEBD5CF, 0);
            page(graphics, 0, 0xFFFFF3E5, 1);
        }

        cover(graphics, -168F * cover);

        float flutterStart = LIFT_TICKS + COVER_TICKS;
        if (time >= flutterStart) {
            float flutter = (time - flutterStart) / 6F;
            // Three independent decorative leaves. None can call setPage/pageForward on the reader.
            for (int leaf = 0; leaf < 3; leaf++) {
                float phase = Mth.clamp(flutter - leaf, 0, 1);
                // Completed leaves remain on the left, making a visible opened stack until settling.
                if (phase > 0) page(graphics, -170F * ease(phase), 0xFFFFF8EB, 3 + leaf);
            }
        }

        graphics.flush();
        graphics.setColor(1, 1, 1, 1);
        poses.popPose();
    }

    private void cover(GuiGraphics graphics, float angle) {
        var poses = graphics.pose();
        poses.pushPose();
        poses.translate(SPINE_X, 0, 2);
        poses.mulPose(Axis.YP.rotationDegrees(angle));
        if (angle > -90F) {
            // Untilt the existing art around its body, then turn that body around the same spine as the paper.
            poses.translate(-SPINE_X, 0, 0);
            poses.mulPose(Axis.ZP.rotationDegrees(PORTRAIT_ROLL));
            poses.translate(-21, -21.5, 0);
            var sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(COVER);
            graphics.blit(0, 0, 0, 48, 48, sprite);
        } else {
            // GUI quads have one facing. Turn the reverse face back toward the camera on the SAME plane.
            poses.translate(COVER_WIDTH, 0, 0);
            poses.mulPose(Axis.YP.rotationDegrees(180));
            graphics.fill(0, -16, COVER_WIDTH, 16, 0xFFDBA7BB);
            graphics.fill(1, -15, COVER_WIDTH - 1, 15, 0xFFF5D3DD);
            graphics.fill(COVER_WIDTH - 2, -15, COVER_WIDTH - 1, 15, 0xFFC98CA6);
        }
        poses.popPose();
    }

    private static void page(GuiGraphics graphics, float angle, int color, int depth) {
        var poses = graphics.pose();
        poses.pushPose();
        poses.translate(SPINE_X + 1, 0, depth);
        poses.mulPose(Axis.YP.rotationDegrees(angle));
        if (angle <= -90F) {
            poses.translate(PAGE_WIDTH, 0, 0);
            poses.mulPose(Axis.YP.rotationDegrees(180));
        }
        graphics.fill(0, -15, PAGE_WIDTH, 15, 0xFFD9BFB3);
        graphics.fill(1, -14, PAGE_WIDTH - 1, 14, color);
        graphics.fill(1, -14, 2, 14, 0xFFE8D3C8);
        poses.popPose();
    }

    private static float ease(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
