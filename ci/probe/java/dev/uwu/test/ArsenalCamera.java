package dev.uwu.test;
import dev.uwu.dimension.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class ArsenalCamera {
    private static String state="menu";
    private static int ticks,index;
    private static boolean shot;
    private static final String[] IDS={"axolotl_whip","frost_whip","counter_soulrender","vesper_scythe","glitch_bow"};
    private static final String[] PHOTOS={"twinfin-lash","frost-lash","dusk-element-wave","spinning-scythe","glitch-static"};
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!"arsenal".equals(System.getenv("UWU_VISIT")))return;
        var mc=Minecraft.getInstance();ticks++;
        if(ticks%200==0){
            System.out.println("ARSENAL CAMERA STATE "+state+" tick="+ticks+" screen="+(mc.screen==null?"none":mc.screen.getClass().getName())+" world="+(mc.level!=null));
            Screenshot.grab(mc.gameDirectory,"diagnostic-"+state+"-"+ticks+".png",mc.getMainRenderTarget(),m->System.out.println(m.getString()));
        }
        if(ticks>1800)throw new AssertionError("Arsenal camera stalled in "+state+" screen="+(mc.screen==null?"none":mc.screen.getClass().getName()));
        if(state.equals("menu")&&mc.screen instanceof TitleScreen title){
            state="create";ticks=0;
            mc.createWorldOpenFlows().createFreshLevel("ArsenalVisit",new LevelSettings("Arsenal Visit",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(152045,true,false),WorldPresets::createNormalWorldDimensions,title);return;
        }
        if(state.equals("create")&&mc.screen!=null&&mc.player==null)for(var child:List.copyOf(mc.screen.children()))if(child instanceof Button b&&(b.getMessage().getString().equals("Proceed")||b.getMessage().getString().equals("I know what I'm doing!"))){b.onPress();break;}
        if(state.equals("closed")&&mc.level==null){System.out.println("UWU ARSENAL CLIENT PASSED");mc.stop();return;}
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
        if(state.equals("create")&&ticks>80){
            state="ready";ticks=0;server.execute(()->{
                var p=server.getPlayerList().getPlayer(uuid);var l=p.serverLevel();
                for(int x=-12;x<=12;x++)for(int z=-8;z<=24;z++)l.setBlockAndUpdate(new BlockPos(x,150,z),(x%5==0?Blocks.PINK_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState());
                l.setDayTime(6000);p.teleportTo(.5,151,.5);p.setYRot(0);p.setXRot(0);
                var foe=EntityType.HUSK.create(l);foe.moveTo(.5,151,9,180,0);foe.setNoAi(true);foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);foe.setHealth(1000);l.addFreshEntity(foe);
            });return;
        }
        if(state.equals("ready")&&ticks>100){state="equip";ticks=0;}
        if(state.equals("equip")){
            if(index>=IDS.length){state="closed";mc.level.disconnect();mc.disconnect(new TitleScreen());return;}
            String id=IDS[index];state="armed";ticks=0;shot=false;
            server.execute(()->{var p=server.getPlayerList().getPlayer(uuid);p.stopUsingItem();p.getPersistentData().remove("uwu.scythe_flight");var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse("uwudimension:"+id));p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));p.getCooldowns().removeCooldown(item);p.teleportTo(.5,151,.5);p.setYRot(0);p.setXRot(0);});return;
        }
        if(state.equals("armed")&&ticks>35){
            mc.player.setYRot(0);mc.player.setXRot(0);
            if(index==2)NeoForge.EVENT_BUS.post(new InputEvent.InteractionKeyMappingTriggered(0,mc.options.keyAttack,InteractionHand.MAIN_HAND));
            else{mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
            state="shooting";ticks=0;return;
        }
        if(state.equals("shooting")){
            if(index==4&&ticks==22){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
            boolean visible=false;
            for(Entity e:mc.level.entitiesForRendering())if((index<2&&e instanceof RibbonLash&&e.tickCount>=3&&e.tickCount<=7)||((index==2||index==3)&&e instanceof RelicFlight f&&f.tickCount>=2&&f.tickCount<=9&&f.wave()==(index==2)))visible=true;
            if(index==4&&ticks>=24&&ticks<=29)visible=mc.player.getMainHandItem().has(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if(visible&&!shot){shot=true;Screenshot.grab(mc.gameDirectory,PHOTOS[index]+".png",mc.getMainRenderTarget(),m->System.out.println(m.getString()));System.out.println("ARSENAL VISUAL PASS "+PHOTOS[index]);}
            if(ticks>80){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);if(!shot)throw new AssertionError("Expected rendered attack "+PHOTOS[index]);index++;state="equip";ticks=0;}
        }
    }
}
