package dev.uwu.test;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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
    private static Robot robot;
    private static boolean connecting,finished;
    private static long start;
    private static int serial=-1,done=-1,ticks;
    private static Properties task;
    public static void message(String text){if(ACTIVE){messages.add(text);System.out.println("WISH CLIENT MESSAGE "+ROLE+" "+text);}}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError("WISH CLIENT "+ROLE+" "+why);}
    private static void release(){
        if(robot!=null){robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK|InputEvent.BUTTON3_DOWN_MASK);robot.keyRelease(KeyEvent.VK_CONTROL);robot.keyRelease(KeyEvent.VK_SHIFT);}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(!ACTIVE||finished)return;
        if(start==0)start=System.nanoTime();
        check(System.nanoTime()-start<1_200_000_000_000L,"session timed out serial="+serial);
        var mc=Minecraft.getInstance();
        if(!connecting&&mc.screen instanceof TitleScreen menu){
            connecting=true;robot=new Robot();mc.options.bobView().set(false);
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
            release();serial=id;ticks=0;task=next;messages.clear();
            String name=task.getProperty("binding","ctrl").equals("shift")?"key.keyboard.left.shift":"key.keyboard.left.control";
            mc.options.keyShift.setKey(com.mojang.blaze3d.platform.InputConstants.getKey(name));
            net.minecraft.client.KeyMapping.resetMapping();
        }
        Entity target=mc.level.getEntity(Integer.parseInt(task.getProperty("target","-1")));
        String action=task.getProperty("action");
        if(target!=null){
            var direction=target.getBoundingBox().getCenter().subtract(mc.player.getEyePosition());
            mc.player.setYRot((float)(Math.toDegrees(Math.atan2(direction.z,direction.x))-90));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));
        }else if(!action.equals("command")&&!action.equals("logout"))return;
        ticks++;
        int crouchKey=task.getProperty("binding","ctrl").equals("shift")?KeyEvent.VK_SHIFT:KeyEvent.VK_CONTROL;
        if(action.equals("shoot")){
            if(ticks==8)robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            if(ticks==40){check(mc.player.isUsingItem(),"physical right mouse did not draw bow");robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);}
        }else if(action.equals("select")){
            if(ticks==3)robot.keyPress(crouchKey);
            if(ticks==10){check(mc.options.keyShift.isDown()&&mc.player.isShiftKeyDown(),"configured crouch mapping inactive");robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);}
            if(ticks==12)robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            if(ticks==18)robot.keyRelease(crouchKey);
        }else if(action.equals("rapid")){
            if(ticks>=10&&ticks<34){if(ticks%2==0)robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);else robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);}
        }else if(action.equals("attack")){
            if(ticks==10)robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            if(ticks==12)robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }else if(action.equals("hold")){
            if(ticks==8)robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            if(ticks==44)robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
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
