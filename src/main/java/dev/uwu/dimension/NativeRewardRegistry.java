package dev.uwu.dimension;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** World-owned assignments. Access never creates or replaces a missing world identity. */
final class NativeRewardRegistry extends SavedData {
    private static final String FILE = "uwudimension_native_rewards";
    private static final Factory<NativeRewardRegistry> FACTORY = new Factory<>(
            () -> { throw new IllegalStateException("Reward registry needs a durable world identity"); },
            NativeRewardRegistry::load, null);
    final UUID namespace;
    private record Placement(ResourceLocation dimension, BlockPos position) {}
    private final Map<UUID, NativeRewardAssignment> assignments = new HashMap<>();
    private final Map<Placement, UUID> placements = new HashMap<>();

    private NativeRewardRegistry(UUID namespace) {
        this.namespace = namespace;
    }

    static NativeRewardRegistry fresh(UUID namespace) {
        return new NativeRewardRegistry(namespace);
    }

    static NativeRewardRegistry get(ServerLevel level) {
        var server = level.getServer();
        if (!server.isSameThread()) return null;
        return server.overworld().getDataStorage().get(FACTORY, FILE);
    }

    static NativeRewardRegistry forNewChest(ServerLevel level, UUID namespace) {
        NativeRewardRegistry registry = get(level);
        if (registry != null || !level.getServer().isSameThread()) return registry;

        // A failed read returns null too. Keep an unreadable existing file intact.
        var path = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE + ".dat");
        if (!Files.notExists(path)) return null;
        registry = fresh(namespace);
        level.getServer().overworld().getDataStorage().set(FILE, registry);
        return registry;
    }

    NativeRewardAssignment original(UUID id) {
        return assignments.get(id);
    }

    boolean reconcile(UUID world, ResourceLocation dimension, BlockPos position,
                      NativeRewardAssignment savedChest) {
        return savedChest != null && namespace.equals(world)
                && savedChest.site().matches(world, dimension, position) && assign(savedChest);
    }

    boolean assign(NativeRewardAssignment assignment) {
        var site = assignment.site();
        UUID id = site.id();
        if (!namespace.equals(site.worldNamespace())) return false;
        NativeRewardAssignment existing = assignments.get(id);
        if (existing != null) return existing.equals(assignment);
        if (placements.putIfAbsent(new Placement(site.dimension(), site.placement()), id) != null) return false;
        assignments.put(id, assignment);
        setDirty();
        return true;
    }

    static NativeRewardRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.hasUUID("Namespace")
                || !(tag.get("Assignments") instanceof ListTag rows)
                || (!rows.isEmpty() && rows.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("Invalid native reward registry");
        }
        var result = new NativeRewardRegistry(tag.getUUID("Namespace"));
        for (Tag row : rows) {
            var assignment = NativeRewardAssignment.load((CompoundTag) row).orElseThrow(
                    () -> new IllegalStateException("Invalid native reward assignment"));
            var site = assignment.site();
            if (result.original(site.id()) != null || !result.assign(assignment)) {
                throw new IllegalStateException("Conflicting native reward assignment");
            }
        }
        result.setDirty(false);
        return result;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", 1);
        tag.putUUID("Namespace", namespace);
        var rows = new ListTag();
        assignments.values().forEach(assignment -> rows.add(assignment.save()));
        tag.put("Assignments", rows);
        return tag;
    }
}
