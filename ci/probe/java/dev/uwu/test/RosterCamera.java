package dev.uwu.test;

import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.CounterArsenal;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Photographs actual in-game GUI item models; no replacement artwork or mock renderer. */
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class RosterCamera {
    private static final int PER_PAGE=16;
    private static final List<ItemStack> ITEMS=new ArrayList<>();
    private static final Set<String> DIRECT=new HashSet<>();
    private static String state="menu";
    private static int ticks,page;
    private static boolean captured;

    private static void add(String id,boolean direct) {
        var key=ResourceLocation.fromNamespaceAndPath("uwudimension",id);
        var item=BuiltInRegistries.ITEM.get(key);
        if(item==Items.AIR)throw new AssertionError("Missing gallery item "+key);
        if(ITEMS.stream().anyMatch(s->s.is(item)))throw new AssertionError("Duplicate gallery item "+key);
        ITEMS.add(new ItemStack(item));
        if(direct)DIRECT.add(id);
        System.out.println("ROSTER GALLERY ITEM "+ITEMS.size()+" "+key+" "+(direct?"direct":"retune"));
    }
    private static void populate() {
        for(var relic:Arsenal.Relic.values())add(relic.name().toLowerCase(java.util.Locale.ROOT),true);
        add("glitch_bow",true);
        add("heart_launcher",true);
        add("bell_hammer",true);
        for(String material:List.of("silver","roseglass","vesper_opal"))
            for(String kind:List.of("pickaxe","axe","shovel","hoe"))add(material+"_"+kind,true);
        for(var entry:CounterArsenal.ROSTER)if(CounterArsenal.selectable(entry))add(entry.id(),CounterArsenal.active(entry));
        if(ITEMS.size()<20)throw new AssertionError("Unexpectedly small gallery: "+ITEMS.size());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!"roster".equals(System.getenv("UWU_VISIT")))return;
        var mc=Minecraft.getInstance();
        ticks++;mc.getToasts().clear();
        if(ticks>1800)throw new AssertionError("Roster gallery stalled: "+state);
        if(state.equals("menu")&&mc.screen instanceof TitleScreen title) {
            state="create";ticks=0;
            mc.createWorldOpenFlows().createFreshLevel("RosterGallery",new LevelSettings("Roster Gallery",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(152045,true,false),WorldPresets::createNormalWorldDimensions,title);
            return;
        }
        if(state.equals("create")&&mc.screen!=null&&mc.player==null)
            for(var child:List.copyOf(mc.screen.children()))if(child instanceof Button b&&(b.getMessage().getString().equals("Proceed")||b.getMessage().getString().equals("I know what I'm doing!"))){b.onPress();break;}
        if(state.equals("closed")&&mc.level==null){System.out.println("UWU ROSTER CLIENT PASSED items="+ITEMS.size()+" pages="+page);mc.stop();return;}
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(state.equals("create")&&ticks>100){populate();state="gallery";ticks=0;}
        if(state.equals("gallery")&&captured&&ticks>=8){
            page++;captured=false;ticks=0;
            if(page*PER_PAGE>=ITEMS.size())state="finish";
        }
        // Screenshots are written asynchronously; keep the client running after the last capture.
        if(state.equals("finish")&&ticks>80){state="closed";mc.level.disconnect();mc.disconnect(new TitleScreen());}
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        if(!"roster".equals(System.getenv("UWU_VISIT"))||!state.equals("gallery"))return;
        var mc=Minecraft.getInstance();var g=event.getGuiGraphics();
        int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight();
        g.fill(0,0,width,height,0xff24192a);
        g.drawString(mc.font,"SAKURA REACH | ACTUAL IN-GAME ITEM MODELS",12,10,0xffffd2e7,false);
        int pages=(ITEMS.size()+PER_PAGE-1)/PER_PAGE;
        g.drawString(mc.font,"Direct + retunable forms; gold mark = direct  |  page "+(page+1)+" / "+pages,12,23,0xfff1dcba,false);
        // Leave room beneath the grid for overlays that other installed mods draw after this event.
        int cellW=(width-24)/4,cellH=(height-72)/4;
        for(int i=0;i<PER_PAGE&&page*PER_PAGE+i<ITEMS.size();i++) {
            ItemStack stack=ITEMS.get(page*PER_PAGE+i);
            int x=12+(i%4)*cellW,y=43+(i/4)*cellH;
            g.fill(x,y,x+cellW-6,y+cellH-5,0xff392a40);
            if(DIRECT.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()))g.fill(x,y,x+cellW-6,y+2,0xffffd481);
            g.pose().pushPose();g.pose().translate(x+5,y+5,0);g.pose().scale(1.8f,1.8f,1);g.renderItem(stack,0,0);g.pose().popPose();
            int textX=x+39,space=cellW-46;
            var lines=mc.font.split(Component.literal(stack.getHoverName().getString()),Math.max(1,(int)(space/.8f)));
            g.pose().pushPose();g.pose().translate(textX,y+5,0);g.pose().scale(.8f,.8f,1);
            for(int line=0;line<Math.min(lines.size(),4);line++)g.drawString(mc.font,lines.get(line),0,line*10,0xffffffff,false);
            g.pose().popPose();
        }
        if(!captured&&ticks>=4){
            g.flush();captured=true;
            String name=String.format(java.util.Locale.ROOT,"roster-gallery-%02d.png",page+1);
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->System.out.println(m.getString()));
            System.out.println("ROSTER GALLERY PAGE "+(page+1)+" of "+pages);
        }
    }
}
