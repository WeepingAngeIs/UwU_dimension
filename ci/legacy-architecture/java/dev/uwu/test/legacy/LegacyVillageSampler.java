package dev.uwu.test.legacy;

import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.ReefLife;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Runs only during the previous release's boot. Record a genuine old Dusk district using its own generator. */
@Mod("uwu_architecture_legacy")
public class LegacyVillageSampler {
    public LegacyVillageSampler(){NeoForge.EVENT_BUS.addListener(this::started);}
    private void started(ServerStartedEvent event) {
        ServerLevel level=event.getServer().getLevel(ReefLife.REACH);
        var origin=GroveSites.first(level);
        for(int ring=4;ring<=15;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
            var site=GroveSites.site(level,origin.gx()+dx,origin.gz()+dz);
            if(!site.usable())continue;
            var plan=BlossomVillage.plan(level,site,true);
            if(plan.isEmpty())continue;
            var village=plan.get();
            var house=village.houses().stream().filter(h->h.style()==3).findFirst().orElseThrow();
            Map<String,BlockPos> sample=new LinkedHashMap<>();
            sample.put("bell",village.site().pos(village.x()+2,village.y()+1,village.z()-2));
            sample.put("door",village.doorway(house));sample.put("upper",village.doorway(house).above());
            sample.put("bed",village.bedHead(house));sample.put("job",village.workstation(house));
            for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=1;y<=14;y++){
                var p=site.pos(house.x()+x,house.floor()+y,house.z()+z);
                String block=BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString();
                if(block.equals("minecraft:spruce_planks")||block.equals("minecraft:oak_fence")
                        ||block.equals("minecraft:spruce_trapdoor"))sample.putIfAbsent(block,p);
            }
            if(!sample.containsKey("minecraft:spruce_planks")||!sample.containsKey("minecraft:oak_fence"))
                throw new AssertionError("Expected old timber and original rails at "+village.plaza()+"; sampled "+sample);
            var report=new StringBuilder().append(site.gx()).append(" ").append(site.gz()).append(" ")
                    .append(village.plaza().asLong()).append("\n");
            for(var p:sample.values())report.append(p.asLong()).append(" ").append(level.getBlockState(p)).append("\n");
            try{Files.writeString(Path.of("old-native-dusk.txt"),report);}
            catch(java.io.IOException error){throw new IllegalStateException(error);}
            System.out.println("BASELINE NATURAL DUSK PASS | "+village.plaza()+" | "+sample);
            return;
        }
        throw new AssertionError("No previous-release Dusk settlement on seed "+level.getSeed());
    }
}
