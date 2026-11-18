package dev.uwu.test;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class GripReviewClient {
    private static final boolean ACTIVE=System.getenv("UWU_GRIP_VARIANT")!=null;
    private static final String ROLE=System.getenv().getOrDefault("WISH_ROLE","");
    private static final boolean SHOOTER=ROLE.equals("archer");
    private static final Path SYNC=Path.of(System.getenv().getOrDefault("WISH_SYNC_DIR","."));
    private static final int[] PHASES={1,3,5,7,10,14};
    private static final AtomicInteger PACKET_ACTOR=new AtomicInteger(-1);
    private static int scene=-1,settle,ticks,attack=-1,photos;
    private static boolean connecting,observing,finished,idle,go,done;
    private static Properties task;
    private static net.minecraft.world.entity.decoration.ArmorStand cameraAnchor;
    private static int updates;
    private static String trigger;
    private static String vesperPlayback;
    private static void status(String detail) throws Exception {
        if ((updates % 30) != 1) return;
        String message="scene="+scene+" role="+ROLE+" "+detail+" settle="+settle+" ticks="+ticks+" attack="+attack+" idle="+idle+" photos="+photos;
        Files.writeString(SYNC.resolve("grip-"+ROLE+"-status.txt"),message);
        System.out.println("GRIP PROGRESS "+message);
    }

    private static void observePacket(Object message) throws ReflectiveOperationException {
        if(message instanceof ClientboundBundlePacket bundle){
            for(var packet:bundle.subPackets())observePacket(packet);
        } else if(message instanceof ClientboundAnimatePacket packet && (packet.getAction()==0||packet.getAction()==3)){
            PACKET_ACTOR.set(packet.getId());
        } else if(message instanceof ClientboundCustomPayloadPacket packet){
            var payload=packet.payload();
            if(payload.getClass().getName().equals("net.bettercombat.network.Packets$AttackAnimation"))
                PACKET_ACTOR.set((Integer)payload.getClass().getMethod("playerId").invoke(payload));
        }
    }
    private static boolean captureThisView() {
        return !SHOOTER || switch (task.getProperty("view")) {
            case "front", "back", "left-side" -> true;
            default -> false;
        };
    }
    private static CameraType cameraType() {
        if (!SHOOTER) return CameraType.FIRST_PERSON;
        return switch (task.getProperty("view")) {
            case "front" -> CameraType.THIRD_PERSON_FRONT;
            case "back" -> CameraType.THIRD_PERSON_BACK;
            default -> CameraType.FIRST_PERSON;
        };
    }
    private static void click(String action)throws Exception{
        var process=new ProcessBuilder("xdotool",action,"1").redirectErrorStream(true).start();
        if(!process.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)||process.exitValue()!=0)throw new AssertionError(action);
    }
    private static void signal(String action)throws Exception{
        Files.writeString(SYNC.resolve("grip-"+scene+"-"+action+"-"+ROLE),"ok");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!ACTIVE||finished)return;
        var mc=Minecraft.getInstance();
        if(!connecting&&mc.screen instanceof TitleScreen menu){
            connecting=true;mc.options.bobView().set(false);
            mc.options.fov().set(SHOOTER?70:48);
            if (!SHOOTER) mc.options.fovEffectScale().set(0.0D);
            ConnectScreen.startConnecting(menu,mc,ServerAddress.parseString("127.0.0.1:25576"),
                    new ServerData("Grip comparison","127.0.0.1:25576",ServerData.Type.OTHER),false,null);return;
        }
        if(mc.player==null||mc.level==null||mc.getConnection()==null||mc.screen!=null)return;
        if(!observing){
            observing=true;
            var channel=mc.getConnection().getConnection().channel();
            channel.eventLoop().execute(()->channel.pipeline().addBefore("packet_handler","grip_animation_observer",new ChannelInboundHandlerAdapter(){
                @Override public void channelRead(ChannelHandlerContext ctx,Object msg)throws Exception{
                    observePacket(msg);super.channelRead(ctx,msg);
                }
            }));
        }
        if(Files.exists(SYNC.resolve("polish-complete"))){
            finished=true;System.out.println("POLISH CLIENT PASS "+ROLE+" grip photos recorded");mc.stop();return;
        }
        Path path=SYNC.resolve("grip.properties");if(!Files.exists(path))return;
        var next=new Properties();try(var reader=Files.newBufferedReader(path)){next.load(reader);}
        int number=Integer.parseInt(next.getProperty("id"));
        if(number!=scene){
            scene=number;settle=ticks=photos=0;attack=-1;idle=go=done=false;PACKET_ACTOR.set(-1);
            vesperPlayback=null;
        }
        task=next;
        if(done)return;
        updates++;

        if(!(mc.level.getEntity(Integer.parseInt(task.getProperty("shooter"))) instanceof Player actor)){status("awaiting shooter entity");return;}
        var remote=mc.level.players().stream().filter(p->p!=mc.player).findFirst().orElse(null);
        if(remote==null||!System.getenv("UWU_GRIP_VARIANT").startsWith("native-")&&!FiguraPivotProof.ready(mc,remote.getUUID())){status("awaiting Figura fixture");return;}
        mc.options.setCameraType(cameraType());
        boolean originalDiagonal = task.getProperty("view").equals("original-diagonal");
        mc.options.hideGui=!SHOOTER && !originalDiagonal;
        if (!SHOOTER) mc.options.fov().set(originalDiagonal ? 70 : 48);
        var arm=task.getProperty("hand").equals("left")?HumanoidArm.LEFT:HumanoidArm.RIGHT;
        boolean offhand=task.getProperty("hand").equals("offhand");
        if(SHOOTER){
            if(mc.options.mainHand().get()!=arm){mc.options.mainHand().set(arm);mc.options.broadcastOptions();}
            mc.player.setYRot(0);mc.player.setXRot(0);
        }else{
            Vec3 eye=new Vec3(Double.parseDouble(task.getProperty("eyeX")),Double.parseDouble(task.getProperty("eyeY")),Double.parseDouble(task.getProperty("eyeZ")));
            // A client-only armor stand is a camera anchor, never spawned or rendered.
            // The network witness stays well outside both the stills and local F5 shots.
            if(cameraAnchor==null)cameraAnchor=net.minecraft.world.entity.EntityType.ARMOR_STAND.create(mc.level);
            Vec3 direction=actor.position().add(0,originalDiagonal ? 1.1 : 1.08,0).subtract(eye);
            float yaw=(float)(Math.toDegrees(Math.atan2(direction.z,direction.x))-90);
            float pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));
            cameraAnchor.moveTo(eye.x,eye.y-cameraAnchor.getEyeHeight(),eye.z,yaw,pitch);
            cameraAnchor.setYHeadRot(yaw);
            cameraAnchor.yHeadRotO=yaw;
            cameraAnchor.setOldPosAndRot();
            if(mc.getCameraEntity()!=cameraAnchor)mc.setCameraEntity(cameraAnchor);
        }
        var item=offhand?actor.getOffhandItem():actor.getMainHandItem();
        String expected=task.getProperty("item");
        String expectedID=expected.contains(":")?expected:"uwudimension:"+expected;
        if(!BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals(expectedID)||actor.getMainArm()!=arm){
            status("awaiting equipment actual="+item+" arm="+actor.getMainArm()+" expected="+expectedID+" "+arm);
            return;
        }
        if ("snowkiss".equals(System.getenv("UWU_GRIP_SUITE"))) {
            boolean shield=task.getProperty("hand").equals("right-shield");
            var other=offhand?actor.getMainHandItem():actor.getOffhandItem();
            boolean paired = shield ? other.is(net.minecraft.world.item.Items.SHIELD) : other.isEmpty();
            if (!paired) {
                status("awaiting paired equipment actual="+other+" shield="+shield);
                return;
            }
        }
        status("camera and equipment ready");
        settle++;
        if(!task.getProperty("state").equals("go")){
            if(idle)signal("ready");
            return;
        }
        go=true;ticks++;
        boolean restingOnly=offhand || task.getProperty("item").equals("simplyswords:soulrender");
        if(!restingOnly){
            if(SHOOTER&&ticks==10)click("mousedown");
            if(SHOOTER&&ticks==12)click("mouseup");
            if(attack<0 && (PACKET_ACTOR.get()==actor.getId() || actor.swinging || SHOOTER && ticks==11)){
                attack=ticks;
                trigger=PACKET_ACTOR.get()==actor.getId()?"network-animation":actor.swinging?"native-arm-swing":"physical-attack-key";
                System.out.println("GRIP ANIMATION "+scene+" "+ROLE+" "+trigger);
            }
            if (expected.equals("vesper_scythe") && attack>=0 && vesperPlayback==null) {
                boolean solo = System.getenv("UWU_GRIP_VARIANT").endsWith("after")
                        && !(SHOOTER && cameraType().isFirstPerson());
                vesperPlayback=GripPlaybackProof.inspect(actor, solo);
                if(vesperPlayback!=null)System.out.println("GRIP PLAYBACK scene="+scene+" role="+ROLE
                        +" primary="+actor.getMainArm()+" camera="+task.getProperty("view")
                        +" localPerspective="+cameraType()+" actual="+vesperPlayback);
            }
        }
        if(ticks>65 && (restingOnly||attack>=0&&photos==PHASES.length || !captureThisView())){
            if(expected.equals("vesper_scythe") && vesperPlayback==null)
                throw new AssertionError("No live Vesper attack clip on "+ROLE+" in "+task);
            signal("done");done=true;return;
        }
        if(ticks>190)throw new AssertionError("Missing physical swing capture "+ROLE+" "+task+" packet="+PACKET_ACTOR+" photos="+photos);
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event)throws Exception{
        if(!ACTIVE||finished||done||task==null||settle<15)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return;
        if(!idle){
            if(captureThisView())capture(mc,"rest");
            idle=true;return;
        }
        if(!go||attack<0||photos>=PHASES.length||!captureThisView())return;
        int age=ticks-attack;
        if(age>=PHASES[photos]){
            capture(mc,"attack-"+String.format(java.util.Locale.ROOT,"%02d",PHASES[photos]));
            System.out.println("GRIP CAPTURE scene="+scene+" role="+ROLE+" swingAge="+age+" targetPhase="+PHASES[photos]+" trigger="+trigger);
            photos++;
        }
    }
    private static void capture(Minecraft mc,String pose)throws Exception{
        Path dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);
        String angle=SHOOTER ? switch(cameraType()) {
            case THIRD_PERSON_FRONT -> "f5-front";
            case THIRD_PERSON_BACK -> "f5-back";
            default -> "first-person";
        } : task.getProperty("view");
        String name=System.getenv("UWU_GRIP_VARIANT")+"_"+task.getProperty("item").replace(':','-')+"_"+task.getProperty("hand")+"_"+angle+"_"+pose+".png";
        try(var pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())){pixels.writeToFile(dir.resolve(name));}
    }
}

