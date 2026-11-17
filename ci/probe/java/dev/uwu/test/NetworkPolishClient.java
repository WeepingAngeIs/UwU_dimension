package dev.uwu.test;

import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** No pose override; screenshots use production models and installed animation bridges. */
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class NetworkPolishClient {
    private static final boolean ACTIVE="network-weapon-polish".equals(System.getenv("UWU_VISIT"));
    private static final Path SYNC=Path.of(System.getenv().getOrDefault("WISH_SYNC_DIR","."));
    private static final String ROLE=System.getenv().getOrDefault("WISH_ROLE","");
    private static boolean connecting,finished,captured;
    private static int scene=-1,ticks,photos;
    private static long started;
    private static Properties task;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception{
        if(!ACTIVE||finished)return;
        if(started==0)started=System.nanoTime();
        if(System.nanoTime()-started>1_800_000_000_000L)throw new AssertionError("POLISH CLIENT timeout "+ROLE+" "+scene);
        var mc=Minecraft.getInstance();
        if(!connecting&&mc.screen instanceof TitleScreen menu){
            connecting=true;mc.options.bobView().set(false);
            ConnectScreen.startConnecting(menu,mc,ServerAddress.parseString("127.0.0.1:25576"),new ServerData("Polish proof","127.0.0.1:25576",ServerData.Type.OTHER),false,null);return;
        }
        if(mc.player==null||mc.level==null||mc.getConnection()==null||mc.screen!=null)return;
        if(mc.getSingleplayerServer()!=null||mc.getConnection().getConnection().isMemoryConnection())throw new AssertionError("Expected actual TCP client");
        if(Files.exists(SYNC.resolve("polish-complete"))){
            finished=true;System.out.println("POLISH CLIENT PASS "+ROLE+" photos="+photos);mc.stop();return;
        }
        Path f=SYNC.resolve("polish-task.properties");if(!Files.exists(f))return;
        var next=new Properties();try(var r=Files.newBufferedReader(f)){next.load(r);}
        int id=Integer.parseInt(next.getProperty("id"));
        if(id!=scene){scene=id;ticks=0;captured=false;}
        task=next;
        String view=task.getProperty("view");
        mc.options.setCameraType(ROLE.equals("archer")&&view.startsWith("third")?CameraType.THIRD_PERSON_FRONT:CameraType.FIRST_PERSON);
        if(ROLE.equals("archer")){
            mc.player.setYRot(0);mc.player.setXRot(view.equals("ground")?24:0);
        }else{
            var actor=mc.level.getEntity(Integer.parseInt(task.getProperty("shooter")));if(actor==null)return;
            var direction=actor.position().add(0,1.1,0).subtract(mc.player.getEyePosition());
            mc.player.setYRot((float)(Math.toDegrees(Math.atan2(direction.z,direction.x))-90));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));
        }
        if(mc.levelRenderer.countRenderedSections()<2)return;
        Files.writeString(SYNC.resolve("polish-ready-"+scene+"-"+ROLE),"ready");
        if(!task.getProperty("state").equals("go"))return;
        ticks++;
        if(captured&&ticks>15)Files.writeString(SYNC.resolve("polish-done-"+scene+"-"+ROLE),"captured");
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post e)throws Exception{
        if(!ACTIVE||finished||captured||task==null||ticks<12)return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension",task.getProperty("item"))));
        if(stack.is(Items.AIR))throw new AssertionError("missing gallery model "+task);
        var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
        if(model==mc.getModelManager().getMissingModel())throw new AssertionError("missing baked model "+task);
        var g=e.getGuiGraphics();
        g.fill(4,4,350,40,0xa01b2030);
        g.drawString(mc.font,ROLE+" "+scene+" "+task.getProperty("item")+" "+task.getProperty("view"),8,8,0xffffffff,false);
        g.renderItem(stack,8,21);g.flush();
        Path dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);
        try(var pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())){pixels.writeToFile(dir.resolve(String.format(Locale.ROOT,"polish-%03d-%s.png",scene,ROLE)));}
        captured=true;photos++;
        System.out.println("POLISH CLIENT FRAME "+ROLE+" "+scene+" model="+model.getParticleIcon().contents().name());
    }
}

