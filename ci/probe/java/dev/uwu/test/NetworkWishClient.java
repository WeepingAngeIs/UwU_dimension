package dev.uwu.test;

import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Physical Xvfb mouse/key events pass through GLFW and the real client's normal mappings. */
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class NetworkWishClient {
    private static final boolean ACTIVE="network-wishes".equals(System.getenv("UWU_VISIT"));
    private static final String ROLE=System.getenv().getOrDefault("WISH_ROLE","");
    private static final Path SYNC=Path.of(System.getenv().getOrDefault("WISH_SYNC_DIR","."));
    private static final List<String> messages=new ArrayList<>();
    private static PhysicalInput robot;
    private static final class PhysicalInput {
        private void send(String... command){
            var args=new ArrayList<String>();args.add("xdotool");args.addAll(List.of(command));
            try{
                var process=new ProcessBuilder(args).redirectErrorStream(true).start();
                check(process.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)&&process.exitValue()==0,"native X11 input failed "+args);
            }catch(Exception e){throw new IllegalStateException("Hosted native input",e);}
        }
        void mousePress(int button){send("mousedown",Integer.toString(button));}
        void mouseRelease(int button){send("mouseup",Integer.toString(button));}
        void keyPress(String key){send("keydown",key);}
        void keyRelease(String key){send("keyup",key);}
    }
    private static boolean connecting,finished;
    private static long start,actionStarted;
    private static int serial=-1,done=-1,ticks;
    private static Properties task;
    public static void message(String text){if(ACTIVE){messages.add(text);System.out.println("WISH CLIENT MESSAGE "+ROLE+" "+text);}}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError("WISH CLIENT "+ROLE+" "+why);}
    private static void release(){
        if(robot!=null){robot.mouseRelease(1);robot.mouseRelease(3);robot.keyRelease("Control_L");robot.keyRelease("Shift_L");}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(!ACTIVE||finished)return;
        if(start==0)start=System.nanoTime();
        check(System.nanoTime()-start<1_200_000_000_000L,"session timed out serial="+serial);
        var mc=Minecraft.getInstance();
        if(!connecting&&mc.screen instanceof TitleScreen menu){
            connecting=true;robot=new PhysicalInput();mc.options.bobView().set(false);
            ConnectScreen.startConnecting(menu,mc,ServerAddress.parseString("127.0.0.1:25576"),
                new ServerData("Wish input proof","127.0.0.1:25576",ServerData.Type.OTHER),false,null);return;
        }
        if(mc.player==null||mc.level==null||mc.getConnection()==null||mc.screen!=null)return;
        check(!mc.isLocalServer(),"requires dedicated network server");
        if(Files.exists(SYNC.resolve("complete"))){release();finished=true;System.out.println("WISH CLIENT PASS "+ROLE);mc.stop();return;}
        Path file=SYNC.resolve("task.properties");if(!Files.exists(file))return;
        var next=new Properties();try(var reader=Files.newBufferedReader(file)){next.load(reader);}
        if(!ROLE.equals(next.getProperty("role")))return;
        int id=Integer.parseInt(next.getProperty("id"));if(id==done)return;
        if(id!=serial){
            release();serial=id;ticks=0;task=next;messages.clear();actionStarted=System.nanoTime();
            String name=task.getProperty("binding","ctrl").equals("shift")?"key.keyboard.left.shift":"key.keyboard.left.control";
            mc.options.keyShift.setKey(com.mojang.blaze3d.platform.InputConstants.getKey(name));
            net.minecraft.client.KeyMapping.resetMapping();
        }
        check(System.nanoTime()-actionStarted<100_000_000_000L,"action stalled "+serial);
        Entity target=mc.level.getEntity(Integer.parseInt(task.getProperty("target","-1")));
        String action=task.getProperty("action");
        if(target!=null||action.equals("shoot")&&task.containsKey("x")){
            // Spectators are intentionally not tracked as pickable/rendered entities by the archer.
            // A negative arrow test still shoots toward their known fixture position.
            var center=target!=null?target.getBoundingBox().getCenter():new net.minecraft.world.phys.Vec3(
                Double.parseDouble(task.getProperty("x")),Double.parseDouble(task.getProperty("y")),Double.parseDouble(task.getProperty("z")));
            var direction=center.subtract(mc.player.getEyePosition());
            mc.player.setYRot((float)(Math.toDegrees(Math.atan2(direction.z,direction.x))-90));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));
        }else if(!action.equals("command")&&!action.equals("logout"))return;
        ticks++;
        String crouchKey=task.getProperty("binding","ctrl").equals("shift")?"Shift_L":"Control_L";
        if(action.equals("shoot")){
            if(ticks==8)robot.mousePress(3);
            if(ticks==40){check(mc.player.isUsingItem(),"physical right mouse did not draw bow");robot.mouseRelease(3);}
        }else if(action.equals("select")){
            if(ticks==3)robot.keyPress(crouchKey);
            if(ticks==10){check(mc.options.keyShift.isDown()&&mc.player.isShiftKeyDown(),"configured crouch mapping inactive");robot.mousePress(1);}
            if(ticks==12)robot.mouseRelease(1);
            if(ticks==18)robot.keyRelease(crouchKey);
        }else if(action.equals("rapid")){
            if(ticks>=10&&ticks<34){if(ticks%2==0)robot.mousePress(1);else robot.mouseRelease(1);}
        }else if(action.equals("attack")){
            if(ticks==10)robot.mousePress(1);
            if(ticks==12)robot.mouseRelease(1);
        }else if(action.equals("hold")||action.equals("wish-hold")){
            if(action.equals("wish-hold")&&ticks==3)robot.keyPress(crouchKey);
            if(ticks==8)robot.mousePress(1);
            if(ticks==44)robot.mouseRelease(1);
            if(action.equals("wish-hold")&&ticks==48)robot.keyRelease(crouchKey);
        }else if(action.equals("command")){
            if(ticks==8)mc.getConnection().sendCommand(task.getProperty("command"));
        }else if(action.equals("logout")){
            if(ticks==8){release();Files.writeString(SYNC.resolve("done-"+serial),"logout");finished=true;
                mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("Wish logout proof"));
                System.out.println("WISH CLIENT LOGOUT "+ROLE);mc.stop();}return;
        }else throw new AssertionError(action);
        if(ticks>=75){
            release();String expected=task.getProperty("expect","");
            check(expected.isEmpty()||messages.stream().anyMatch(m->m.contains(expected)),"missing actual server message: "+expected+" received="+messages);
            if(action.equals("select"))check(messages.stream().noneMatch(m->m.contains("No eligible target in your sights")),"crouch selection routed to attack designation");
            if(action.equals("select")){
                var dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);
                try(var pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())){pixels.writeToFile(dir.resolve("wish-"+serial+".png"));}
            }
            Files.writeString(SYNC.resolve("done-"+serial),String.join("\n",messages));done=serial;
            System.out.println("WISH PHYSICAL INPUT PASS "+ROLE+" id="+serial+" action="+action);
        }
    }
}
