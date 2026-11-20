package dev.uwu.dimension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** A quiet screen-edge decoration drawn under the ordinary HUD. */
@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class SacredSpringClient {
    private SacredSpringClient() {}

    @SubscribeEvent
    public static void draw(RenderGuiEvent.Pre event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui || !player.isInWater()
                || SacredSpring.OVERLAY.get() == SacredSpring.Overlay.OFF) return;
        BlockPos feet = player.blockPosition();
        boolean spring = false;
        for (int y=1; y<=3; y++) {
            BlockPos pos = feet.below(y);
            if (!mc.level.hasChunkAt(pos)) return;
            if (mc.level.getBlockState(pos).is(SacredSpring.BED.get())) { spring=true; break; }
            if (!mc.level.getFluidState(pos).is(FluidTags.WATER)) break;
        }
        if (!spring) return;
        GuiGraphics g=event.getGuiGraphics();
        int w=g.guiWidth(), h=g.guiHeight(), y=Math.max(28,h/2-18);
        heart(g,8,y,0x70ffb9d5);
        if (SacredSpring.OVERLAY.get()==SacredSpring.Overlay.FULL) {
            wing(g,18,y+2,0x58fff3f9);
            vine(g,5,y+13,0x5887bb9a);
            heart(g,w-19,y+20,0x60ffc5df);
            wing(g,w-31,y+22,0x50fff3f9);
            vine(g,w-7,y+33,0x5087bb9a);
        }
    }

    private static void heart(GuiGraphics g,int x,int y,int color) {
        g.fill(x+1,y,x+4,y+2,color); g.fill(x+5,y,x+8,y+2,color);
        g.fill(x,y+2,x+9,y+5,color); g.fill(x+1,y+5,x+8,y+6,color);
        g.fill(x+2,y+6,x+7,y+7,color); g.fill(x+3,y+7,x+6,y+8,color);
    }
    private static void wing(GuiGraphics g,int x,int y,int color) {
        g.fill(x,y+2,x+3,y+7,color); g.fill(x+3,y+1,x+5,y+5,color);
        g.fill(x+5,y,x+7,y+3,color);
    }
    private static void vine(GuiGraphics g,int x,int y,int color) {
        for(int i=0;i<15;i++) {
            int dx=(i/3)%2;
            g.fill(x+dx,y+i,x+dx+1,y+i+1,color);
            if (i%5==1) g.fill(x+dx+1,y+i,x+dx+3,y+i+2,color);
        }
    }
}

