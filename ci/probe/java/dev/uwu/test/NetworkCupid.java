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
    private static final String OUTLINE="dev.uwu.dimension.CupidParticleClient$Ripple";
    private enum Phase { MENU, LOAD, DRAW, FLY, CLEAR, FAMILY, FINISH }
    private static Phase phase=Phase.MENU;
    private static int ticks,shot,drawPackets,trailPackets,impactPackets,chargePings,impactPings;
    private static final class Progress{
        float min=Float.MAX_VALUE,max=0,bright=0,faint=1;
        boolean observe(float size,float alpha){
            min=Math.min(min,size);max=Math.max(max,size);bright=Math.max(bright,alpha);faint=Math.min(faint,alpha);
            return max-min>.18F && bright-faint>.025F;
        }
    }
    private static net.minecraft.world.phys.Vec3 chargeAt,hitAt;
    private static int aimStable;
    private static Progress chargeGrowth=new Progress(),impactGrowth=new Progress();
    public static void sound(net.minecraft.network.protocol.game.ClientboundSoundPacket packet){
        if(!ACTIVE || (phase!=Phase.DRAW && phase!=Phase.FLY)
                || !net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(packet.getSound().value()).toString().equals("uwudimension:cupid.ripple"))return;
        if(packet.getSource()!=net.minecraft.sounds.SoundSource.PLAYERS)throw new AssertionError("Wrong Cupid ping channel");
        if(Math.abs(packet.getVolume()-.30F)<.002F)chargePings++;
        else if(Math.abs(packet.getVolume()-.19F)<.002F)impactPings++;
        else throw new AssertionError("Unexpected original spatial ping volume "+packet.getVolume());
    }
    private static final float[][] familyMeasurements=new float[4][];
    private static boolean drawPhoto,trailPhoto,impactPhoto;
    private static long started;
    private record Quad(float x,float y,float z,float size,float alpha,boolean outline,org.joml.Quaternionf facing) {}
    private static final List<Quad> quads=new ArrayList<>();

    public static void received(ClientboundLevelParticlesPacket packet) {
        if(!ACTIVE || (phase!=Phase.DRAW && phase!=Phase.FLY)) return;
        var type=packet.getParticle().getType();
        if(type!=CupidParticles.DRAW_HEART.get() && type!=CupidParticles.PASTEL_HEART.get() && type!=CupidParticles.IMPACT_OUTLINE.get()) return;
        if(!packet.isOverrideLimiter()) throw new AssertionError("Visible bow cue omitted its recipient override flag");
        if(type==CupidParticles.DRAW_HEART.get()){drawPackets++;chargeAt=new net.minecraft.world.phys.Vec3(packet.getX(),packet.getY(),packet.getZ());}
        else if(type==CupidParticles.IMPACT_OUTLINE.get()){impactPackets++;hitAt=new net.minecraft.world.phys.Vec3(packet.getX(),packet.getY(),packet.getZ());}
        else trailPackets++;
    }

    public static void quad(SingleQuadParticle particle,org.joml.Quaternionf face,float x,float y,float z,float partial) {
        if(phase!=Phase.DRAW && phase!=Phase.FLY) return;
        String type=particle.getClass().getName();
        if(type.equals(HEART) || type.equals(OUTLINE))
            quads.add(new Quad(x,y,z,particle.getQuadSize(partial),((dev.uwu.test.mixin.CupidOpacity)particle).uwuProbeOpacity(),type.equals(OUTLINE),new org.joml.Quaternionf(face)));
    }

    @SubscribeEvent public static void beginFrame(RenderFrameEvent.Pre event) {
        if(ACTIVE) {
            quads.clear();
            // Keep the actual first-person hand and hotbar; remove tutorial toasts covering the bow.
            Minecraft.getInstance().getToasts().clear();
        }
    }

    @SubscribeEvent public static void photograph(RenderFrameEvent.Post event) throws IOException {
        if(!ACTIVE)return;
        if(phase==Phase.FAMILY){
            if(ticks>12 && java.util.Arrays.stream(familyMeasurements).allMatch(v->v!=null)) {
                for(int i=0;i<2;i++){
                    var adult=familyMeasurements[i];var baby=familyMeasurements[i+2];
                    for(int n=0;n<3;n++)if(Math.abs(baby[n]/adult[n]-.5F)>.12F)
                        throw new AssertionError("Remote guardian youth dimensions "+i+" "+n+" "+baby[n]+"/"+adult[n]);
                    if(Math.abs(baby[3]/adult[3]-.3F)>.01F)throw new AssertionError("Remote guardian youth health "+baby[3]+"/"+adult[3]);
                }
                var mc=Minecraft.getInstance();
                try(var im=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    var path=mc.gameDirectory.toPath().resolve("screenshots/network-guardian-family.png");
                    Files.createDirectories(path.getParent());im.writeToFile(path);
                }
                System.out.println("NETWORK GUARDIAN FAMILY PASS | four remote original model renders, two juvenile hitboxes, eye heights and health ratios");
                phase=Phase.FINISH;mc.stop();
            }
            return;
        }
        if(quads.isEmpty()) return;
        boolean takeDraw=phase==Phase.DRAW && drawPackets==1 && !drawPhoto;
        boolean takeTrail=phase==Phase.FLY && trailPackets>=2 && !trailPhoto;
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
                if(depth<.2&&!q.outline) continue;
                int x=(int)Math.round(pixels.getWidth()*.5-offset.dot(camera.getLeftVector())*scale/depth);
                int y=(int)Math.round(pixels.getHeight()*.5-offset.dot(camera.getUpVector())*scale/depth);
                int r=(int)Math.ceil(q.size*scale/depth)+3;
                if(q.outline){
                    boolean charging=phase==Phase.DRAW;
                    var center=charging?chargeAt:hitAt;
                    if(center==null||camera.getPosition().add(q.x,q.y,q.z).distanceToSqr(center)>.01)continue;
                    int hits=0;
                    for(int sample=0;sample<32;sample++){
                        double a=Math.PI*sample/16;
                        double px=16*Math.pow(Math.sin(a),3)*1.74/32.0;
                        double py=(31.5-26.8+(13*Math.cos(a)-5*Math.cos(2*a)-2*Math.cos(3*a)-Math.cos(4*a))*1.64)/32.0;
                        var pixel=new Vector3f((float)px*q.size,(float)py*q.size,0).rotate(q.facing).add(q.x,q.y,q.z);
                        double z=pixel.dot(camera.getLookVector());
                        if(z<.12)continue;
                        int sx=(int)Math.round(pixels.getWidth()*.5-pixel.dot(camera.getLeftVector())*scale/z);
                        int sy=(int)Math.round(pixels.getHeight()*.5-pixel.dot(camera.getUpVector())*scale/z);
                        if(sx<0||sy<0||sx>=pixels.getWidth()||sy>=pixels.getHeight())continue;
                        int thickness=Math.clamp((int)Math.ceil(q.size*scale/z*.041),2,11);
                        if(pink(pixels,sx,sy,thickness))hits++;
                    }
                    // The first-person wearer looks down at the forward half of the actual ring.
                    int threshold=charging&&shot==0?3:7;
                    if(hits<threshold)continue;
                    if(charging && chargeGrowth.observe(q.size,q.alpha))drawSeen=hits;
                    if(!charging && impactGrowth.observe(q.size,q.alpha))outlineSeen=hits;
                }else if(depth>=4&&pink(pixels,x,y,r))trailSeen++;
            }
            if(takeDraw && drawSeen>0) {
                save(mc,pixels,"draw");drawPhoto=true;
            }
            if(takeTrail && trailSeen>0) {
                save(mc,pixels,"trail");trailPhoto=true;
            }
            // Require a group of distinct locations on the actually drawn perimeter.
            if(takeImpact && outlineSeen>=7) {
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
            if(drawPhoto){
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                mc.player.setXRot(3);
                aimStable++;
            }
            if(drawPhoto && aimStable>=4 && mc.player.isUsingItem() && ticks>=26) {
                phase=Phase.FLY; ticks=0;
                mc.options.keyUse.setDown(false);
                mc.gameMode.releaseUsingItem(mc.player);
            }
        } else if(phase==Phase.FLY && impactPhoto && trailPhoto && ticks>=12) {
            if(drawPackets!=1 || impactPackets!=1 || trailPackets<1 || chargePings!=1 || impactPings!=1)
                throw new AssertionError("Missing or repeated remote cues");
            System.out.println("NETWORK CUPID SHOT PASS "+(shot+1)+" draw="+drawPackets+" trail="+trailPackets+" outline="+impactPackets);
            System.out.println("NETWORK CUPID RIPPLE PASS shot="+(shot+1)+" chargeSize="+chargeGrowth.min+".."+chargeGrowth.max
                    +" impactSize="+impactGrowth.min+".."+impactGrowth.max+" impactAlpha="+impactGrowth.bright+".."+impactGrowth.faint
                    +" spatialPings="+chargePings+"/"+impactPings);
            phase=Phase.CLEAR;ticks=0;
        } else if(phase==Phase.CLEAR && ticks>60) {
            if(shot==0) {shot=1;beginShot(mc);}
            else {
                if(mc.options.particles().get()!=ParticleStatus.MINIMAL) throw new AssertionError("Minimal mode changed");
                phase=Phase.FAMILY;ticks=0;
                mc.player.setYRot(90);mc.player.setXRot(4);
                System.out.println("NETWORK CUPID VISUAL PASS | real TCP server, first-person shooter and ordinary elevated rear charge view, Minimal + Smart Particles + Particle Core, six framebuffer captures");
            }
        }
    }

    @SubscribeEvent public static void guardianRendered(net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?,?> e){
        if(!ACTIVE || phase!=Phase.FAMILY || !(e.getEntity() instanceof dev.uwu.dimension.BlossomWarden g))return;
        if(!(e.getRenderer() instanceof dev.uwu.dimension.BlossomWardenRenderer renderer))
            throw new AssertionError("Expected native guardian renderer");
        boolean femaleTexture=renderer.getTextureLocation(g).getPath().contains("female_uwu_guardian");
        if(g.female()!=femaleTexture)throw new AssertionError("Remote guardian sex and texture differ");
        familyMeasurements[(g.isBaby()?2:0)+(g.female()?1:0)]=new float[]{g.getBbWidth(),g.getBbHeight(),g.getEyeHeight(),g.getMaxHealth()};
    }

    private static void beginShot(Minecraft mc) {
        ticks=drawPackets=trailPackets=impactPackets=chargePings=impactPings=0;
        chargeGrowth=new Progress();impactGrowth=new Progress();chargeAt=hitAt=null;aimStable=0;
        drawPhoto=trailPhoto=impactPhoto=false;
        phase=Phase.DRAW;
        mc.options.hideGui=false;
        mc.player.setYRot(shot==0?0:-18);
        mc.options.setCameraType(shot==0?net.minecraft.client.CameraType.FIRST_PERSON:net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        mc.player.setXRot(shot==0?53:40);
        mc.options.keyUse.setDown(true);
        mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
    }
}
