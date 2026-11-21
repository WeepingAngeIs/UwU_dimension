package dev.uwu.dimension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class LanternStorybookClient {
    private LanternStorybookClient() {}

    /** Called only from the native held-book open path; titles are not an identity check. */
    public static boolean openHeld(ItemStack book, InteractionHand hand) {
        var client = Minecraft.getInstance();
        if (!LanternBookStacks.isLanternBook(book) || client.player == null || client.level == null) return false;
        if (client.screen instanceof LanternBookOpeningScreen opening && opening.matches(book, hand)) return true;
        client.setScreen(new LanternBookOpeningScreen(client, book, hand));
        return true;
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        if (Minecraft.getInstance().screen instanceof LanternBookOpeningScreen opening
                && opening.matches(event.getItemStack(), event.getHand())) {
            // The same atlas cover is moving into the reader. Do not leave a second copy at the wrist.
            event.setCanceled(true);
        }
    }

    static void open(WrittenBookContent content) {
        var client = Minecraft.getInstance();
        var pages = content.getPages(client.isTextFilteringEnabled());
        client.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(pages)));
    }
}
