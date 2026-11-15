package dev.uwu.test;

import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Uses a real client to photograph the launcher during its actual cooldown. */
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class LauncherCamera {
    private static String state="menu";
    private static int ticks, shot;
    private static boolean captured;
    private static boolean awaitingRecoil;
    private LauncherCamera() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!"launcher".equals(System.getenv("UWU_VISIT")))return;
        var mc=Minecraft.getInstance();
        ticks++;
        if(ticks>2400)throw new AssertionError("Launcher camera stalled at "+state);
        if(state.equals("closed") && mc.level==null) {System.out.println("LAUNCHER CLIENT VIEWS PASS");mc.stop();return;}
        if(state.equals("menu") && mc.screen instanceof TitleScreen title) {
            state="create";ticks=0;
            mc.createWorldOpenFlows().createFreshLevel("LauncherVisit",new LevelSettings("Launcher Visit",
                    GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(152045,true,false),WorldPresets::createNormalWorldDimensions,title);return;
        }
        if(state.equals("create") && mc.screen!=null && mc.player==null)
            for(var c:List.copyOf(mc.screen.children()))if(c instanceof Button b && (b.getMessage().getString().equals("Proceed")
                    || b.getMessage().getString().equals("I know what I'm doing!"))){b.onPress();break;}
        if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
        if(state.equals("create") && ticks>80) {
            state="wait";ticks=0;
            server.execute(()->{
                var p=server.getPlayerList().getPlayer(id);var level=p.serverLevel();
                for(int x=-12;x<=12;x++)for(int z=-12;z<=36;z++)
                    level.setBlockAndUpdate(new BlockPos(x,150,z),(x%5==0?Blocks.PINK_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState());
                p.teleportTo(.5,151,.5);p.setYRot(0);p.setXRot(0);
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("uwudimension:heart_launcher"))));
            });return;
        }
        if(state.equals("wait") && ticks>100) {state="armed";ticks=0;return;}
        if(state.equals("armed") && ticks==1)
            mc.options.setCameraType(shot==0?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_FRONT);
        if(state.equals("armed") && ticks==20) {
            String frame=shot==0?"launcher-first-rest":"launcher-third-rest";
            Screenshot.grab(mc.gameDirectory,frame+".png",mc.getMainRenderTarget(),m->System.out.println(m.getString()));
        }
        if(state.equals("armed") && ticks>30) {
            mc.options.setCameraType(shot==0?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_FRONT);
            mc.options.hideGui=false;
            mc.player.setYRot(0);mc.player.setXRot(0);
            captured=false;awaitingRecoil=false;state="firing";ticks=0;
            mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return;
        }
        if(state.equals("firing")) {
            var stack=mc.player.getMainHandItem();
            if(mc.player.getCooldowns().getCooldownPercent(stack.getItem(),0)>0) awaitingRecoil=true;
            if(ticks>70) {
                if(!captured)throw new AssertionError("Launcher cooldown not observed in view "+shot);
                shot++;
                if(shot==2) {state="closed";mc.level.disconnect();mc.disconnect(new TitleScreen());}
                else {state="armed";ticks=0;}
            }
        }
    }
    @SubscribeEvent public static void rendered(RenderGuiEvent.Post event) {
        if(!"launcher".equals(System.getenv("UWU_VISIT")) || !state.equals("firing") || captured || !awaitingRecoil) return;
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.player.getCooldowns().getCooldownPercent(mc.player.getMainHandItem().getItem(),0)<=0) return;
        captured=true;
        String frame=shot==0?"launcher-first-recoil":"launcher-third-recoil";
        Screenshot.grab(mc.gameDirectory,frame+".png",mc.getMainRenderTarget(),m->System.out.println(m.getString()));
        System.out.println("LAUNCHER RECOIL FRAME PASS "+frame);
    }
}

