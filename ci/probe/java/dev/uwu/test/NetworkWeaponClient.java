package dev.uwu.test;

import io.netty.channel.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Actual X11 click, untouched packet observation and real first/third/remote framebuffers. */
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class NetworkWeaponClient {
    private static final boolean ACTIVE="network-weapon-animation".equals(System.getenv("UWU_VISIT"));
    private static final String ROLE=System.getenv().getOrDefault("WISH_ROLE","");
    private static final Path SYNC=Path.of(System.getenv().getOrDefault("WISH_SYNC_DIR","."));
    private static final Queue<String> PACKETS=new ConcurrentLinkedQueue<>();
    private static boolean connecting,watching,finished,pressed;private static int scene=-1,ticks,frames=-1,renderFrames;
    private static String packet;private static long started;private static Properties task;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError("WEAPON CLIENT "+ROLE+" "+why);}
    private static void click(String cmd){
        try{var process=new ProcessBuilder("xdotool",cmd,"1").redirectErrorStream(true).start();
            check(process.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)&&process.exitValue()==0,"physical mouse "+cmd);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void observe(Minecraft mc){
        var channel=mc.getConnection().getConnection().channel();
        channel.eventLoop().execute(()->channel.pipeline().addBefore("packet_handler","uwu_weapon_observer",new ChannelInboundHandlerAdapter(){
            @Override public void channelRead(ChannelHandlerContext ctx,Object msg)throws Exception{
                if(msg instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet){
                    Object p=packet.payload();
                    if(p.getClass().getName().equals("net.bettercombat.network.Packets$AttackAnimation"))
                        PACKETS.add(p.getClass().getMethod("playerId").invoke(p)+"|"+p.getClass().getMethod("animationName").invoke(p)+"|"+p.getClass().getMethod("animatedHand").invoke(p));
                }
                super.channelRead(ctx,msg);
            }
        }));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!ACTIVE||finished)return;
        if(started==0)started=System.nanoTime();check(System.nanoTime()-started<900_000_000_000L,"timeout scene="+scene);
        var mc=Minecraft.getInstance();
        if(!connecting&&mc.screen instanceof TitleScreen menu){connecting=true;mc.options.bobView().set(false);
            ConnectScreen.startConnecting(menu,mc,ServerAddress.parseString("127.0.0.1:25576"),new ServerData("Weapon animation proof","127.0.0.1:25576",ServerData.Type.OTHER),false,null);return;}
        if(mc.player==null||mc.level==null||mc.getConnection()==null||mc.screen!=null)return;
        check(mc.getSingleplayerServer()==null&&!mc.getConnection().getConnection().isMemoryConnection(),"expected dedicated server over TCP");
        if(!watching){watching=true;observe(mc);}
        if(Files.exists(SYNC.resolve("weapon-complete"))){if(pressed)click("mouseup");finished=true;System.out.println("WEAPON CLIENT PASS "+ROLE);mc.stop();return;}
        Path file=SYNC.resolve("weapon-task.properties");if(!Files.exists(file))return;
        var next=new Properties();try(var reader=Files.newBufferedReader(file)){next.load(reader);}
        int nextScene=Integer.parseInt(next.getProperty("id"));
        if(nextScene!=scene){scene=nextScene;task=next;ticks=0;frames=-1;renderFrames=0;packet=null;PACKETS.clear();pressed=false;
            mc.options.setCameraType(ROLE.equals("archer")&&task.getProperty("camera").equals("third")?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON);}
        task=next;
        var actor=mc.level.getEntity(Integer.parseInt(task.getProperty(ROLE.equals("archer")?"target":"shooter")));
        if(actor==null||mc.levelRenderer.countRenderedSections()<2)return;
        if(ROLE.equals("witness")&&(!(actor instanceof net.minecraft.world.entity.LivingEntity remote)
                ||!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(remote.getMainHandItem().getItem()).getPath().equals(task.getProperty("item"))))return;
        var dir=actor.getBoundingBox().getCenter().subtract(mc.player.getEyePosition());
        mc.player.setYRot((float)(Math.toDegrees(Math.atan2(dir.z,dir.x))-90));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(dir.y,Math.hypot(dir.x,dir.z))));
        if(ROLE.equals("archer")&&!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).getPath().equals(task.getProperty("item")))return;
        Files.writeString(SYNC.resolve("weapon-ready-"+scene+"-"+ROLE),"ready");
        if(!task.getProperty("state").equals("go"))return;
        ticks++;
        if(ROLE.equals("archer")&&ticks==12){click("mousedown");pressed=true;}
        if(ROLE.equals("archer")&&ticks==15){click("mouseup");pressed=false;}
        String nextPacket;
        while((nextPacket=PACKETS.poll())!=null){
            String[] parts=nextPacket.split("\\|",-1);
            if(parts.length==3&&Integer.toString(Integer.parseInt(task.getProperty("shooter"))).equals(parts[0])
                    &&List.of(task.getProperty("allowed").split(",")).contains(parts[1])&&packet==null){
                packet=nextPacket;frames=0;Files.writeString(SYNC.resolve("weapon-packet-"+scene+"-"+ROLE),packet);
                System.out.println("WEAPON CLIENT RECEIVED "+ROLE+" scene="+scene+" packet="+packet);
            }
        }
        if(ticks>=70&&frames>=4)Files.writeString(SYNC.resolve("weapon-done-"+scene+"-"+ROLE),"captured");
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event)throws Exception{
        if(!ACTIVE||finished||frames<0||frames>=4)return;
        if(renderFrames++%3!=0)return;
        var mc=Minecraft.getInstance();Path dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);
        try(var pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())){pixels.writeToFile(dir.resolve("weapon-"+scene+"-"+ROLE+"-"+frames+".png"));}
        frames++;
    }
}
