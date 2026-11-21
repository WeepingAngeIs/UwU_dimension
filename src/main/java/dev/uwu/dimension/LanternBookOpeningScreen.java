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
    public boolean isPauseScreen() { return false; }

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

        float target = Math.min(width * .44F, height * .72F);
        float size = Mth.lerp(lift, Math.min(target * .58F, 78F), target);
        float centerX = Mth.lerp(lift, width * (.5F + side * .30F), width * .5F);
        float centerY = Mth.lerp(lift, height + size * .15F, Math.min(height * .48F, 102F));
        var poses = graphics.pose();
        poses.pushPose();
        poses.translate(centerX, centerY, 220);
        poses.scale(size / 48F, size / 48F, size / 48F);
        poses.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(lift, side * 8F, 14F)));
        graphics.setColor(1, 1, 1, 1 - settle);

        if (cover > 0) {
            // Blank cream leaves underneath the authored cover; no story text or page numbers.
            page(graphics, 0, 0xFFEBD5CF, 0);
            page(graphics, 0, 0xFFFFF3E5, 1);
        }

        poses.pushPose();
        poses.translate(-13, 0, 2);
        poses.mulPose(Axis.YP.rotationDegrees(-168F * cover));
        poses.translate(13, 0, 0);
        if (cover < .54F) {
            // The same animated atlas sprite retains the pastel cover, heart clasp, lace and ribbons.
            var sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(COVER);
            graphics.blit(-24, -24, 0, 48, 48, sprite);
        } else {
            graphics.fill(-15, -18, 14, 18, 0xFFDBA7BB);
            graphics.fill(-13, -16, 12, 16, 0xFFF5D3DD);
        }
        poses.popPose();

        float flutterStart = LIFT_TICKS + COVER_TICKS;
        if (time >= flutterStart && time < flutterStart + FLUTTER_TICKS) {
            float flutter = (time - flutterStart) / 6F;
            // Three independent decorative leaves. None can call setPage/pageForward on the reader.
            for (int leaf = 0; leaf < 3; leaf++) {
                float phase = Mth.clamp(flutter - leaf, 0, 1);
                if (phase > 0 && phase < 1) page(graphics, -170F * ease(phase), 0xFFFFF8EB, 3 + leaf);
            }
        }

        graphics.flush();
        graphics.setColor(1, 1, 1, 1);
        poses.popPose();
    }

    private static void page(GuiGraphics graphics, float angle, int color, int depth) {
        var poses = graphics.pose();
        poses.pushPose();
        poses.translate(-12, 0, depth);
        poses.mulPose(Axis.YP.rotationDegrees(angle));
        graphics.fill(0, -15, 24, 16, 0xFFD9BFB3);
        graphics.fill(1, -14, 23, 15, color);
        graphics.fill(1, -14, 2, 15, 0xFFE8D3C8);
        poses.popPose();
    }

    private static float ease(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
