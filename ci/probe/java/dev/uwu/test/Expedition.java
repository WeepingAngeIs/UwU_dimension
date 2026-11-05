package dev.uwu.test;

import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.ReefLife;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("heartwater_probe")
public class Expedition {
    private int stage;
    private boolean failed;
    private void recordFailure(String check, Throwable error) {
        failed=true;
        System.err.println("EXPEDITION CHECK FAILED "+check);
        error.printStackTrace();
    }
    private interface Check { void run() throws Exception; }
    private void check(String name, Check test) {
        try { test.run(); } catch (Throwable error) { recordFailure(name,error); }
    }
    public Expedition() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private void tick(ServerTickEvent.Post event) {
        var server=event.getServer();
        if(!server.isDedicatedServer() || stage<0 || server.getTickCount()<15)return;
        try {
            ServerLevel level=server.getLevel(ReefLife.REACH);
            if(level==null)throw new AssertionError("Missing default Blossom Reach");
            if(Boolean.getBoolean("uwu.bossReload")) {
                if(BossProof.verifyRestart(level)) { stage=-1; server.halt(false); }
                return;
            }
            if(stage==0) {
                try { if(!UpgradeProof.ready(server))return; }
                catch(Throwable error) { recordFailure("upgrade",error); }
                var site=GroveSites.first(level);
                check("terrain and route",()->RouteProof.run(level));
                check("loot and factions",()->ChestProof.run(level,site.pos(0,53,103)));
                check("relics",()->ArsenalProof.run(level));
                long entries=PlayerSpawnerFactory.INSTANCE.getSpawnPool().getDetails().stream().filter(d->d.getId().startsWith("heartwater-")).count();
                if(entries!=27)throw new AssertionError("Wrong native Cobblemon definition count "+entries);
                stage=1;
            } else if(stage==1) {
                try { if(VillageProof.tick(level,GroveSites.village(level,false)))stage=2; }
                catch(Throwable error) { recordFailure("village life and guardian",error); stage=2; }
            } else if(stage==2) {
                if(!BossProof.tick(level,GroveSites.first(level).pos(-7,-17,146)))return;
                System.out.println((failed?"HEARTWATER EXPEDITION FAILED":"HEARTWATER EXPEDITION PASSED")+" seed="+level.getSeed());
                stage=-1;server.halt(false);
            }
        } catch(Throwable error) {
            System.err.println("HEARTWATER EXPEDITION FAILED in stage "+stage);
            error.printStackTrace();stage=-1;server.halt(false);
        }
    }
}
