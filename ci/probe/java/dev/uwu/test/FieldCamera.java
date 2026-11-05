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
    private static final String[] TOUR=SEED==152045?new String[]{"arrival","village","village-hillside","offsite-terrain","mirror-court","blush-reef","outer-reef"}:
            new String[]{"arrival","village-hillside","blush-reef","outer-reef"};
    private static volatile String state="menu";
    private static volatile SeaWatch.View view;
    private static volatile boolean stocked;
    private static volatile String counts="";
    private static volatile double launchY;
    private static long poseTime;
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
        if(name.endsWith("reef")){
            var origin=previousReef==null?arrival.offset(725,0,349):previousReef.offset(1050,0,-1120);
            var reef=SeaWatch.reef(p,origin,name);
            if(previousReef!=null && previousReef.distSqr(BlockPos.containing(reef.eye()))<512*512)throw new AssertionError("Duplicate field sites");
            previousReef=BlockPos.containing(reef.eye());SeaWatch.reset();return reef;
        }
        if(name.equals("arrival"))return frame(name,point(arrival).add(0,1.2,0),point(site.pos(0,site.entryY()+4,site.lip())));
        if(name.equals("offsite-terrain")){
            var c=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,new BlockPos(2787,0,-1117));
            return frame(name,point(c.offset(-26,55,-32)),point(c));
        }
        if(name.equals("mirror-court")){
            var bell=site.pos(-7,-17,146);
            l.getChunkAt(bell);
            p.teleportTo(l,bell.getX()+.5,bell.getY(),bell.getZ()+.5,0,0);
            l.getBlockState(bell).useWithoutItem(l,p,new BlockHitResult(point(bell),net.minecraft.core.Direction.UP,bell,false));
            return frame(name,point(site.pos(-7,-16,142)),point(site.pos(-7,-16,151)));
        }
        var village=BlossomVillage.first(l,false);var ctr=village.plaza();var vs=village.site();
        var house=village.houses().get(village.houses().size()/2);
        if(name.equals("village")){
            var door=village.doorway(house);
            var lane=vs.pos(house.x()+4,village.road()[house.x()-village.x()+29]+2,village.z());
            return frame(name,point(lane),point(door.above()));
        }
        return frame(name,point(vs.pos(village.x()-15,ctr.getY()+21,village.z()-24)),point(ctr.above(2)));
    }
    private static void place(ServerPlayer p,SeaWatch.View v){
        Vec3 d=v.subject().subtract(v.eye());
        float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));
        p.serverLevel().setDayTime(2100);p.serverLevel().setWeatherParameters(9000,0,false,false);
        p.getAbilities().invulnerable=true;p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();
        p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,20000,0,false,false));
        p.teleportTo(p.serverLevel(),v.eye().x,v.eye().y-p.getEyeHeight(),v.eye().z,yaw,pitch);
    }
    @SubscribeEvent public static void step(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();ticks++;
        if(state.equals("menu")&&mc.screen instanceof TitleScreen title){
            enter("create");
            var settings=new LevelSettings("Field Visit",GameType.SURVIVAL,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("FieldVisit",settings,new WorldOptions(SEED,true,false),WorldPresets::createNormalWorldDimensions,title);return;
        }
        if(state.equals("create")&&mc.screen!=null&&mc.player==null)
            for(var child:List.copyOf(mc.screen.children()))if(child instanceof Button b&&(b.getMessage().getString().equals("Proceed")||b.getMessage().getString().equals("I know what I'm doing!"))){b.onPress();break;}
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
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
            if(index==TOUR.length){enter("provoke");return;}
            enter("preparing");var name=TOUR[index];
            server.execute(()->{
                var p=server.getPlayerList().getPlayer(id);
                view=viewFor(p,name);stocked=false;counts="";place(p,view);enter("pose");
            });return;
        }
        if(state.equals("pose")){
            mc.options.hideGui=true;
            if(mc.screen!=null && mc.level.dimension().equals(ReefLife.REACH))mc.setScreen(null);
            if(view.sea()&&ticks%40==0)server.execute(()->{
                var p=server.getPlayerList().getPlayer(id);SeaWatch.observe(p);stocked=SeaWatch.stocked();counts=SeaWatch.counts();
            });
            if(seconds()>200)throw new AssertionError("Field visit timed out "+view.file()+" "+counts);
            if(seconds()<22||mc.player.getEyePosition().distanceToSqr(view.eye())>12||!mc.levelRenderer.isSectionCompiled(BlockPos.containing(view.subject()))
                    ||mc.levelRenderer.countRenderedSections()<4||(view.sea()&&!stocked))return;
            if(index==0)MusicProof.surface();
            if(view.sea()){MusicProof.water();System.out.println("SEA-LIFE PASS "+SEED+" "+view.file()+" "+counts);}
            Screenshot.grab(mc.gameDirectory,view.file()+".png",mc.getMainRenderTarget(),m->System.out.println(m.getString()));
            index++;enter("advance");return;
        }
        if(state.equals("provoke")){
            enter("launch");
            server.execute(()->{
                var p=server.getPlayerList().getPlayer(id);var l=p.serverLevel();var village=BlossomVillage.first(l,false);
                var h=village.houses().getFirst();var house=village.resident(h);l.getChunkAt(house);
                var nearby=l.getEntitiesOfClass(Keeper.class,new AABB(village.plaza()).inflate(40));
                if(nearby.isEmpty()){p.teleportTo(l,house.getX()+.5,house.getY(),house.getZ()+.5,0,0);enter("wait-keepers");return;}
                var keeper=nearby.getFirst();p.teleportTo(l,keeper.getX()+1.2,keeper.getY(),keeper.getZ(),0,0);
                launchY=p.getY();p.attack(keeper);
                System.out.println("PLAYER PROVOKED GUARD at "+launchY);
            });return;
        }
        if(state.equals("wait-keepers")&&ticks>60){enter("provoke");return;}
        if(state.equals("launch")){
            if(launchY>0&&mc.player.getY()-launchY>=7950){
                System.out.println("CLIENT GUARDIAN PASS | observed Y="+mc.player.getY()+" versus "+launchY);
                enter("returning");server.execute(()->use(server.getPlayerList().getPlayer(id),false));
            } else if(seconds()>160)throw new AssertionError("Client never observed the guardian launch: "+mc.player.getY()+" from "+launchY);
            return;
        }
        if(state.equals("returning")&&mc.level.dimension().equals(Level.OVERWORLD)&&ticks>60){
            if(mc.player.position().distanceToSqr(home)>30)throw new AssertionError("Locket lost saved origin");
            enter("legacy");server.execute(()->use(server.getPlayerList().getPlayer(id),true));return;
        }
        if(state.equals("legacy")&&mc.level.dimension().location().toString().equals("uwudimension:heartwater")&&ticks>60){
            enter("last-return");server.execute(()->use(server.getPlayerList().getPlayer(id),false));return;
        }
        if(state.equals("last-return")&&mc.level.dimension().equals(Level.OVERWORLD)){
            System.out.println("HEARTWATER CLIENT ROUND TRIP PASSED seed="+SEED+" with legacy locket access");enter("done");mc.stop();
        }
    }
}
