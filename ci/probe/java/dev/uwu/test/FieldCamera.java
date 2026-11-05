package dev.uwu.test;

import java.util.*;
import dev.uwu.dimension.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class FieldCamera {
    private static final long SEED=Long.parseLong(System.getenv().getOrDefault("UWU_FIELD_SEED","152045"));
    private static final boolean SEA="sea".equals(System.getenv("UWU_VISIT"));
    private static final boolean VILLAGE="village".equals(System.getenv("UWU_VISIT"));
    private static final String[] TOUR=SEA ? new String[]{"blush-reef","outer-reef","grove-water"} :
            VILLAGE?new String[]{"village","keeper-family","heart-floor","village-hillside","dusk-village"}:
            SEED==152045?new String[]{"arrival","offsite-terrain","mirror-court"}:
            new String[]{"arrival","village","village-hillside"};
    private static volatile String state="menu";
    private static volatile SeaWatch.View view;
    private static volatile boolean stocked;
    private static volatile String counts="";
    private static volatile Throwable cameraFailure;
    private static long poseTime, readySince;
    private static int ticks,index;
    private static Vec3 home;
    private static BlockPos previousReef;
    private static long seconds(){return (System.nanoTime()-poseTime)/1_000_000_000L;}
    private static void enter(String next){ticks=0;poseTime=System.nanoTime();state=next;}
    private static void use(ServerPlayer p,boolean sneak){
        p.setShiftKeyDown(sneak);
        p.getMainHandItem().use(p.level(),p,InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false);
    }
    private static Vec3 point(BlockPos p){return Vec3.atCenterOf(p);}
    private static SeaWatch.View frame(String name,Vec3 eye,Vec3 subject){return new SeaWatch.View(eye,subject,name,false);}
    private static SeaWatch.View viewFor(ServerPlayer p,String name){
        ServerLevel l=p.serverLevel();var site=GroveSites.first(l);
        var arrival=GroveSites.arrival(l);
        if(name.equals("grove-water")){SeaWatch.reset();return SeaWatch.inland(p,arrival.offset(345,0,-560));}
        if(name.endsWith("reef")){
            var origin=previousReef==null?arrival.offset(725,0,349):previousReef.offset(1050,0,-1120);
            var reef=SeaWatch.reef(p,origin,previousReef,name);
            if(previousReef!=null && previousReef.distSqr(BlockPos.containing(reef.eye()))<512*512)throw new AssertionError("Duplicate field sites");
            previousReef=BlockPos.containing(reef.eye());SeaWatch.reset();return reef;
        }
        if(name.equals("arrival"))return frame(name,point(arrival).add(0,1.2,0),point(site.pos(0,site.entryY()+4,site.lip())));
        if(name.equals("offsite-terrain")){
            l.getChunk(2787 >> 4,-1117 >> 4);
            var c=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,new BlockPos(2787,0,-1117));
            var bank=c.offset(-25,0,-27);
            l.getChunkAt(bank);
            var top=l.getHeightmapPos(Heightmap.Types.WORLD_SURFACE,bank);
            var eye=new BlockPos(bank.getX(),Math.max(c.getY()+11,top.getY()+4),bank.getZ());
            return frame(name,point(eye),point(c.above(3)));
        }
        if(name.equals("mirror-court")){
            var bell=site.pos(-7,-17,146);
            l.getChunkAt(bell);
            p.teleportTo(l,bell.getX()+.5,bell.getY(),bell.getZ()+.5,0,0);
            l.getBlockState(bell).useWithoutItem(l,p,new BlockHitResult(point(bell),net.minecraft.core.Direction.UP,bell,false));
            return frame(name,point(site.pos(-7,-16,142)),point(site.pos(-7,-16,151)));
        }
        var village=BlossomVillage.first(l,name.equals("dusk-village"));var ctr=village.plaza();var vs=village.site();
        if(name.equals("heart-floor")){
            var h=village.houses().stream().filter(v->v.style()==0||v.style()==2).findFirst().orElseThrow();
            var eye=Vec3.atBottomCenterOf(vs.pos(h.x()+h.width()-1,h.floor()+3,h.z()-h.side()*(h.depth()-1))).add(0,.18,0);
            var aim=point(vs.pos(h.x()-1,h.floor(),h.z()+h.side()));
            return frame(name,eye,aim);
        }
        if(name.equals("village") || name.equals("dusk-village")){
            var houses=village.houses();
            for(var house:houses){
                var door=village.doorway(house);
                var target=point(door.above(3));
                for(int offset:new int[]{13,-13,10,-10,7,-7}){
                    int x=house.x()+offset, i=x-village.x()+village.road().length/2;
                    if(i<0||i>=village.road().length)continue;
                    var lane=vs.pos(x,village.road()[i]+2,village.z()+village.bends()[i]);
                    l.getChunkAt(lane);l.getChunkAt(door);
                    var eye=point(lane);
                    // Keep the street in the foreground and a clear view of the entrance and roof.
                    var aim=point(door.relative(net.minecraft.core.Direction.UP,2));
                    var hit=l.clip(new ClipContext(eye,aim,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                    if(hit.getLocation().distanceToSqr(aim)<9) return frame(name,eye,target);
                }
            }
            throw new AssertionError("No unobstructed view along the generated village street at "+ctr);
        }
        return frame(name,point(vs.pos(village.x()-23,ctr.getY()+25,village.z()-32)),point(ctr.above(3)));
    }
    private static boolean sceneryReady(Minecraft mc) {
        var here=BlockPos.containing(view.eye());
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)
            if(!mc.level.hasChunk((here.getX()>>4)+dx,(here.getZ()>>4)+dz))return false;
        return mc.levelRenderer.isSectionCompiled(BlockPos.containing(view.subject())) && mc.levelRenderer.countRenderedSections()>=4;
    }
    private static void place(ServerPlayer p,SeaWatch.View v){
        Vec3 d=v.subject().subtract(v.eye());
        float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));
        p.server.overworld().setDayTime(2100);p.server.overworld().setWeatherParameters(9000,0,false,false);
        p.getAbilities().invulnerable=true;p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();
        p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,20000,0,false,false));
        p.teleportTo(p.serverLevel(),v.eye().x,v.eye().y-p.getEyeHeight(),v.eye().z,yaw,pitch);
    }
    @SubscribeEvent public static void step(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();ticks++;
        if(state.equals("closed")&&mc.level==null&&mc.getSingleplayerServer()==null){
            System.out.println("HEARTWATER CLIENT ROUND TRIP PASSED seed="+SEED+" with 1.0 and early-playtest locket access and clean disconnect");
            enter("done");mc.stop();return;
        }
        if(state.equals("menu")&&mc.screen instanceof TitleScreen title){
            enter("create");
            var settings=new LevelSettings("Field Visit",GameType.SURVIVAL,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("FieldVisit",settings,new WorldOptions(SEED,true,false),WorldPresets::createNormalWorldDimensions,title);return;
        }
        if(state.equals("create")&&mc.screen!=null&&mc.player==null)
            for(var child:List.copyOf(mc.screen.children()))if(child instanceof Button b&&(b.getMessage().getString().equals("Proceed")||b.getMessage().getString().equals("I know what I'm doing!"))){b.onPress();break;}
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(cameraFailure!=null)throw new AssertionError("Field camera stage "+state,cameraFailure);
        if(!state.equals("menu")&&!state.equals("create")&&seconds()>400)
            throw new AssertionError("Field client stalled at "+state+" visit "+index);
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
        if(state.equals("create")&&ticks>70){
            enter("arriving");server.execute(()->{
                var p=server.getPlayerList().getPlayer(id); home=p.position();
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("uwudimension:heartwater_locket"))));
                use(p,false);
            });return;
        }
        if(state.equals("arriving")&&mc.level.dimension().equals(ReefLife.REACH))enter("advance");
        if(state.equals("advance")){
            if(index==TOUR.length){
                if(SEA||VILLAGE){enter("returning");server.execute(()->use(server.getPlayerList().getPlayer(id),false));}
                else enter("provoke");
                return;
            }
            var name=TOUR[index];
            if(name.equals("keeper-family")){enter("family-wait");return;}
            enter("preparing");
            server.execute(()->{
                var p=server.getPlayerList().getPlayer(id);
                try {
                    System.out.println("SURVEY START "+name);
                    view=viewFor(p,name);readySince=0;stocked=false;counts="";place(p,view);enter("pose");
                    System.out.println("FIELD CAMERA "+name+" eye="+view.eye()+" subject="+view.subject());
                } catch(Throwable error){cameraFailure=error;}
            });return;
        }
        if(state.equals("family-wait")&&ticks%40==0){
            server.execute(()->{
                if(!state.equals("family-wait"))return;
                try{
                    var p=server.getPlayerList().getPlayer(id);
                    var portrait=FamilyCamera.arrange(p);
                    if(portrait!=null){view=portrait;readySince=0;place(p,view);enter("pose");}
                }catch(Throwable error){cameraFailure=error;}
            });
            return;
        }
        if(state.equals("pose")){
            mc.options.hideGui=true;
            if(mc.screen!=null && mc.level.dimension().equals(ReefLife.REACH))mc.setScreen(null);
            if(view.sea()&&ticks%40==0)server.execute(()->{
                var p=server.getPlayerList().getPlayer(id);boolean inland=view.file().equals("grove-water");SeaWatch.observe(p,inland);stocked=SeaWatch.stocked(inland);counts=SeaWatch.counts();
            });
            if(ticks%20==0 && mc.player.getEyePosition().distanceToSqr(view.eye())>.25)
                server.execute(()->place(server.getPlayerList().getPlayer(id),view));
            if(ticks%400==0)System.out.println("FIELD WAIT "+view.file()+" secs="+seconds()+" clientEye="+mc.player.getEyePosition()
                    +" renderSections="+mc.levelRenderer.countRenderedSections()+" mesh="+sceneryReady(mc)+" "+counts);
            if(seconds()>330)throw new AssertionError("Field visit timed out "+view.file()+" "+counts);
            if(seconds()<38||mc.player.getEyePosition().distanceToSqr(view.eye())>12||!sceneryReady(mc)){
                readySince=0;return;
            }
            if(readySince==0)readySince=System.nanoTime();
            if(System.nanoTime()-readySince<10_000_000_000L||(view.sea()&&!stocked))return;
            if(index==0&&!SEA){MusicProof.surface();WardrobeProof.verify(mc);HeartFloorClientProof.verify(mc);}
            if(view.sea()){MusicProof.water();System.out.println("SEA-LIFE PASS "+SEED+" "+view.file()+" "+counts);}
            Screenshot.grab(mc.gameDirectory,view.file()+".png",mc.getMainRenderTarget(),m->System.out.println(m.getString()));
            if(view.file().equals("keeper-family"))server.execute(FamilyCamera::release);
            index++;enter("advance");return;
        }
        if(state.equals("provoke")){
            if(!GuardianCameraProof.tick(mc))return;
            enter("returning");server.execute(()->use(server.getPlayerList().getPlayer(id),false));return;
        }
        if(state.equals("returning")&&mc.level.dimension().equals(Level.OVERWORLD)&&ticks>60){
            if(mc.player.position().distanceToSqr(home)>30)throw new AssertionError("Locket lost saved origin");
            enter("legacy");server.execute(()->use(server.getPlayerList().getPlayer(id),true));return;
        }
        if(state.equals("legacy")&&mc.level.dimension().location().toString().equals("uwudimension:heartwater")&&ticks>60){
            enter("last-return");server.execute(()->use(server.getPlayerList().getPlayer(id),false));return;
        }
        if(state.equals("last-return")&&mc.level.dimension().equals(Level.OVERWORLD)&&ticks>60){
            enter("preview");server.execute(()->{
                var p=server.getPlayerList().getPlayer(id);
                p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(net.minecraft.world.item.Items.PINK_PETALS));
                use(p,true);
            });return;
        }
        if(state.equals("preview")&&mc.level.dimension().equals(ReefLife.PREVIEW)&&ticks>60){
            enter("preview-return");server.execute(()->use(server.getPlayerList().getPlayer(id),false));return;
        }
        if(state.equals("preview-return")&&mc.level.dimension().equals(Level.OVERWORLD)){
            enter("closing");
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
            mc.setScreen(new TitleScreen());
            enter("closed");
        }
    }
}
