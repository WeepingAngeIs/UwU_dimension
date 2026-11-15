package dev.uwu.dimension;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable bounded receipts: distinct action UUIDs can never complete one another. */
final class GlitchCompletions extends SavedData {
    // One stack cannot start another action until its last receipt is read. The larger
    // history covers copied stacks and crash-restored saves without per-shot growth.
    private static final int HISTORY_PER_STACK = 64;
    private static final Factory<GlitchCompletions> FACTORY = new Factory<>(GlitchCompletions::new, GlitchCompletions::read, null);
    private CompoundTag completed = new CompoundTag();
    private static GlitchCompletions get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, "uwudimension_glitch_completions");
    }
    static void complete(ServerLevel level, UUID stackId, UUID token) {
        var data = get(level);
        var key = stackId.toString();
        ListTag history = data.completed.getList(key, Tag.TAG_STRING);
        for (Tag existing : history) if (existing.getAsString().equals(token.toString())) return;
        history.add(StringTag.valueOf(token.toString()));
        while (history.size() > HISTORY_PER_STACK) history.remove(0);
        data.completed.put(key, history);
        data.setDirty();
    }
    static boolean completed(ServerLevel level, UUID stackId, UUID token) {
        for (Tag existing : get(level).completed.getList(stackId.toString(), Tag.TAG_STRING))
            if (existing.getAsString().equals(token.toString())) return true;
        return false;
    }
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
