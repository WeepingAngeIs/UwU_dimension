package dev.uwu.shrinecheck;

import static dev.uwu.shrinecheck.ShrineGate.require;

import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.ReefLife;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Can run on the pre-shrine jar. Stop normally after this stage to save the actual newly built road. */
public final class LegacyPetalShrineSeed {
    private ServerLevel level;
    private BlossomVillage.Plan petal;
    private ChunkPos eastRoad;
    private long started;
    private volatile boolean freshLoad;

    public LegacyPetalShrineSeed() {
        NeoForge.EVENT_BUS.addListener(this::recordLoad);
    }

    public boolean tick(MinecraftServer server) {
        if (level == null) {
            level = server.getLevel(ReefLife.REACH);
            require(level != null, "Sakura Reach is absent in the legacy save seed run");
            petal = BlossomVillage.first(level, false);
            eastRoad = ShrineGate.farEnd(petal);
            require(!eastRoad.equals(new ChunkPos(ShrineGate.center(petal))), "Bad old-world fixture geometry");
            started = level.getGameTime();
            level.getChunkSource().addRegionTicket(TicketType.FORCED, eastRoad, 0, eastRoad, true);
            return false;
        }
        if (level.getChunkSource().getChunkNow(eastRoad.x, eastRoad.z) == null) {
            require(level.getGameTime() - started < 1000, "Timed out generating the pre-upgrade Petal road");
            return false;
        }
        require(freshLoad, "The legacy Petal road fixture needs a fresh disposable save");
        ShrineGate.verifyRoad(level, petal, ShrineGate.road(petal, petal.road().length - 1, 0));
        require(level.getChunkSource().getChunkNow(ShrineGate.center(petal).getX() >> 4,
                        ShrineGate.center(petal).getZ() >> 4) == null,
                "The legacy setup unexpectedly loaded the west gate before the upgrade");
        System.out.println("PETAL SHRINE LEGACY WORLD SEEDED eastRoadChunk=" + eastRoad
                + ", westGate=" + ShrineGate.center(petal));
        return true;
    }

    private void recordLoad(ChunkEvent.Load event) {
        if (event.getLevel() == level && event.getChunk().getPos().equals(eastRoad)) freshLoad |= event.isNewChunk();
    }
}
