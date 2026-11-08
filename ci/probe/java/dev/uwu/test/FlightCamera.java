package dev.uwu.test;
import dev.uwu.dimension.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class FlightCamera {
    static String state="menu";static int ticks,samples,index;static double lastY,highest;static volatile double serverY;
    static volatile int recoveries;static volatile boolean active;static UUID resident;static final double[] HEIGHTS={180,500,2000,4000,7000,8149};
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        if(!"flight".equals(System.getenv("UWU_VISIT"))||Minecraft.getInstance().player==null)return;
        var mc=Minecraft.getInstance();event.getGuiGraphics().drawString(mc.font,String.format("REAL GUARDIAN FLIGHT | client Y %.1f | server Y %.1f | recovery teleports %d",mc.player.getY(),serverY,recoveries),10,10,0xffffff,true);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!"flight".equals(System.getenv("UWU_VISIT")))return;
        var mc=Minecraft.getInstance();ticks++;mc.getToasts().clear();
        if(ticks%200==0)System.out.println("FLIGHT CAMERA "+state+" ticks="+ticks+" serverY="+serverY);
        check(ticks<2000,"Flight client stalled: "+state);
        if(state.equals("menu")&&mc.screen instanceof TitleScreen title){state="create";ticks=0;
            mc.createWorldOpenFlows().createFreshLevel("GuardianFlight",new LevelSettings("Guardian Flight",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(152045,true,false),WorldPresets::createNormalWorldDimensions,title);return;}
        if(state.equals("create")&&mc.screen!=null&&mc.player==null)for(var child:List.copyOf(mc.screen.children()))if(child instanceof Button b&&(b.getMessage().getString().equals("Proceed")||b.getMessage().getString().equals("I know what I'm doing!"))){b.onPress();break;}
        if(state.equals("closed")&&mc.level==null){System.out.println("UWU FLIGHT CLIENT PASSED");mc.stop();return;}
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
        if(state.equals("create")&&ticks>80){state="ready";ticks=0;server.execute(()->{
            server.setFlightAllowed(false);
            var p=server.getPlayerList().getPlayer(uuid);var l=p.serverLevel();
            for(int x=-15;x<=15;x++)for(int z=-15;z<=15;z++)l.setBlockAndUpdate(new BlockPos(x,150,z),(x%4==0?Blocks.PINK_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState());
            l.setDayTime(6000);p.teleportTo(.5,151,.5);
            var k=People.PETAL_KEEPER.get().create(l);k.moveTo(.5,151,1.5,0,0);k.bindVillage(new BlockPos(0,151,3));l.addFreshEntity(k);resident=k.getUUID();
            var g=People.BLOSSOM_WARDEN.get().create(l);g.moveTo(.5,151,3.5,0,0);g.bindVillage(new BlockPos(0,151,3));l.addFreshEntity(g);
        });return;}
        if(state.equals("ready")&&ticks>80){
            state="attack";ticks=0;mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.player.setXRot(35);
            server.execute(()->{var p=server.getPlayerList().getPlayer(uuid);p.setGameMode(GameType.SURVIVAL);p.attack(p.serverLevel().getEntity(resident));});return;
        }
        if(state.equals("attack")||state.equals("flight")||state.equals("descent")){
            server.execute(()->{var p=server.getPlayerList().getPlayer(uuid);if(p!=null){serverY=p.getY();recoveries=p.getPersistentData().getInt(GuardianFlight.RECOVERIES);active=p.getPersistentData().contains(GuardianFlight.STATE);}});
            double y=mc.player.getY();
            if(state.equals("attack")&&active){state="flight";ticks=0;lastY=y;}
            if(state.equals("flight")){
                double step=y-lastY;check(step<60&&step>=-2,"Player position jumped or was corrected during ascent: "+step);check(recoveries==0,"Unobstructed player used fallback");
                if(step>.5)samples++;lastY=y;highest=Math.max(highest,y);
                if(ticks%10==0)System.out.println("PLAYER FLIGHT SAMPLE clientY="+y+" serverY="+serverY+" delta="+step+" recovery="+recoveries);
                if(index<HEIGHTS.length&&y>=HEIGHTS[index]){Screenshot.grab(mc.gameDirectory,"guardian-flight-"+index+".png",mc.getMainRenderTarget(),m->System.out.println(m.getString()));index++;}
                if(!active&&serverY>8148){check(samples>120&&index==HEIGHTS.length,"Insufficient actual player movement/frames");state="descent";ticks=0;}
            }else if(state.equals("descent")&&ticks>30){
                check(y<highest-5,"Player remained suspended instead of falling");check(recoveries==0,"Player recovery count changed");
                System.out.println("PLAYER REAL TRAJECTORY PASS samples="+samples+" apex="+highest+" current="+y+" zero recovery teleports");
                state="closed";mc.level.disconnect();mc.disconnect(new TitleScreen());
            }
        }
    }
}
