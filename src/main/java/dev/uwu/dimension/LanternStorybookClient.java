package dev.uwu.dimension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class LanternStorybookClient {
    private LanternStorybookClient() {}

    static void open(WrittenBookContent content) {
        var client = Minecraft.getInstance();
        var pages = content.getPages(client.isTextFilteringEnabled());
        client.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(pages)));
    }
}
