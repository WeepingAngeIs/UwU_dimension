package dev.uwu.test;

import java.util.List;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Real vanilla hand pipeline; no model or pose replacement in the probe. */
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class BowPoseCamera {
    private static final String[] IDS={"minecraft:bow","uwudimension:cupid_bow","uwudimension:ripple_bow"};
    private static final String[] STAGES={"rest","early","middle","full"};
    private static String state="menu";
    private static int ticks,trial,stage,photos;
    private static boolean captured;
    private static InteractionHand hand(){return trial%4<2?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!"bow-poses".equals(System.getenv("UWU_VISIT")))return;
        var mc=Minecraft.getInstance();ticks++;mc.getToasts().clear();
        if(ticks>2400)throw new AssertionError("Bow camera stalled "+state+" trial="+trial+" stage="+stage);
        if(state.equals("menu")&&mc.screen instanceof TitleScreen title){
            state="create";ticks=0;
            mc.createWorldOpenFlows().createFreshLevel("BowPoseVisit",new LevelSettings("Bow poses",GameType.CREATIVE,false,
                    Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(152045,true,false),WorldPresets::createNormalWorldDimensions,title);
            return;
        }
        if(state.equals("create")&&mc.screen!=null&&mc.player==null)
            for(var child:List.copyOf(mc.screen.children()))if(child instanceof Button b&&(b.getMessage().getString().equals("Proceed")||b.getMessage().getString().equals("I know what I'm doing!"))){b.onPress();break;}
        if(state.equals("closed")&&mc.level==null){
            if(photos!=48)throw new AssertionError("Expected 48 bow photos, got "+photos);
            System.out.println("BOW POSE MATRIX PASS 48 vanilla/Cupid/Ripple real held/draw views in both hands");mc.stop();return;
        }
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
        if(state.equals("create")&&ticks>80){
            state="wait";ticks=0;
            server.execute(()->{
                var p=server.getPlayerList().getPlayer(uuid);var level=p.serverLevel();
                for(int x=-12;x<=12;x++)for(int z=-12;z<=20;z++)level.setBlockAndUpdate(new BlockPos(x,150,z),(x%5==0?Blocks.PINK_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState());
                level.setDayTime(6000);p.teleportTo(.5,151,.5);p.setYRot(0);p.setXRot(0);server.tickRateManager().setTickRate(8F);
            });return;
        }
        if(state.equals("wait")&&ticks>80){state="equip";ticks=0;}
        if(state.equals("equip")){
            if(trial==12){state="closed";mc.level.disconnect();mc.disconnect(new TitleScreen());return;}
            state="armed";ticks=0;stage=0;captured=false;
            mc.options.setCameraType(trial%2==0?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_FRONT);
            server.execute(()->{
                var p=server.getPlayerList().getPlayer(uuid);p.stopUsingItem();
                p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                p.setItemInHand(hand(),new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(IDS[trial/4]))));
                p.containerMenu.broadcastChanges();
            });return;
        }
        mc.player.setYRot(0);mc.player.setXRot(0);
        if(state.equals("armed")&&ticks>18&&captured){
            mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,hand());state="drawing";stage=1;captured=false;ticks=0;return;
        }
        if(state.equals("drawing")&&captured){
            if(stage==3){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);trial++;state="equip";ticks=0;}
            else{stage++;captured=false;}
        }
    }
    @SubscribeEvent public static void rendered(RenderGuiEvent.Post event){
        if(!"bow-poses".equals(System.getenv("UWU_VISIT"))||captured||(!state.equals("armed")&&!state.equals("drawing")))return;
        var mc=Minecraft.getInstance();if(mc.player==null||(stage==0&&ticks<10))return;
        var stack=mc.player.getItemInHand(hand());
        if(!stack.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse(IDS[trial/4]))))return;
        int t=mc.player.getTicksUsingItem();int needed=switch(stage){case 1->4;case 2->14;case 3->24;default->0;};
        if(stage>0&&(!mc.player.isUsingItem()||t<needed))return;
        var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
        String sprite=model.getParticleIcon().contents().name().toString();
        String root=IDS[trial/4].replace(":",":item/");
        String expected=stage==0?root:trial/4==0?root+"_pulling_"+(stage-1):root+"_draw"+(trial/4==2?(stage==3?2:1):(stage-1));
        if(!sprite.equals(expected))throw new AssertionError("Wrong actual bow model "+sprite+" expected "+expected+" at "+t);
        String frame="pose-"+IDS[trial/4].split(":")[1]+"-"+(hand()==InteractionHand.MAIN_HAND?"right":"left")+"-"+(trial%2==0?"first":"third")+"-"+STAGES[stage];
        event.getGuiGraphics().drawString(mc.font,frame+" drawTicks="+t,8,8,0xffffffff);
        event.getGuiGraphics().flush();
        Screenshot.grab(mc.gameDirectory,frame+".png",mc.getMainRenderTarget(),m->System.out.println(m.getString()));
        System.out.println("BOW POSE FRAME "+frame+" model="+sprite+" drawTicks="+t);captured=true;photos++;
    }
}
