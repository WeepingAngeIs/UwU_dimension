package dev.uwu.test;

import dev.uwu.dimension.GlitchBow;
import dev.uwu.dimension.RelicFlight;
import dev.uwu.dimension.UwUDimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Captures actual remote equipment and projectile data on a second real client. */
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class NetworkGlitchClient {
    private static final boolean ACTIVE=NetworkGlitchServer.SESSION.equals(System.getenv("UWU_VISIT"));
    private static final String ROLE=System.getenv().getOrDefault("GLITCH_ROLE","");
    private static final Path SYNC=Path.of(System.getenv().getOrDefault("GLITCH_SYNC_DIR","."));
    private static boolean connecting,shot,sawFlight,finished;
    private static String capture;
    private static long start;

    private static void check(boolean ok,String what){if(!ok)throw new AssertionError("Network Glitch "+ROLE+": "+what);}
    private static void modelMatches(Minecraft mc,ItemStack stack,net.minecraft.world.entity.LivingEntity holder,String label){
        ItemStack nativeStack=GlitchBow.mimicStack(stack);
        check(!nativeStack.isEmpty(),label+" has no corresponding form");
        var actual=mc.getItemRenderer().getModel(stack,mc.level,holder,0);
        var expected=mc.getItemRenderer().getModel(nativeStack,mc.level,holder,0);
        check(actual==expected,label+" selected a different baked model from the real form");
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event)throws IOException{
        if(!ACTIVE||capture==null)return;
        var mc=Minecraft.getInstance();
        String kind=capture;capture=null;
        Path dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            image.writeToFile(dir.resolve("network-glitch-"+kind+".png"));
        }
        Files.createDirectories(SYNC);
        Files.writeString(SYNC.resolve(kind+".seen"),kind);
        System.out.println("NETWORK GLITCH OBSERVER FRAME "+kind);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ACTIVE||finished)return;
        if(start==0)start=System.nanoTime();
        check(System.nanoTime()-start<480_000_000_000L,"timed out waiting for remote held/flight/advanced states");
        var mc=Minecraft.getInstance();
        if(!connecting&&mc.screen instanceof TitleScreen menu){
            connecting=true;mc.options.bobView().set(false);mc.options.hideGui=true;
            String host="127.0.0.1:25575";
            ConnectScreen.startConnecting(menu,mc,ServerAddress.parseString(host),new ServerData("Glitch test",host,ServerData.Type.OTHER),false,null);
            return;
        }
        if(mc.level==null||mc.player==null||mc.gameMode==null||mc.screen!=null)return;
        check(mc.getSingleplayerServer()==null&&!mc.isLocalServer(),"must use a real remote server");
        if(ROLE.equals("shooter")){
            if(!shot&&Files.exists(SYNC.resolve("held.seen"))&&mc.player.getMainHandItem().is(UwUDimension.GLITCH.get())
                    &&GlitchBow.form(mc.player.getMainHandItem()).equals(NetworkGlitchServer.FORM)){
                shot=true;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
                System.out.println("NETWORK GLITCH SHOOTER used selected Twin Chakram through normal client interaction");
            }
            if(Files.exists(SYNC.resolve("changed.seen"))){finished=true;System.out.println("NETWORK GLITCH SHOOTER PASS");mc.stop();}
            return;
        }
        check(ROLE.equals("observer"),"unknown role");
        Player remote=mc.level.players().stream().filter(p->p.getGameProfile().getName().equals(NetworkGlitchServer.SHOOTER)).findFirst().orElse(null);
        if(remote==null||mc.levelRenderer.countRenderedSections()<2)return;
        ItemStack held=remote.getMainHandItem();
        if(!Files.exists(SYNC.resolve("held.seen"))&&capture==null&&held.is(UwUDimension.GLITCH.get())&&GlitchBow.form(held).equals(NetworkGlitchServer.FORM)){
            modelMatches(mc,held,remote,"remote Twin Chakram");
            capture="held";return;
        }
        for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof RelicFlight flight)||flight.wave()||!flight.getItem().is(UwUDimension.GLITCH.get()))continue;
            check(GlitchBow.form(flight.getItem()).equals(NetworkGlitchServer.FORM),"in-flight remote stack changed form");
            modelMatches(mc,flight.getItem(),null,"remote in-flight Twin Chakram");
            if(!Files.exists(SYNC.resolve("flight.seen"))&&capture==null){capture="flight";sawFlight=true;return;}
        }
        if(sawFlight&&Files.exists(SYNC.resolve("flight.seen"))&&held.is(UwUDimension.GLITCH.get())
                &&!GlitchBow.form(held).equals(NetworkGlitchServer.FORM)&&capture==null){
            modelMatches(mc,held,remote,"remote changed form");capture="changed";
            System.out.println("NETWORK GLITCH OBSERVER PASS remote held, physical projectile, and changed form="+GlitchBow.form(held));
        }
        if(Files.exists(SYNC.resolve("changed.seen"))){finished=true;mc.stop();}
    }
}
