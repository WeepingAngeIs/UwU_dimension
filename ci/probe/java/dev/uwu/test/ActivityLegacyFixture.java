package dev.uwu.test;

import java.nio.file.*;
import java.util.UUID;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/** Runs with the preserved beta.9 main JAR. Never links the new activity implementation. */
final class ActivityLegacyFixture {
    static void run(ServerLevel level)throws Exception{
        var uuid=UUID.fromString("c85f9018-bd56-4d65-a0ab-777700000001");
        try(var visitor=new ChestProof.Visitor(level,level.getSharedSpawnPos(),"ActivityA",uuid)){
            var p=visitor.player;CriteriaTriggers.CHANGED_DIMENSION.trigger(p,Level.OVERWORLD,level.dimension());
            var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("uwudimension:roseglass_prism")));
            p.getInventory().add(stack.copy());CriteriaTriggers.INVENTORY_CHANGED.trigger(p,p.getInventory(),stack);
            p.getAdvancements().save();var file=p.server.getWorldPath(LevelResource.ROOT).resolve("advancements").resolve(uuid+".json");
            Files.copy(file,Path.of("activity-old-progress.json"),StandardCopyOption.REPLACE_EXISTING);
            System.out.println("ACTIVITY BETA9 BASELINE SAVED root entry and native recipe progress");
        }
    }
}
