package dev.uwu.test;

import io.netty.channel.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Connected real players, physical clicks, packet observation and unchanged gameplay. */
final class NetworkWeaponServer {
    private static final Path SYNC=Path.of(System.getenv("WISH_SYNC_DIR"));
    // The counter's legacy item ID says longsword, but its actual roster shape is spear.
    private static final String[] ITEMS={"blossom_greatsword","blossom_greatsword","counter_iron_longsword","counter_iron_longsword"};
    private static final Queue<String> IN=new ConcurrentLinkedQueue<>(),OUT=new ConcurrentLinkedQueue<>();
    private static ServerPlayer archer,witness;private static Cow cow;
    private static boolean ready,issued,go,finished;private static int scene;private static long started;
    private static float health;private static Set<String> allowed=Set.of();
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError("WEAPON NETWORK "+why);}
    private static String payload(Object packet){
        try{
            Object p=packet instanceof net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket incoming?incoming.payload():
                    packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket outgoing?outgoing.payload():null;
            if(p==null)return null;
            if(!p.getClass().getName().equals("net.bettercombat.network.Packets$AttackAnimation"))return null;
            return p.getClass().getMethod("playerId").invoke(p)+"|"+p.getClass().getMethod("animationName").invoke(p)+"|"+p.getClass().getMethod("animatedHand").invoke(p);
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private static void observe(ServerPlayer player,Queue<String> incoming,Queue<String> outgoing){
        var channel=player.connection.getConnection().channel();
        channel.eventLoop().execute(()->channel.pipeline().addBefore("packet_handler","uwu_weapon_observer",new ChannelDuplexHandler(){
            @Override public void channelRead(ChannelHandlerContext c,Object msg)throws Exception{String p=payload(msg);if(p!=null&&incoming!=null)incoming.add(p);super.channelRead(c,msg);}
            @Override public void write(ChannelHandlerContext c,Object msg,ChannelPromise promise)throws Exception{String p=payload(msg);if(p!=null&&outgoing!=null)outgoing.add(p);super.write(c,msg,promise);}
        }));
    }
    private static Set<String> animations(ItemStack stack)throws Exception{
        var registry=Class.forName("net.bettercombat.logic.WeaponRegistry");var attrs=registry.getMethod("getAttributes",ItemStack.class).invoke(null,stack);
        check(attrs!=null,"missing current Better Combat profile "+stack);
        Object a=attrs.getClass().getMethod("attacks").invoke(attrs);check(a!=null&&java.lang.reflect.Array.getLength(a)>0,"empty attack profile");
        var names=new TreeSet<String>();for(int i=0;i<java.lang.reflect.Array.getLength(a);i++){
            Object attack=java.lang.reflect.Array.get(a,i);names.add((String)attack.getClass().getMethod("animation").invoke(attack));
        }return names;
    }
    private static void task(String state)throws Exception{
        var p=new Properties();p.setProperty("id",Integer.toString(scene));p.setProperty("state",state);p.setProperty("item",ITEMS[scene]);
        p.setProperty("camera",scene%2==0?"first":"third");p.setProperty("target",Integer.toString(cow.getId()));
        p.setProperty("shooter",Integer.toString(archer.getId()));p.setProperty("allowed",String.join(",",allowed));
        Path temp=SYNC.resolve("weapon-task.tmp");try(var out=Files.newBufferedWriter(temp)){p.store(out,"Disposable network fixture");}
        Files.move(temp,SYNC.resolve("weapon-task.properties"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    static void tick(MinecraftServer server)throws Exception{
        if(finished)return;
        if(!ready){
            archer=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals("WishArcher")).findFirst().orElse(null);
            witness=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals("WishWitness")).findFirst().orElse(null);
            if(archer==null||witness==null)return;
            var level=server.overworld();server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gamerule doMobSpawning false");
            for(int x=-8;x<=8;x++)for(int z=-5;z<=10;z++)level.setBlockAndUpdate(new BlockPos(x,112,z),Blocks.STONE.defaultBlockState());
            archer.teleportTo(level,.5,113,.5,0,0);witness.teleportTo(level,4.5,113,1.5,104,0);
            cow=EntityType.COW.create(level);cow.setNoAi(true);cow.setPersistenceRequired();cow.moveTo(.5,113,2.7,0,0);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);cow.setHealth(1000);check(level.addFreshEntity(cow),"target insertion");
            observe(archer,IN,null);observe(witness,null,OUT);started=System.nanoTime();ready=true;
            System.out.println("WEAPON NETWORK READY real shooter and tracking witness");return;
        }
        check(System.nanoTime()-started<600_000_000_000L,"session timed out scene="+scene);
        if(scene==ITEMS.length){Files.writeString(SYNC.resolve("weapon-complete"),"passed");finished=true;System.out.println("WEAPON NETWORK PASS physical attacks, packet relay, damage, first/third/witness frames");return;}
        if(!issued){
            var item=BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("uwudimension",ITEMS[scene])).orElseThrow();
            var stack=new ItemStack(item);archer.setItemInHand(InteractionHand.MAIN_HAND,stack);archer.containerMenu.broadcastChanges();
            cow.setHealth(1000);cow.invulnerableTime=0;cow.moveTo(.5,113,2.7,0,0);health=cow.getHealth();
            archer.teleportTo(server.overworld(),.5,113,.5,0,0);archer.setDeltaMovement(Vec3.ZERO);
            IN.clear();OUT.clear();allowed=animations(stack);task("prepare");issued=true;go=false;return;
        }
        if(!go){
            if(!Files.exists(SYNC.resolve("weapon-ready-"+scene+"-archer"))||!Files.exists(SYNC.resolve("weapon-ready-"+scene+"-witness")))return;
            task("go");go=true;return;
        }
        if(!Files.exists(SYNC.resolve("weapon-done-"+scene+"-archer"))||!Files.exists(SYNC.resolve("weapon-done-"+scene+"-witness")))return;
        String expected=Files.readString(SYNC.resolve("weapon-packet-"+scene+"-witness")).strip();
        String[] p=expected.split("\\|",-1);check(p.length==3&&Integer.parseInt(p[0])==archer.getId()&&allowed.contains(p[1]),"witness animation was not a server profile attack: "+expected+" allowed="+allowed);
        check(OUT.contains(expected),"server did not relay matching animation to witness "+expected+" got="+OUT);
        check(IN.stream().anyMatch(s->{String[] v=s.split("\\|",-1);return v.length==3&&v[1].equals(p[1])&&v[2].equals(p[2]);}),"server did not receive matching client animation "+expected+" got="+IN);
        check(cow.getHealth()<health,"no actual target damage for "+ITEMS[scene]);
        System.out.println("WEAPON NETWORK SCENE PASS "+scene+" item="+ITEMS[scene]+" packet="+expected+" damage="+(health-cow.getHealth()));
        scene++;issued=false;
    }
}
