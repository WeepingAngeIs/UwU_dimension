package dev.uwu.test;

import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;

final class AdvancementProof {
    private static final ResourceLocation ID = ResourceLocation.parse("uwudimension:a_rather_pretty_place");
    private static final List<ResourceKey<Level>> REALMS = List.of(
        ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("uwudimension:sakura_reach"))
    );

    private static int completedCriteria(net.minecraft.advancements.AdvancementProgress progress) {
        int count = 0;
        for (var ignored : progress.getCompletedCriteria()) count++;
        return count;
    }

    static boolean tick(ServerLevel level) {
        var server = level.getServer();
        AdvancementHolder advancement = server.getAdvancements().get(ID);
        if (advancement == null) throw new AssertionError("Realm entry advancement did not load");
        if (!advancement.value().display().orElseThrow().shouldShowToast()) throw new AssertionError("Advancement toast is disabled");
        if (!advancement.value().display().orElseThrow().getIcon().is(
            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("uwudimension:heartwater_locket"))))
            throw new AssertionError("Wrong advancement icon");
        for (var destination : REALMS) {
            try (var visitor = new ChestProof.Visitor(level, level.getSharedSpawnPos(), "Realm" + destination.location().getPath(), UUID.randomUUID())) {
                CriteriaTriggers.CHANGED_DIMENSION.trigger(visitor.player, Level.OVERWORLD, destination);
                if (!visitor.player.getAdvancements().getOrStartProgress(advancement).isDone())
                    throw new AssertionError("Entry into " + destination.location() + " did not complete the advancement");
                int completed = completedCriteria(visitor.player.getAdvancements().getOrStartProgress(advancement));
                if (completed != 1) throw new AssertionError("A single realm entry granted unexpected criteria: " + completed);
                CriteriaTriggers.CHANGED_DIMENSION.trigger(visitor.player, destination, Level.OVERWORLD);
                CriteriaTriggers.CHANGED_DIMENSION.trigger(visitor.player, Level.OVERWORLD, destination);
                if (completedCriteria(visitor.player.getAdvancements().getOrStartProgress(advancement)) != 1)
                    throw new AssertionError("Repeated visits changed the one-time reward");
            }
        }
        try (var visitor = new ChestProof.Visitor(level, level.getSharedSpawnPos(), "Elsewhere", UUID.randomUUID())) {
            CriteriaTriggers.CHANGED_DIMENSION.trigger(visitor.player, Level.OVERWORLD, Level.NETHER);
            if (visitor.player.getAdvancements().getOrStartProgress(advancement).isDone())
                throw new AssertionError("Entering another dimension incorrectly granted the advancement");
        }
        System.out.println("UWU ADVANCEMENT SERVER PASSED Sakura Reach entry, one-time completion, and unrelated-dimension exclusion");
        return true;
    }
}
