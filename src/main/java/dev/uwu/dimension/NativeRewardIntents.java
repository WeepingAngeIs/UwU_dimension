package dev.uwu.dimension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import dev.uwu.dimension.loot.NativeRewardSite;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** The placement decision is durable before generation can write a reward block. */
final class NativeRewardIntents {
    enum Backend { NATIVE, LOOTR }

    record Receipt(NativeRewardAssignment assignment, Backend backend) {
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Version", 1);
            tag.putString("Backend", backend.name());
            tag.put("Assignment", assignment.save());
            return tag;
        }

        private static Receipt load(CompoundTag tag) throws IOException {
            if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                    || !tag.contains("Backend", Tag.TAG_STRING) || !tag.contains("Assignment", Tag.TAG_COMPOUND)) {
                throw new IOException("Invalid reward placement receipt");
            }
            var assignment = NativeRewardAssignment.load(tag.getCompound("Assignment"))
                    .orElseThrow(() -> new IOException("Invalid reward placement assignment"));
            try {
                return new Receipt(assignment, Backend.valueOf(tag.getString("Backend")));
            } catch (IllegalArgumentException unknown) {
                throw new IOException("Unknown reward backend", unknown);
            }
        }
    }

    private final Path directory;
    private UUID namespace;
    private static final Map<MinecraftServer, NativeRewardIntents> WORLDS =
            Collections.synchronizedMap(new WeakHashMap<>());

    static NativeRewardIntents forWorld(MinecraftServer server) {
        return WORLDS.computeIfAbsent(server, world -> new NativeRewardIntents(
                world.getWorldPath(LevelResource.ROOT).resolve("data/uwudimension_reward_intents")));
    }

    NativeRewardIntents(Path directory) {
        this.directory = directory;
    }

    synchronized Receipt reserve(ResourceLocation dimension, BlockPos anchor, NativeRewardAssignment.Kind kind,
                                 BlockPos placement, BlockPos bell, Predicate<NativeRewardAssignment> preparedLootr)
            throws IOException {
        UUID world = worldNamespace(true).orElseThrow();
        var site = new NativeRewardSite(world, dimension, anchor, kind.slot, placement);
        var assignment = new NativeRewardAssignment(site, bell);
        var current = readReceipt(site.id());
        if (current.isPresent()) {
            if (!current.get().assignment().equals(assignment)) throw new IOException("Conflicting reward placement");
            return current.get();
        }
        var receipt = new Receipt(assignment, preparedLootr.test(assignment) ? Backend.LOOTR : Backend.NATIVE);
        writeDurably(receiptFile(site.id()), receipt.save());
        return receipt;
    }

    synchronized Optional<Receipt> find(NativeRewardAssignment assignment) throws IOException {
        var world = worldNamespace(false);
        if (world.isEmpty() || !world.get().equals(assignment.site().worldNamespace())) return Optional.empty();
        return readReceipt(assignment.site().id()).filter(receipt -> receipt.assignment().equals(assignment));
    }

    private Optional<UUID> worldNamespace(boolean create) throws IOException {
        if (namespace != null) return Optional.of(namespace);
        Path file = directory.resolve("world.dat");
        if (Files.notExists(file)) {
            if (!create) return Optional.empty();
            Files.createDirectories(directory);
            try (var records = Files.newDirectoryStream(directory, "*.dat")) {
                if (records.iterator().hasNext()) throw new IOException("Reward world identity is missing");
            }
            UUID id = UUID.randomUUID();
            var tag = new CompoundTag();
            tag.putUUID("World", id);
            writeDurably(file, tag);
            namespace = id;
        } else {
            var tag = read(file);
            if (!tag.hasUUID("World")) throw new IOException("Invalid reward world identity");
            namespace = tag.getUUID("World");
        }
        return Optional.of(namespace);
    }

    private Optional<Receipt> readReceipt(UUID id) throws IOException {
        Path file = receiptFile(id);
        return Files.notExists(file) ? Optional.empty() : Optional.of(Receipt.load(read(file)));
    }

    private Path receiptFile(UUID id) {
        return directory.resolve(id + ".dat");
    }

    private static CompoundTag read(Path path) throws IOException {
        try {
            return NbtIo.readCompressed(path, NbtAccounter.create(64 * 1024L));
        } catch (ReportedNbtException | NbtAccounterException failure) {
            throw new IOException("Invalid reward placement data in " + path.getFileName(), failure);
        }
    }

    private static void writeDurably(Path destination, CompoundTag tag) throws IOException {
        Path staging = Files.createTempFile(destination.getParent(), "writing-", ".tmp");
        try {
            try (var output = Files.newOutputStream(staging, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC)) {
                NbtIo.writeCompressed(tag, output);
            }
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(staging);
        }
    }
}
