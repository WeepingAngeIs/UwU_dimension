package dev.uwu.test;

import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("heartwater_probe")
public class Expedition {
    private boolean completed;
    public Expedition() { NeoForge.EVENT_BUS.addListener(this::tick); }

    private void tick(ServerTickEvent.Post event) {
        if (!event.getServer().isDedicatedServer() || completed || event.getServer().getTickCount() < 10) return;
        var server = event.getServer();
        try {
            var key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("uwudimension","heartwater"));
            ServerLevel level = server.getLevel(key);
            if (level == null) throw new IllegalStateException("Dimension failed to load");
            completed = true;
            RouteProof.run(level);
            ChestProof.run(level, new BlockPos(0,53,103));
            ArsenalProof.run(level);
            long entries = PlayerSpawnerFactory.INSTANCE.getSpawnPool().getDetails().stream()
                    .filter(d->d.getId().startsWith("heartwater-")).count();
            if (entries!=19) throw new AssertionError("Native spawn definitions loaded: "+entries);
            System.out.println("HEARTWATER NATIVE POOL contains 19 dimension-specific definitions; natural populations sampled in graphical client");
            System.out.println("HEARTWATER EXPEDITION PASSED");
        } catch (Throwable error) {
            completed = true;
            System.err.println("HEARTWATER EXPEDITION FAILED");
            error.printStackTrace();
        } finally {
            if (completed) server.halt(false);
        }
    }

}
