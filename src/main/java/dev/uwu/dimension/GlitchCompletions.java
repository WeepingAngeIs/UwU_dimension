package dev.uwu.dimension;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable completion receipts: unloaded arrows and offline owners must not advance early. */
final class GlitchCompletions extends SavedData {
    private static final Factory<GlitchCompletions> FACTORY = new Factory<>(GlitchCompletions::new, GlitchCompletions::read, null);
    private CompoundTag completed = new CompoundTag();
    private static GlitchCompletions get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, "uwudimension_glitch_completions");
    }
    static void complete(ServerLevel level, UUID token) {
        var data = get(level);
        data.completed.putBoolean(token.toString(), true);
        data.setDirty();
    }
    static boolean completed(ServerLevel level, UUID token) { return get(level).completed.getBoolean(token.toString()); }
    private static GlitchCompletions read(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new GlitchCompletions();
        data.completed = tag.getCompound("completed").copy();
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("completed", completed.copy());
        return tag;
    }
}
