package dev.uwu.test;

import com.mojang.blaze3d.platform.NativeImage;
import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.CupidParticles;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Vector3f;

/** A remote shooter fires twice. Only the ordinary multiplayer controller sends game actions. */
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class NetworkCupid {
    public static final boolean ACTIVE="network-cupid".equals(System.getenv("UWU_VISIT"));
    private static final String HEART="dev.uwu.dimension.CupidParticleClient$Heart";
    private static final String OUTLINE="dev.uwu.dimension.CupidParticleClient$Outline";
    private enum Phase { MENU, LOAD, DRAW, FLY, CLEAR, FINISH }
    private static Phase phase=Phase.MENU;
    private static int ticks,shot,drawPackets,trailPackets,impactPackets;
    private static boolean drawPhoto,trailPhoto,impactPhoto;
    private static long started;
    private record Quad(float x,float y,float z,float size,boolean outline) {}
    private static final List<Quad> quads=new ArrayList<>();

    public static void received(ClientboundLevelParticlesPacket packet) {
        if(!ACTIVE || (phase!=Phase.DRAW && phase!=Phase.FLY)) return;
        var type=packet.getParticle().getType();
        if(type!=CupidParticles.DRAW_HEART.get() && type!=CupidParticles.PASTEL_HEART.get() && type!=CupidParticles.IMPACT_OUTLINE.get()) return;
        if(!packet.isOverrideLimiter()) throw new AssertionError("Visible bow cue omitted its recipient override flag");
        if(type==CupidParticles.DRAW_HEART.get()) drawPackets++;
        else if(type==CupidParticles.IMPACT_OUTLINE.get()) impactPackets++;
        else trailPackets++;
    }

    public static void quad(SingleQuadParticle particle,float x,float y,float z,float partial) {
        if(phase!=Phase.DRAW && phase!=Phase.FLY) return;
        String type=particle.getClass().getName();
        if(type.equals(HEART) || type.equals(OUTLINE))
            quads.add(new Quad(x,y,z,particle.getQuadSize(partial),type.equals(OUTLINE)));
    }

    @SubscribeEvent public static void beginFrame(RenderFrameEvent.Pre event) {
        if(ACTIVE) quads.clear();
    }

    @SubscribeEvent public static void photograph(RenderFrameEvent.Post event) throws IOException {
        if(!ACTIVE || quads.isEmpty()) return;
        boolean takeDraw=phase==Phase.DRAW && drawPackets==1 && !drawPhoto;
        boolean takeTrail=phase==Phase.FLY && trailPackets>0 && !trailPhoto;
        boolean takeImpact=phase==Phase.FLY && impactPackets==1 && !impactPhoto;
        if(!takeDraw && !takeTrail && !takeImpact) return;
        var mc=Minecraft.getInstance();
        try(var pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int drawSeen=0,trailSeen=0,outlineSeen=0;
            var camera=mc.gameRenderer.getMainCamera();
            double scale=pixels.getHeight()/(2*Math.tan(Math.toRadians(mc.options.fov().get()/2.0)));
            for(var q:quads) {
                var offset=new Vector3f(q.x,q.y,q.z);
                double depth=offset.dot(camera.getLookVector());
                if(depth<.2) continue;
                int x=(int)Math.round(pixels.getWidth()*.5-offset.dot(camera.getLeftVector())*scale/depth);
                int y=(int)Math.round(pixels.getHeight()*.5-offset.dot(camera.getUpVector())*scale/depth);
                int r=(int)Math.ceil(q.size*scale/depth)+3;
                if(!pink(pixels,x,y,r)) continue;
                if(q.outline) outlineSeen++;
                else if(depth<=1.5) drawSeen++;
                else trailSeen++;
            }
            if(takeDraw && drawSeen>0) {
                save(mc,pixels,"draw");drawPhoto=true;
            }
            if(takeTrail && trailSeen>0) {
                save(mc,pixels,"trail");trailPhoto=true;
            }
            // Require a group of distinct locations on the actually drawn perimeter.
            if(takeImpact && outlineSeen>=8) {
                save(mc,pixels,shot==0?"entity-impact":"block-impact");impactPhoto=true;
                System.out.println("NETWORK CUPID VISIBLE OUTLINE | pink pixel regions="+outlineSeen);
            }
        }
    }

    private static boolean pink(NativeImage im,int x,int y,int r) {
        int count=0;
        for(int j=Math.max(0,y-r);j<=Math.min(im.getHeight()-1,y+r);j++)
            for(int i=Math.max(0,x-r);i<=Math.min(im.getWidth()-1,x+r);i++) {
                int abgr=im.getPixelRGBA(i,j);
                int red=abgr&255, green=(abgr>>>8)&255, blue=(abgr>>>16)&255;
                if(red>=158 && red-green>=21 && blue-green>=9 && red-blue>=9 && ++count>=2) return true;
            }
        return false;
    }

    private static void save(Minecraft mc,NativeImage im,String stage) throws IOException {
        var dir=mc.gameDirectory.toPath().resolve("screenshots");
        Files.createDirectories(dir);
        String name="network-cupid-"+(shot+1)+"-"+stage+".png";
        im.writeToFile(dir.resolve(name));
        System.out.println("NETWORK CUPID FRAME "+name+" quads="+quads.size());
    }

    @SubscribeEvent public static void step(ClientTickEvent.Post event) {
        if(!ACTIVE)return;
        if(started==0) started=System.nanoTime();
        if((System.nanoTime()-started)>300_000_000_000L)
            throw new AssertionError("Network Cupid timed out: "+phase+" shot="+shot+" packets="+drawPackets+"/"+trailPackets+"/"+impactPackets+
                    " photographed="+drawPhoto+"/"+trailPhoto+"/"+impactPhoto);
        var mc=Minecraft.getInstance();ticks++;
        if(phase==Phase.MENU && mc.screen instanceof TitleScreen menu) {
            mc.options.particles().set(ParticleStatus.MINIMAL);
            mc.options.bobView().set(false);
            mc.options.fovEffectScale().set(0D);
            if(!ModList.get().isLoaded("smart_particles") || !ModList.get().isLoaded("particle_core"))
                throw new AssertionError("Both TEST profile particle optimizers must be active");
            String host="127.0.0.1:25574";
            phase=Phase.LOAD;
            ConnectScreen.startConnecting(menu,mc,ServerAddress.parseString(host),new ServerData("Cupid server",host,ServerData.Type.OTHER),false,null);
            return;
        }
        if(mc.player==null || mc.gameMode==null || mc.level==null) return;
        if(mc.getSingleplayerServer()!=null || mc.isLocalServer()) throw new AssertionError("Requires a real remote connection");
        if(phase==Phase.LOAD && mc.player.getMainHandItem().is(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get()) &&
                mc.player.getY()>NetworkCupidServer.FLOOR && mc.screen==null && mc.levelRenderer.countRenderedSections()>2){
            beginShot(mc);return;
        }
        if(phase==Phase.DRAW) {
            if(drawPackets>1) throw new AssertionError("Full draw emitted twice");
            if(drawPhoto && mc.player.isUsingItem() && ticks>=26) {
                phase=Phase.FLY; ticks=0;
                mc.options.keyUse.setDown(false);
                mc.gameMode.releaseUsingItem(mc.player);
            }
        } else if(phase==Phase.FLY && impactPhoto && trailPhoto && ticks>=12) {
            if(drawPackets!=1 || impactPackets!=1 || trailPackets<1)
                throw new AssertionError("Missing or repeated remote cues");
            System.out.println("NETWORK CUPID SHOT PASS "+(shot+1)+" draw="+drawPackets+" trail="+trailPackets+" outline="+impactPackets);
            phase=Phase.CLEAR;ticks=0;
        } else if(phase==Phase.CLEAR && ticks>60) {
            if(shot==0) {shot=1;beginShot(mc);}
            else {
                if(mc.options.particles().get()!=ParticleStatus.MINIMAL) throw new AssertionError("Minimal mode changed");
                phase=Phase.FINISH;
                System.out.println("NETWORK CUPID VISUAL PASS | real TCP server, first-person shooter, Minimal + Smart Particles + Particle Core, six framebuffer captures");
                mc.stop();
            }
        }
    }

    private static void beginShot(Minecraft mc) {
        ticks=drawPackets=trailPackets=impactPackets=0;
        drawPhoto=trailPhoto=impactPhoto=false;
        phase=Phase.DRAW;
        mc.options.hideGui=true;
        mc.player.setYRot(shot==0?0:-18);
        mc.player.setXRot(3);
        mc.options.keyUse.setDown(true);
        mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
    }
}
