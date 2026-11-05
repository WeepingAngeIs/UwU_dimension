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
    public Expedition() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private void tick(ServerTickEvent.Post event) {
        var server=event.getServer();
        if(!server.isDedicatedServer() || stage<0 || server.getTickCount()<15)return;
        try {
            ServerLevel level=server.getLevel(ReefLife.REACH);
            if(level==null)throw new AssertionError("Missing default Blossom Reach");
            if(stage==0) {
                if(!UpgradeProof.ready(server))return;
                var site=GroveSites.first(level);
                RouteProof.run(level);
                ChestProof.run(level,site.pos(0,53,103));
                ArsenalProof.run(level);
                long entries=PlayerSpawnerFactory.INSTANCE.getSpawnPool().getDetails().stream().filter(d->d.getId().startsWith("heartwater-")).count();
                if(entries!=27)throw new AssertionError("Wrong native Cobblemon definition count "+entries);
                stage=1;
            } else if(stage==1) {
                if(VillageProof.tick(level,GroveSites.village(level,false)))stage=2;
            } else if(stage==2) {
                if(!BossProof.tick(level,GroveSites.first(level).pos(-7,-17,146)))return;
                System.out.println("HEARTWATER EXPEDITION PASSED seed="+level.getSeed());
                stage=-1;server.halt(false);
            }
        } catch(Throwable error) {
            System.err.println("HEARTWATER EXPEDITION FAILED in stage "+stage);
            error.printStackTrace();stage=-1;server.halt(false);
        }
    }
}
