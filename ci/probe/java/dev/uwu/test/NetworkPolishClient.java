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
    private static final boolean ACTIVE="network-weapon-polish".equals(System.getenv("UWU_VISIT")) && System.getenv("UWU_GRIP_VARIANT")==null;
    private static final Path SYNC=Path.of(System.getenv().getOrDefault("WISH_SYNC_DIR","."));
    private static final String ROLE=System.getenv().getOrDefault("WISH_ROLE","");
    private static boolean connecting,finished,captured;
    private static int scene=-1,ticks,photos,drawPhotos;
    private static boolean held;
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
        if(id!=scene){scene=id;ticks=0;captured=false;drawPhotos=0;held=false;}
        task=next;
        String view=task.getProperty("view");
        var remote=mc.level.players().stream().filter(p->p!=mc.player).findFirst().orElse(null);
        if(remote==null||!FiguraPivotProof.ready(mc,remote.getUUID()))return;
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
        if(task.getProperty("action").equals("draw") && ROLE.equals("archer")){
            if(ticks==12){button("mousedown");held=true;}
            if(ticks==44){button("mouseup");held=false;}
        }
        if(captured&&ticks>(task.getProperty("action").equals("draw")?64:15))Files.writeString(SYNC.resolve("polish-done-"+scene+"-"+ROLE),"captured");
    }
    private static void button(String action)throws Exception{
        var process=new ProcessBuilder("xdotool",action,"3").redirectErrorStream(true).start();
        if(!process.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)||process.exitValue()!=0)throw new AssertionError("Physical bow input "+action);
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post e)throws Exception{
        if(!ACTIVE||finished||captured||task==null||ticks<12)return;
        boolean draw=task.getProperty("action").equals("draw");
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        if(draw){
            var actor=mc.level.getEntity(Integer.parseInt(task.getProperty("shooter")));
            if(!(actor instanceof net.minecraft.world.entity.LivingEntity living) || !living.isUsingItem())return;
            int[] thresholds={1,8,15,21};
            if(drawPhotos>=4 || living.getTicksUsingItem()<thresholds[drawPhotos])return;
        }
        var stack=new ItemStack(BuiltInRegistries.ITEM.get(task.getProperty("item").contains(":")?ResourceLocation.parse(task.getProperty("item")):ResourceLocation.fromNamespaceAndPath("uwudimension",task.getProperty("item"))));
        if(stack.is(Items.AIR))throw new AssertionError("missing gallery model "+task);
        var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
        if(model==mc.getModelManager().getMissingModel())throw new AssertionError("missing baked model "+task);
        var g=e.getGuiGraphics();
        g.fill(4,4,350,40,0xa01b2030);
        g.drawString(mc.font,ROLE+" "+scene+" "+task.getProperty("item")+" "+task.getProperty("view"),8,8,0xffffffff,false);
        g.renderItem(stack,8,21);g.flush();
        Path dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);
        try(var pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())){pixels.writeToFile(dir.resolve(String.format(Locale.ROOT,"polish-%03d-%s-%d.png",scene,ROLE,drawPhotos)));}
        drawPhotos++;captured=!draw||drawPhotos==4;photos++;
        System.out.println("POLISH CLIENT FRAME "+ROLE+" "+scene+" model="+model.getParticleIcon().contents().name());
    }
}

