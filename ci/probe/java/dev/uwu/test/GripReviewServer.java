package dev.uwu.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

final class GripReviewServer {
    static final String VARIANT = System.getenv("UWU_GRIP_VARIANT");
    private static final Path SYNC = Path.of(System.getenv().getOrDefault("WISH_SYNC_DIR", "."));
    private record Hold(String item, String hand) { }
    private static final Hold[] HOLDS = {
        new Hold("vesper_scythe", "right"), new Hold("vesper_scythe", "left"),
        new Hold("frost_whip", "right"), new Hold("frost_whip", "left"), new Hold("frost_whip", "offhand"),
        new Hold("simplyswords:soulrender", "right")
    };
    // A player facing +Z has anatomical left on +X. Positions are eye offsets from their feet.
    private record Camera(String name, double x, double y, double z) { }
    private static final Camera[] VIEWS = {
        new Camera("front", 0, 1.22, 3.4), new Camera("back", 0, 1.22, -3.4),
        new Camera("left-side", 3.4, 1.22, 0), new Camera("right-side", -3.4, 1.22, 0),
        new Camera("above", 0, 4.65, 1.2), new Camera("low-three-quarter", 2.65, .25, 2.15),
        new Camera("original-diagonal", 3, 1.62, 3)
    };
    private static ServerPlayer archer, witness;
    private static int scene, phase;
    private static long began;
    private static boolean finished;

    private static void task(String stage) throws Exception {
        var p = new Properties();
        var view=VIEWS[scene%VIEWS.length];
        p.setProperty("id", Integer.toString(scene));
        p.setProperty("state", stage);
        p.setProperty("item", HOLDS[scene/VIEWS.length].item());
        p.setProperty("hand", HOLDS[scene/VIEWS.length].hand());
        p.setProperty("view", view.name());
        p.setProperty("shooter", Integer.toString(archer.getId()));
        p.setProperty("eyeX", Double.toString(.5+view.x()));
        p.setProperty("eyeY", Double.toString(113+view.y()));
        p.setProperty("eyeZ", Double.toString(.5+view.z()));
        Path partial=SYNC.resolve("grip.partial");
        try(var out=Files.newBufferedWriter(partial)) { p.store(out,"Fixed actor and separate anatomical camera"); }
        Files.move(partial,SYNC.resolve("grip.properties"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }

    static void tick(MinecraftServer server) throws Exception {
        if(finished)return;
        var level=server.overworld();
        if(archer==null || witness==null){
            archer=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals("WishArcher")).findFirst().orElse(null);
            witness=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals("WishWitness")).findFirst().orElse(null);
            if(archer==null || witness==null)return;
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gamerule doMobSpawning false");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gamerule doDaylightCycle false");
            level.setDayTime(6000);
            level.setWeatherParameters(20000,0,false,false);
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++) level.setBlockAndUpdate(new BlockPos(x,112,z),Blocks.SMOOTH_STONE.defaultBlockState());
            archer.setGameMode(GameType.SURVIVAL);
            witness.setGameMode(GameType.CREATIVE);
            witness.getAbilities().flying=true;
            witness.onUpdateAbilities();
            witness.setInvisible(true);
            witness.teleportTo(level, 18.5, 113, 14.5, 0, 0);
            // Keep the low camera above the floor without lowering the actor or moving its pivot.
            archer.teleportTo(level,.5,113,.5,0,0);
        }
        if(scene>=HOLDS.length*VIEWS.length){
            Files.writeString(SYNC.resolve("polish-complete"),"review complete");
            System.out.println("POLISH NETWORK PASS grip review "+scene+" directed poses");
            finished=true;return;
        }
        if(phase==0){
            String key=HOLDS[scene/VIEWS.length].item();
            var item=BuiltInRegistries.ITEM.getOptional(key.contains(":")?ResourceLocation.parse(key):ResourceLocation.fromNamespaceAndPath("uwudimension",key)).orElseThrow();
            var camera=VIEWS[scene%VIEWS.length];
            boolean offhand = HOLDS[scene/VIEWS.length].hand().equals("offhand");
            archer.setMainArm(HOLDS[scene/VIEWS.length].hand().equals("left")?HumanoidArm.LEFT:HumanoidArm.RIGHT);
            archer.setItemInHand(InteractionHand.MAIN_HAND,offhand ? ItemStack.EMPTY : new ItemStack(item));
            archer.setItemInHand(InteractionHand.OFF_HAND,offhand ? new ItemStack(item) : ItemStack.EMPTY);
            archer.getCooldowns().removeCooldown(item);
            archer.containerMenu.broadcastChanges();
            archer.teleportTo(level,.5,113,.5,0,0);
            task("prepare");phase=1;began=System.nanoTime();return;
        }
        if(System.nanoTime()-began>90_000_000_000L)throw new AssertionError("Grip scene timeout "+scene+" phase "+phase
                +" archer="+ Files.readString(SYNC.resolve("grip-archer-status.txt"))+" witness="+Files.readString(SYNC.resolve("grip-witness-status.txt")));
        String flag=phase==1?"ready":"done";
        if(!Files.exists(SYNC.resolve("grip-"+scene+"-"+flag+"-archer")) || !Files.exists(SYNC.resolve("grip-"+scene+"-"+flag+"-witness")))return;
        if(phase==1){task("go");phase=2;return;}
        System.out.println("GRIP SCENE "+scene+" "+HOLDS[scene/VIEWS.length].item()+" "+VIEWS[scene%VIEWS.length].name());
        scene++;phase=0;
    }
}

