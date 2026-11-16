package dev.uwu.test;

import dev.uwu.dimension.GlitchBow;
import dev.uwu.dimension.RelicFlight;
import dev.uwu.dimension.UwUDimension;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;

/** Two genuine remote clients connect to a disposable dedicated server. */
final class NetworkGlitchServer {
    static final String SESSION="network-glitch";
    static final String SHOOTER="GlitchShooter",OBSERVER="GlitchObserver",FORM="counter_runic_twinblade";
    static final int FLOOR=112;
    private static boolean ready,launched,advanced,cupidReady,charmed,logoutPassed;
    private static ServerPlayer shooter;
    private static RelicFlight projectile;

    private static Path marker(String name){return Path.of(System.getenv().getOrDefault("GLITCH_SYNC_DIR",".")).resolve(name+".seen");}
    private static void signal(String name){try{Files.createDirectories(marker(name).getParent());Files.writeString(marker(name),name);}catch(IOException e){throw new UncheckedIOException(e);}}
    static void tick(MinecraftServer server){
        if(!ready){
            ServerPlayer first=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals(SHOOTER)).findFirst().orElse(null);
            ServerPlayer second=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals(OBSERVER)).findFirst().orElse(null);
            if(first==null||second==null)return;
            var level=server.overworld();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gamerule doMobSpawning false");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gamerule doDaylightCycle false");
            level.setDayTime(6000);
            for(int x=-8;x<=8;x++)for(int z=-6;z<=14;z++){
                level.setBlockAndUpdate(new BlockPos(x,FLOOR,z),Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
                if(z==10)for(int y=1;y<=7;y++)level.setBlockAndUpdate(new BlockPos(x,FLOOR+y,z),Blocks.GRAY_CONCRETE.defaultBlockState());
            }
            first.teleportTo(level,.5,FLOOR+1,.5,0,0);
            second.teleportTo(level,5.5,FLOOR+1,3.5,90,0);
            var stack=new ItemStack(UwUDimension.GLITCH.get());
            int index=GlitchBow.FORMS.indexOf(FORM);
            if(index<0)throw new AssertionError("Missing reported Twin Chakram form");
            CustomData.update(DataComponents.CUSTOM_DATA,stack,n->n.putInt("uwu_form",index));
            first.setItemInHand(InteractionHand.MAIN_HAND,stack);
            first.containerMenu.broadcastChanges();
            server.tickRateManager().setTickRate(8F);
            shooter=first;ready=true;
            System.out.println("NETWORK GLITCH SERVER READY two real remote players, Runic Twin Chakram");
            return;
        }
        if(!launched){
            List<RelicFlight> flights=shooter.serverLevel().getEntitiesOfClass(RelicFlight.class,shooter.getBoundingBox().inflate(20),f->f.getOwner()==shooter&&!f.wave());
            if(!flights.isEmpty()){
                projectile=flights.getFirst();
                if(flights.size()!=1||!projectile.getItem().is(UwUDimension.GLITCH.get())||!GlitchBow.form(projectile.getItem()).equals(FORM)||!shooter.getMainHandItem().isEmpty())
                    throw new AssertionError("Actual client throw did not preserve the selected physical Glitch stack");
                launched=true;System.out.println("NETWORK GLITCH SERVER LAUNCH PASS selected physical form left the hand");
            }
        }else if(!advanced&&projectile.isRemoved()&&shooter.getMainHandItem().is(UwUDimension.GLITCH.get())){
            ItemStack stack=shooter.getMainHandItem();
            if(!GlitchBow.form(stack).equals(FORM)){
                if(shooter.getInventory().countItem(UwUDimension.GLITCH.get())!=1)throw new AssertionError("Glitch stack duplicated during remote throw");
                advanced=true;System.out.println("NETWORK GLITCH SERVER ADVANCE PASS returned stack now "+GlitchBow.form(stack));
            }
        }
        if(advanced&&!cupidReady&&Files.exists(marker("changed"))){
            var victim=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals(OBSERVER)).findFirst().orElse(null);
            if(victim==null)throw new AssertionError("Observer disconnected before Cupid logout test");
            var level=server.overworld();
            shooter.teleportTo(level,.5,FLOOR+1,.5,0,0);
            victim.teleportTo(level,.5,FLOOR+1,4.5,180,0);
            ItemStack stack=shooter.getMainHandItem();
            int index=GlitchBow.FORMS.indexOf("cupid_bow");
            CustomData.update(DataComponents.CUSTOM_DATA,stack,n->n.putInt("uwu_form",index));
            shooter.containerMenu.broadcastChanges();cupidReady=true;signal("cupid-ready");
            System.out.println("NETWORK GLITCH CUPID READY two real clients");
        }
        if(cupidReady&&!charmed){
            var victim=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals(OBSERVER)).findFirst().orElse(null);
            if(victim!=null&&victim.getTags().contains("uwudimension.charm_owner")){
                if(!GlitchBow.form(shooter.getMainHandItem()).equals("cupid_bow"))throw new AssertionError("Cupid form changed while real player remained charmed");
                charmed=true;signal("charm");System.out.println("NETWORK GLITCH CUPID CHARM PASS real remote player");
            }
        }
        if(charmed&&!logoutPassed&&server.getPlayerList().getPlayers().stream().noneMatch(p->p.getGameProfile().getName().equals(OBSERVER))){
            if(!shooter.getMainHandItem().is(UwUDimension.GLITCH.get()))throw new AssertionError("Shooter lost Glitch after observer logout");
            if(!GlitchBow.form(shooter.getMainHandItem()).equals("cupid_bow")){
                logoutPassed=true;signal("logout-pass");System.out.println("NETWORK GLITCH CUPID LOGOUT PASS real disconnect released charm and form advanced");
            }
        }
    }
}
