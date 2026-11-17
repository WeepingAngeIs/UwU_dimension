package dev.uwu.test;

import java.nio.file.*;
import java.util.*;
import dev.uwu.dimension.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Two TCP clients photograph all registered shapes through the actual held and dropped pipelines. */
final class NetworkPolishServer {
    private static final Path SYNC=Path.of(System.getenv().getOrDefault("WISH_SYNC_DIR","."));
    private static final List<String> ITEMS=new ArrayList<>();
    private static final String[] VIEWS={"first-right","first-left","third-right","third-left","ground"};
    private static ServerPlayer archer,witness;
    private static ItemEntity drop;
    private static boolean ready,issued,go,finished;
    private static int scene;
    private static long started;
    private static int shots, shotsBefore;
    private static int baseCount(){return ITEMS.size()*VIEWS.length;}
    private static ResourceLocation key(String value){return value.contains(":")?ResourceLocation.parse(value):ResourceLocation.fromNamespaceAndPath("uwudimension",value);}
    private static String item(){return scene<baseCount()?ITEMS.get(scene/VIEWS.length):(scene-baseCount())/4==0?"cupid_bow":"ripple_bow";}
    private static String view(){return scene<baseCount()?VIEWS[scene%VIEWS.length]:VIEWS[(scene-baseCount())%4];}
    private static void require(boolean test,String why){if(!test)throw new AssertionError("POLISH NETWORK "+why);}
    private static void task(String state)throws Exception{
        var p=new Properties();
        p.setProperty("id",Integer.toString(scene));p.setProperty("state",state);
        p.setProperty("item",item());p.setProperty("view",view());
        p.setProperty("action",scene<baseCount()?"idle":"draw");
        p.setProperty("shooter",Integer.toString(archer.getId()));
        Path temp=SYNC.resolve("polish-task.tmp");
        try(var out=Files.newBufferedWriter(temp)){p.store(out,"Actual registered weapon visual matrix");}
        Files.move(temp,SYNC.resolve("polish-task.properties"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    static void tick(MinecraftServer server)throws Exception{
        if(finished)return;
        if(!ready){
            archer=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals("WishArcher")).findFirst().orElse(null);
            witness=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals("WishWitness")).findFirst().orElse(null);
            if(archer==null||witness==null)return;
            for(var relic:Arsenal.Relic.values())ITEMS.add(relic.name().toLowerCase(Locale.ROOT));
            for(var e:CounterArsenal.ROSTER)ITEMS.add(e.id());
            ITEMS.addAll(List.of("glitch_bow","heart_launcher","bell_hammer","returnbloom_dirk","dawnpetal_axe"));
            require(ITEMS.size()==153,"expected 153 weapons "+ITEMS.size());
            ITEMS.addAll(List.of("minecraft:bow","simplyswords:soulrender"));
            var level=server.overworld();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gamerule doMobSpawning false");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gamerule doDaylightCycle false");
            level.setDayTime(6000);
            for(int x=-8;x<=8;x++)for(int z=-5;z<=10;z++)level.setBlockAndUpdate(new BlockPos(x,112,z),Blocks.STONE.defaultBlockState());
            archer.teleportTo(level,.5,113,.5,0,0);
            witness.teleportTo(level,3.5,113,3.5,135,0);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event)->{
                if(event.getEntity() instanceof HeartArrow arrow && arrow.getOwner()==archer)shots++;
            });
            ready=true;started=System.nanoTime();
            System.out.println("POLISH NETWORK READY items="+ITEMS.size());return;
        }
        require(System.nanoTime()-started<1_650_000_000_000L,"timeout "+scene);
        if(scene==baseCount()+8){
            Files.writeString(SYNC.resolve("polish-complete"),"passed "+scene);
            finished=true;System.out.println("POLISH NETWORK PASS "+scene+" mounted scenes, 153 registered weapons, real remote client");return;
        }
        if(!issued){
            if(drop!=null){drop.discard();drop=null;}
            var key=key(item());
            var stack=new ItemStack(BuiltInRegistries.ITEM.getOptional(key).orElseThrow());
            archer.stopUsingItem();archer.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);archer.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            String view=view();
            if(view.equals("ground")){
                drop=new ItemEntity(server.overworld(),.5,113,2.2,stack);
                drop.setNoGravity(true);drop.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                require(server.overworld().addFreshEntity(drop),"drop spawn");
            }else archer.setItemInHand(view.endsWith("left")?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,stack);
            archer.containerMenu.broadcastChanges();
            task("prepare");issued=true;go=false;shotsBefore=shots;return;
        }
        if(!go){
            if(!Files.exists(SYNC.resolve("polish-ready-"+scene+"-archer"))||!Files.exists(SYNC.resolve("polish-ready-"+scene+"-witness")))return;
            task("go");go=true;return;
        }
        if(!Files.exists(SYNC.resolve("polish-done-"+scene+"-archer"))||!Files.exists(SYNC.resolve("polish-done-"+scene+"-witness")))return;
        System.out.println("POLISH NETWORK FRAME "+scene+" "+item()+" "+view());
        if(scene>=baseCount())require(shots==shotsBefore+1,"bow did not emit exactly one real shot "+item()+" "+view()+" shots="+(shots-shotsBefore));
        scene++;issued=false;
    }
}

