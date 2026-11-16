package dev.uwu.test;

import dev.uwu.dimension.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Set up fixtures only. Every charm, command and selection is sent by a genuine remote client. */
@EventBusSubscriber(modid="heartwater_probe")
public final class NetworkWishServer {
    private static final boolean ACTIVE="network-wishes".equals(System.getenv("UWU_VISIT"));
    private static final Path SYNC=Path.of(System.getenv().getOrDefault("WISH_SYNC_DIR","."));
    private static final String OWNER="uwudimension.charm_owner",END="uwudimension.charm_end";
    private record Action(String role,String kind,LivingEntity target,String expect,Runnable before,Runnable verify){}
    private static final List<Action> actions=new ArrayList<>();
    private static final List<EntityType<?>> babies=new ArrayList<>();
    private static boolean ready,issued,finished,logic;
    private static int index,afterLogout;
    private static long cue=-1,started;
    private static ServerPlayer archer,witness;
    private static Animal cow,second,sheep;
    private static MinecraftServer host;
    private static float health;
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError("NETWORK WISH "+why);}
    private static void run(Runnable r){if(r!=null)r.run();}
    private static void action(String role,String kind,LivingEntity target,String expect,Runnable before,Runnable verify){actions.add(new Action(role,kind,target,expect,before,verify));}
    private static void arrow(LivingEntity target,Runnable before,Runnable verify){action("archer","shoot",target,"",before,verify);}
    private static void select(LivingEntity target,String message,Runnable verify){action("archer","select",target,message,null,verify);}
    private static void refresh(){
        archer.getPersistentData().remove("uwudimension.wish_archer_until");
        for(var e:List.of(cow,second,sheep,witness)){
            e.getPersistentData().remove("uwudimension.wish_rest_until");
            if(e instanceof AgeableMob age)age.setAge(0);
        }
    }
    private static boolean charmed(LivingEntity e){return e.getPersistentData().hasUUID(OWNER)&&archer.getUUID().equals(e.getPersistentData().getUUID(OWNER))&&e.getPersistentData().getLong(END)>e.level().getGameTime();}
    private static void births(int count,EntityType<?> type){
        check(babies.size()==count,"baby count "+babies+" expected "+count);
        if(type!=null)check(babies.getLast()==type,"wrong young creature type "+babies.getLast());
    }
    private static Animal animal(EntityType<? extends Animal> type,double x){
        var a=type.create(host.overworld());check(a!=null,"missing animal");a.setNoAi(true);a.setPersistenceRequired();
        a.moveTo(x,113,5.5,180,0);check(host.overworld().addFreshEntity(a),"animal insertion");return a;
    }
    private static void resetWitness(GameType type){
        // A real mode switch makes the production minion tick release prior charm.
        witness.setGameMode(type);witness.teleportTo(host.overworld(),.5,113,5.5,180,0);
    }
    static void tick(MinecraftServer server)throws Exception {
        if(!ACTIVE||finished)return;
        if(!ready){
            archer=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals("WishArcher")).findFirst().orElse(null);
            witness=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals("WishWitness")).findFirst().orElse(null);
            if(archer==null||witness==null)return;
            host=server;started=System.nanoTime();server.setPvpAllowed(true);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gamerule doMobSpawning false");
            for(int x=-10;x<=10;x++)for(int z=-5;z<=18;z++){
                server.overworld().setBlockAndUpdate(new BlockPos(x,112,z),Blocks.STONE.defaultBlockState());
                if(z==17)for(int y=113;y<=119;y++)server.overworld().setBlockAndUpdate(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState());
            }
            archer.setGameMode(GameType.SURVIVAL);resetWitness(GameType.SURVIVAL);
            archer.teleportTo(server.overworld(),.5,113,.5,0,0);
            archer.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get()));
            archer.containerMenu.broadcastFullState();cow=animal(EntityType.COW,-1.5);second=animal(EntityType.COW,2.5);sheep=animal(EntityType.SHEEP,4.5);
            health=cow.getHealth();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CupidPairEvent.class,e->{if(e.archer==archer&&cue<0)cue=server.overworld().getGameTime();});
            action("archer","rapid",cow,"",()->cow.moveTo(.5,113,2,0,0),()->check(cow.getHealth()==health,"rapid mouse clicks dealt accidental melee damage"));
            action("archer","hold",cow,"",null,()->{check(cow.getHealth()==health,"held left mouse dealt accidental melee damage");cow.moveTo(-1.5,113,5.5,0,0);});
            arrow(cow,null,()->check(charmed(cow),"first cow not charmed by physical arrow"));
            arrow(second,null,()->check(charmed(second),"second cow not charmed"));
            select(cow,"First wish target",null);select(second,"Two hearts answered",()->births(1,EntityType.COW));
            action("archer","hold",cow,"",null,()->births(1,EntityType.COW));
            arrow(cow,NetworkWishServer::refresh,()->check(charmed(cow),"cow refresh"));
            arrow(sheep,null,()->check(charmed(sheep),"sheep charm"));
            select(cow,"First wish target",null);select(sheep,"Two hearts answered",()->{births(2,null);check(List.of(EntityType.COW,EntityType.SHEEP).contains(babies.getLast()),"cross-species young");});
            arrow(witness,NetworkWishServer::refresh,()->{check(charmed(witness),"survival player did not charm");check(witness.position().distanceTo(archer.position())<5,"remote minion failed to follow");});
            arrow(cow,null,()->check(charmed(cow),"cow charm before player wish"));
            select(witness,"Choose an available adult",()->births(2,null));
            action("witness","command",null,"Cupid wish invitations on",null,null);
            select(witness,"First wish target",null);select(cow,"Two hearts answered",()->births(3,EntityType.COW));
            arrow(witness,NetworkWishServer::refresh,()->check(charmed(witness),"refresh actual player charm"));
            arrow(sheep,null,()->check(charmed(sheep),"sheep charm before reversed pair"));
            select(sheep,"First wish target",null);select(witness,"Two hearts answered",()->births(4,EntityType.SHEEP));
            action("witness","command",null,"Cupid Bow:",null,null);
            arrow(witness,()->resetWitness(GameType.CREATIVE),()->check(!charmed(witness),"creative player charmed"));
            arrow(witness,()->resetWitness(GameType.SPECTATOR),()->check(!charmed(witness),"spectator player charmed"));
            arrow(witness,()->{resetWitness(GameType.SURVIVAL);server.setPvpAllowed(false);health=witness.getHealth();},()->check(!charmed(witness)&&witness.getHealth()==health,"PvP-disabled player affected"));
            arrow(witness,()->{server.setPvpAllowed(true);refresh();},()->check(charmed(witness),"PvP-enabled player charm"));
            arrow(cow,null,()->check(charmed(cow),"cow for pending logout wish"));
            select(witness,"First wish target",null);
            action("witness","logout",null,"",null,null);
            ready=true;System.out.println("NETWORK WISH READY two real clients; no direct select/charm calls");
        }
        check(System.nanoTime()-started<1_100_000_000_000L,"network session timeout step="+index);
        if(index==actions.size()){
            if(++afterLogout<30)return;
            check(server.getPlayerList().getPlayer(witness.getUUID())==null,"witness did not disconnect");
            check(!witness.getPersistentData().hasUUID(OWNER),"logout retained player charm");births(4,null);
            var f=Class.forName("dev.uwu.dimension.CupidWishes").getDeclaredField("wishes");f.setAccessible(true);
            check(!((Map<?,?>)f.get(null)).containsKey(archer.getUUID()),"logout retained pending wish");
            System.out.println("NETWORK WISH PLAYER LOGOUT PASS pending wish cleared without a baby");
            logic=true;CupidWishProof.run(server.overworld());
            Files.writeString(SYNC.resolve("complete"),"passed");finished=true;
            System.out.println("NETWORK WISH FULL PASS physical input, cows, mixed species, opt-in player/cow and sheep/player, restrictions, timing and logout");return;
        }
        var current=actions.get(index);
        if(!issued){
            run(current.before);var p=new Properties();p.setProperty("id",Integer.toString(index));p.setProperty("role",current.role);p.setProperty("action",current.kind);
            p.setProperty("target",Integer.toString(current.target==null?-1:current.target.getId()));p.setProperty("expect",current.expect);
            if(current.kind.equals("command"))p.setProperty("command",current.role.equals("witness")&&index<20?"sakura cupid witness on":"sakura cupid help");
            Path temp=SYNC.resolve("task.tmp");Files.createDirectories(SYNC);try(var writer=Files.newBufferedWriter(temp)){p.store(writer,"Disposable native-input test");}
            Files.move(temp,SYNC.resolve("task.properties"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);issued=true;
            System.out.println("NETWORK WISH ACTION "+index+" "+current.kind);return;
        }
        if(Files.exists(SYNC.resolve("done-"+index))){run(current.verify);System.out.println("NETWORK WISH STEP PASS "+index+" "+current.kind);index++;issued=false;}
    }
    @SubscribeEvent public static void birth(EntityJoinLevelEvent e){
        if(!ACTIVE||!ready||logic||!(e.getEntity() instanceof AgeableMob baby)||!baby.isBaby()||baby.getY()<112||baby.getY()>115)return;
        check(cue>=0&&e.getLevel().getGameTime()-cue>=20,"baby appeared before 20-tick heart cue");
        babies.add(baby.getType());System.out.println("NETWORK WISH BIRTH type="+baby.getType()+" cueTicks="+(e.getLevel().getGameTime()-cue));cue=-1;
    }
}
