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
    private long arenaReadyAt;
    private final java.util.List<net.minecraft.world.level.ChunkPos> arenaTickets=new java.util.ArrayList<>();
    private void recordFailure(String check, Throwable error) {
        failed=true;
        System.err.println("EXPEDITION CHECK FAILED "+check);
        error.printStackTrace();
    }
    private interface Check { void run() throws Exception; }
    private void check(String name, Check test) {
        try { test.run(); } catch (Throwable error) { recordFailure(name,error); }
    }
    public Expedition(net.neoforged.bus.api.IEventBus bus) {
        if(System.getenv("UWU_OVERLAP")!=null)OverlapProof.register(bus);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ServerTickEvent.Post event) {
        var server=event.getServer();
        if (server.isDedicatedServer() && "1".equals(System.getenv("UWU_HEART_SHIELDS"))) {
            HeartShieldProof.tick(server);
            return;
        }
        if(server.isDedicatedServer() && "network-weapon-motion".equals(System.getenv("UWU_VISIT"))){
            try{NetworkMotionServer.tick(server);}catch(Exception error){throw new IllegalStateException("Network motion proof",error);}
            return;
        }
        if(server.isDedicatedServer() && "network-weapon-polish".equals(System.getenv("UWU_VISIT"))){
            try{NetworkPolishServer.tick(server);}catch(Exception error){throw new IllegalStateException("Network weapon polish proof",error);}
            return;
        }
        if(server.isDedicatedServer() && "network-weapon-animation".equals(System.getenv("UWU_VISIT"))){
            try{NetworkWeaponServer.tick(server);}catch(Exception e){throw new IllegalStateException("Network weapon animation proof",e);}
            return;
        }
        if(server.isDedicatedServer() && "network-wishes".equals(System.getenv("UWU_VISIT"))){
            try{NetworkWishServer.tick(server);}catch(Exception e){throw new IllegalStateException("Network wish proof",e);}
            return;
        }
        if (server.isDedicatedServer() && NetworkCupidServer.SESSION.equals(System.getenv("UWU_VISIT"))) {
            NetworkCupidServer.tick(server);
            return;
        }
        if (server.isDedicatedServer() && NetworkGlitchServer.SESSION.equals(System.getenv("UWU_VISIT"))) {
            NetworkGlitchServer.tick(server);
            return;
        }
        if(!server.isDedicatedServer() || stage<0 || server.getTickCount()<15)return;
        try {
            ServerLevel level=server.getLevel(ReefLife.REACH);
            if(level==null)throw new AssertionError("Missing default Blossom Reach");
            if(System.getenv("UWU_POLISH_SERVER")!=null){
                boolean restart="restart".equals(System.getenv("UWU_POLISH_SERVER"));
                if(stage==0){
                    if(restart)BlossomRevivalProof.restart(level);else BlossomRevivalProof.run(level);
                    stage=1;
                }
                if(stage==1){
                    if(!BlossomArtsProof.tick(level,restart))return;
                    stage=2;
                }
                if(!restart&&!BlossomRevivalProof.verifyLighting(level))return;
                System.out.println("WEAPON POLISH SERVER PASS "+(restart?"restart":"first"));
                stage=-1;server.halt(false);return;
            }
            if(System.getenv("UWU_WEAPON_OBSERVE")!=null){WeaponProfileObservation.run();stage=-1;server.halt(false);return;}
            if(System.getenv("UWU_OVERLAP")!=null){OverlapProof.run(level,"restart".equals(System.getenv("UWU_OVERLAP")));stage=-1;server.halt(false);return;}
            if(System.getenv("UWU_ACTIVITY_BASELINE")!=null){ActivityLegacyFixture.run(level);stage=-1;server.halt(false);return;}
            if(System.getenv("UWU_ACTIVITY_RESTART")!=null){ActivityProof.restart(level);stage=-1;server.halt(false);return;}
            if(System.getenv("UWU_ACTIVITY_SERVER")!=null){
                if(stage==0){for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setChunkForced(x,z,true);stage=1;arenaReadyAt=server.getTickCount()+80;return;}
                if(server.getTickCount()<arenaReadyAt)return;
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x,z)))return;
                if(stage==1){ActivityProof.first(level);stage=2;}
                if(!ToolAreaProof.tick(level))return;
                stage=-1;server.halt(false);return;
            }
            if(System.getenv("UWU_GUARDIAN_CREEPER")!=null){
                if(GuardianCreeperProof.tick(level)){stage=-1;server.halt(false);}
                return;
            }
            if(System.getenv("UWU_GLITCH_LIFECYCLE")!=null){
                if(GlitchLifecycleProof.tick(level)){stage=-1;server.halt(false);}
                return;
            }
            if(System.getenv("UWU_FOREST_RESTART")!=null){ForestRestartProof.run(level);stage=-1;server.halt(false);return;}
            if(System.getenv("UWU_FOREST_BASELINE")!=null){ForestBaselineFixture.run(level);stage=-1;server.halt(false);return;}
            if(System.getenv("UWU_FOREST_UPGRADE")!=null){ForestUpgradeProof.run(level);stage=-1;server.halt(false);return;}
            if(System.getenv("UWU_FOREST")!=null){if(!GiantSakuraProof.tick(level))return;ForestProof.run(level);stage=-1;server.halt(false);return;}
            if(System.getenv("UWU_GIANT_SAKURA")!=null){
                if(GiantSakuraProof.tick(level)){stage=-1;server.halt(false);}
                return;
            }
            if(System.getenv("UWU_ROSTER_POLICY")!=null){
                var actor=CounterProof.player(level,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("c719e828-f310-44fa-95d5-41fef4a9da11"),"UwUArcher"));
                level.addNewPlayer(actor);
                try { RosterProof.policy(actor); } finally { actor.discard(); }
                stage=-1;server.halt(false);return;
            }
            if (System.getenv("UWU_RENEWAL") != null) {
                boolean restart = java.nio.file.Files.exists(java.nio.file.Path.of("first-site.txt"));
                if (stage == 99) {
                    if (!ToolAreaProof.tick(level)) return;
                    stage = 16;
                    return;
                }
                if (stage == 0) {
                    if (restart && !BossProof.verifyRestart(level)) return;
                    var disk=GuardianFamilyDisk.CHUNK;
                    if(level.setChunkForced(disk.x,disk.z,true))arenaTickets.add(disk);
                    for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) if(level.setChunkForced(x,z,true)) arenaTickets.add(new net.minecraft.world.level.ChunkPos(x,z));
                    arenaReadyAt = level.getGameTime() + 40;
                    stage = 1;
                    return;
                }
                if (stage == 1) {
                    for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x,z))) {
                        if (level.getGameTime() - arenaReadyAt > 400) throw new AssertionError("Arena entity data unavailable");
                        return;
                    }
                    if(!level.areEntitiesLoaded(GuardianFamilyDisk.CHUNK.toLong())){
                        if(level.getGameTime()-arenaReadyAt>400)throw new AssertionError("Guardian disk-test region unavailable");
                        return;
                    }
                    if (level.getGameTime() < arenaReadyAt || !AmbienceProof.tick(level)) return;
                    if (!TreehouseProof.ready(level,restart)) return;
                    try {
                        RenewalProof.run(level);
                    } catch (Throwable error) {
                        recordFailure("renewal checks", error);
                    } finally {
                        for (var chunk : arenaTickets) level.setChunkForced(chunk.x,chunk.z,false);
                        arenaTickets.clear();
                    }
                    if (!restart) { stage = failed ? 2 : 99; return; }
                } else if(stage==16){
                    if(!GuardianFamilyDisk.roundTrip(level))return;
                    stage=2;return;
                } else if (stage == 2) {
                    try {
                        if ("152045".equals(System.getenv("UWU_FIELD_SEED"))
                                && !VillageProof.tick(level,GroveSites.village(level,false))) return;
                    } catch (Throwable error) {
                        recordFailure("village life and guardian flight",error);
                    }
                    stage = 3;
                    return;
                } else if (stage == 3) {
                    var site = GroveSites.first(level);
                    if (!site.usable()) throw new AssertionError("Both hosted seeds should have a generated Mirror Court");
                    if (!BossProof.tick(level,site.pos(-7,-17,146))) return;
                }
                if (failed) throw new AssertionError("Renewal checks failed; see each recorded failure above");
                System.out.println("SAKURA FULL SERVER VISIT PASS");
                stage = -1;
                server.halt(false);
                return;
            }
            if(System.getenv("UWU_ADVANCEMENT")!=null){if(AdvancementProof.tick(level)){stage=-1;server.halt(false);}return;}
            if(System.getenv("UWU_CONTROLS")!=null){if(ControlsProof.tick(level)){stage=-1;server.halt(false);}return;}
            if(System.getenv("UWU_FLIGHT")!=null){if(FlightProof.tick(level)){stage=-1;server.halt(false);}return;}
            if(System.getenv("UWU_RESIDENTS")!=null){
                if(ResidentProof.tick(level)){stage=-1;server.halt(false);}return;
            }
            if(System.getenv("UWU_ARSENAL")!=null){
                if(stage==0){
                    for(int x=-1;x<=1;x++)for(int z=-1;z<=2;z++)level.setChunkForced(x,z,true);
                    arenaReadyAt=level.getGameTime()+40;stage=1;return;
                }
                if(level.getGameTime()<arenaReadyAt)return;
                if(stage==1){if(!CounterProof.ready(level))return;CounterProof.run(level);stage=2;}
                if(!RevivalProof.tick(level))return;
                var court=GroveSites.first(level).pos(-7,-17,146);
                if(BossProof.tick(level,court)){
                    System.out.println("UWU COUNTER ARSENAL PASSED");stage=-1;server.halt(false);
                }
                return;
            }
            if(Boolean.getBoolean("uwu.bossReload")) {
                if(BossProof.verifyRestart(level)) { stage=-1; server.halt(false); }
                return;
            }
            if(stage==0) {
                try { if(!UpgradeProof.ready(server))return; }
                catch(Throwable error) { recordFailure("upgrade",error); }
                var site=GroveSites.first(level);
                check("terrain and route",()->RouteProof.run(level));
                check("village and dungeon furnishings",()->FurnishingProof.verify(level));
                check("loot and factions",()->ChestProof.run(level,site.pos(0,53,103)));
                var arena=new net.minecraft.world.level.ChunkPos(site.pos(42,1,115));
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
                    if(level.setChunkForced(arena.x+dx,arena.z+dz,true))
                        arenaTickets.add(new net.minecraft.world.level.ChunkPos(arena.x+dx,arena.z+dz));
                arenaReadyAt=level.getGameTime()+25;
                long entries=PlayerSpawnerFactory.INSTANCE.getSpawnPool().getDetails().stream().filter(d->d.getId().startsWith("heartwater-")).count();
                if(entries!=27)throw new AssertionError("Wrong native Cobblemon definition count "+entries);
                stage=3;
            } else if(stage==3) {
                if(level.getGameTime()<arenaReadyAt)return;
                check("heartsill block family",()->HeartFloorProof.verify(level,GroveSites.first(level).pos(42,level.getMaxBuildHeight()-12,115)));
                check("relics",()->ArsenalProof.run(level));
                arenaTickets.forEach(c->level.setChunkForced(c.x,c.z,false));
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
