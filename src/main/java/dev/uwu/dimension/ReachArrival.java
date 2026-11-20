package dev.uwu.dimension;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Dimension storage is only read and dirtied on the server thread. */
final class ReachArrival extends SavedData {
    private static final Factory<ReachArrival> FACTORY =
            new Factory<>(ReachArrival::new, ReachArrival::read, null);
    GroveSites.Site site;

    static void onLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && ReefLife.isReach(level)) {
            ReachArrival saved = level.getDataStorage().computeIfAbsent(FACTORY, "uwudimension_arrival");
            if (saved.site != null) GroveSites.restore(level, saved.site);
        }
    }

    static GroveSites.Site forTravel(ServerLevel level) {
        ReachArrival saved = level.getDataStorage().computeIfAbsent(FACTORY, "uwudimension_arrival");
        if (saved.site == null) {
            saved.site = GroveSites.first(level);
            saved.setDirty();
        }
        return saved.site;
    }

    private static ReachArrival read(CompoundTag tag, HolderLookup.Provider registries) {
        ReachArrival data = new ReachArrival();
        if (tag.contains("entryY")) {
            data.site = new GroveSites.Site(tag.getInt("x"), tag.getInt("z"), tag.getInt("turns") & 3,
                    tag.getInt("entryY"), tag.getInt("gx"), tag.getInt("gz"), tag.getBoolean("usable"), tag.getBoolean("shrine"));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (site == null) return tag;
        tag.putInt("x", site.x());
        tag.putInt("z", site.z());
        tag.putInt("turns", site.turns());
        tag.putInt("entryY", site.entryY());
        tag.putInt("gx", site.gx());
        tag.putInt("gz", site.gz());
        tag.putBoolean("usable", site.usable());
        tag.putBoolean("shrine", site.shrine());
        return tag;
    }
}
