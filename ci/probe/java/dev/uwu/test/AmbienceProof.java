package dev.uwu.test;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Counts the particle packets at each individual connection over sixty real server ticks. */
final class AmbienceProof {
    private static final ArrayList<ChestProof.Visitor> visitors = new ArrayList<>();
    private static int elapsed;

    static boolean tick(ServerLevel level) {
        if (visitors.isEmpty()) {
            BlockPos together = new BlockPos(6,level.getMaxBuildHeight()-5,6);
            for (int i=0; i<4; i++) {
                var visitor = new ChestProof.Visitor(level,together,"PetalTest"+i,UUID.randomUUID());
                var root = visitor.player.getPersistentData();
                var saved = root.getCompound(Player.PERSISTED_NBT_TAG);
                saved.putInt("sakura_effects", i==3 ? 0 : 2);
                root.put(Player.PERSISTED_NBT_TAG,saved);
                visitors.add(visitor);
            }
        }
        for (var visitor : visitors) NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(visitor.player));
        if (++elapsed < 60) return false;
        try {
            var counts = visitors.stream().map(v -> v.receivedPetalPackets).toList();
            if (!counts.equals(java.util.List.of(2,2,2,0)))
                throw new AssertionError("Clustered sky petal packets in 60 ticks " + counts);
            System.out.println("SAKURA CLUSTERED PARTICLES PASS playerPacketCounts=" + counts + " ticks=" + elapsed);
        } finally {
            visitors.forEach(ChestProof.Visitor::close);
            visitors.clear();
        }
        return true;
    }
}
