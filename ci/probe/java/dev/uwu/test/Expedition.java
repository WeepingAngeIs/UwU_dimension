package dev.uwu.test;

import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;

@Mod("heartwater_probe")
public class Expedition {
    private boolean completed;
    private boolean warming;
    private int waitTicks;
    public Expedition() { NeoForge.EVENT_BUS.addListener(this::tick); }

    private void tick(ServerTickEvent.Post event) {
        if (!event.getServer().isDedicatedServer() || completed || event.getServer().getTickCount() < 10) return;
        var server = event.getServer();
        try {
            var key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("uwudimension","heartwater"));
            ServerLevel level = server.getLevel(key);
            if (level == null) throw new IllegalStateException("Dimension failed to load");
            if (!warming) {
                warming = true;
                // Cobblemon requires entity-loaded chunks throughout a 48-block square.
                // A synchronous getChunk loads terrain but entity sections load on following ticks.
                for (int x=-5;x<=3;x++) for (int z=-4;z<=10;z++) level.setChunkForced(x,z,true);
                return;
            }
            if (++waitTicks<160) return;
            for (int x=-5;x<=3;x++) for(int z=-4;z<=10;z++) {
                if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x,z))) {
                    if (waitTicks<1800) return;
                    throw new AssertionError("Spawn-test entity sections did not load at "+x+","+z);
                }
            }
            completed = true;
            RouteProof.run(level);
            ChestProof.run(level, new BlockPos(0,53,103));
            ArsenalProof.run(level);
            testHabitats(level);
            System.out.println("HEARTWATER EXPEDITION PASSED");
        } catch (Throwable error) {
            completed = true;
            System.err.println("HEARTWATER EXPEDITION FAILED");
            error.printStackTrace();
        } finally {
            if (completed) server.halt(false);
        }
    }

    private static void testHabitats(ServerLevel level) {
        var visitor = new FakePlayer(level, new GameProfile(UUID.fromString("d24d9fcf-faff-4933-a82f-1b406c3f59b1"),"FieldBiologist"));
        visitor.moveTo(-10.5,81,-5.5,0,0);
        level.addNewPlayer(visitor);
        var spawner = PlayerSpawnerFactory.INSTANCE.create(visitor);
        // Each sample uses Cobblemon's actual player spawner, selectors, terrain and cap checks.
        // Choose areas explicitly to cover strata without a networked client wandering randomly.
        long entries=spawner.getSpawnPool().getDetails().stream().filter(d->d.getId().startsWith("heartwater-")).count();
        System.out.println("HEARTWATER NATIVE POOL contains "+entries+" dimension-specific definitions");
        if (entries!=19) throw new AssertionError("Spawn pool missing native entries");
        int found = 0;
        int[][] regions = {{-32,77,-16,12,10,12},{-12,47,63,12,10,11},{8,-24,125,12,10,10},{-45,78,-17,12,9,12}};
        String[] names={"flowering surface","upper lake","deep lake","settlement outskirts"};
        for (int i=0;i<regions.length;i++) {
            int[] r=regions[i];
            for (int x=r[0]-16;x<=r[0]+r[3]+16;x+=8) for (int z=r[2]-16;z<=r[2]+r[5]+16;z+=8)
                level.getChunkAt(new BlockPos(x,r[1],z));
            visitor.moveTo(r[0]+29.5,r[1]+4,r[2]+17.5,0,0);
            var cause = new SpawnCause(spawner,visitor);
            var zone = new SpawningZoneInput(cause,level,r[0],r[1],r[2],r[3],r[4],r[5]);
            boolean seen=false;
            for (int attempt=0;attempt<36 && !seen;attempt++) {
                for (Object obj:spawner.runForArea(zone,1)) {
                    if (obj instanceof PokemonEntity p && p.isAlive() && level.getEntity(p.getUUID())!=null) {
                        System.out.println("HEARTWATER WILD "+names[i]+" | "+p.getPokemon().getSpecies().getName()+" at "+p.blockPosition());
                        found++;
                        seen=true;
                    }
                }
            }
            if (!seen) System.out.println("HEARTWATER NO SAMPLE "+names[i]);
        }
        if (found<4) throw new AssertionError("Only "+found+" native Cobblemon habitat samples");
    }
}

