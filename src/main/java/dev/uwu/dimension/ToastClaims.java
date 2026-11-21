package dev.uwu.dimension;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** The authoritative token belongs to one position in this save's Sakura Reach. */
final class ToastClaims {
    private static final Map<MinecraftServer, ToastClaims> WORLDS = Collections.synchronizedMap(new WeakHashMap<>());
    private final Path directory;
    private final Set<BlockPos> failedRetirements = new HashSet<>();

    private ToastClaims(Path directory) {
        this.directory = directory;
    }

    static ToastClaims forServer(MinecraftServer server) {
        return WORLDS.computeIfAbsent(server, s -> new ToastClaims(s.getWorldPath(LevelResource.ROOT)
                .resolve("data/uwudimension_baker_toasters/sakura_reach")));
    }

    private Path receipt(BlockPos pos, String state) {
        return directory.resolve(pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "." + state);
    }

    /** Only the generated, unpublished placement calls this. A spent position can never be reissued. */
    synchronized Optional<UUID> issue(BlockPos pos) throws IOException {
        if (failedRetirements.contains(pos)) return Optional.empty();
        Path ready = receipt(pos, "ready");
        if (present(receipt(pos, "spent"))) return Optional.empty();
        if (present(ready)) return Optional.of(readToken(ready, pos));

        Files.createDirectories(directory);
        UUID token = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putString("Dimension", ReefLife.REACH.location().toString());
        tag.putLong("Position", pos.asLong());
        tag.putUUID("Token", token);
        Path stage = Files.createTempFile(directory, "issuing-", ".tmp");
        try {
            try (var out = Files.newOutputStream(stage, StandardOpenOption.WRITE, StandardOpenOption.SYNC)) {
                NbtIo.writeCompressed(tag, out);
            }
            publish(stage, ready);
        } finally {
            Files.deleteIfExists(stage);
        }
        return Optional.of(token);
    }

    synchronized boolean ready(BlockPos pos, UUID token) throws IOException {
        Path ready = receipt(pos, "ready");
        return !failedRetirements.contains(pos) && !present(receipt(pos, "spent")) && present(ready)
                && token.equals(readToken(ready, pos));
    }

    /** Atomically retire the only ready receipt before publishing an item or allowing replacement. */
    synchronized boolean take(BlockPos pos, UUID token) throws IOException {
        if (!ready(pos, token)) return false;
        publish(receipt(pos, "ready"), receipt(pos, "spent"));
        return true;
    }

    synchronized void retire(BlockPos pos, UUID token) throws IOException {
        try {
            take(pos, token);
        } catch (IOException failure) {
            failedRetirements.add(pos.immutable());
            throw failure;
        }
    }

    /** Only the original live toaster may retry after the entity manager definitively rejected its bread. */
    synchronized boolean retryRejectedSpawn(BlockPos pos, UUID token) throws IOException {
        Path spent = receipt(pos, "spent");
        if (failedRetirements.contains(pos) || present(receipt(pos, "ready"))
                || !present(spent) || !token.equals(readToken(spent, pos))) {
            return false;
        }
        publish(spent, receipt(pos, "ready"));
        return true;
    }

    private static boolean present(Path path) throws IOException {
        try {
            if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile()) {
                throw new IOException("Unexpected toaster receipt type at " + path.getFileName());
            }
            return true;
        } catch (NoSuchFileException missing) {
            return false;
        }
    }

    private void publish(Path from, Path to) throws IOException {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        // POSIX requires flushing the containing directory to persist a rename across power loss.
        if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            try (var parent = FileChannel.open(directory, StandardOpenOption.READ)) {
                parent.force(true);
            }
        }
    }

    private UUID readToken(Path file, BlockPos pos) throws IOException {
        CompoundTag tag;
        try {
            tag = NbtIo.readCompressed(file, NbtAccounter.create(4096));
        } catch (ReportedNbtException | NbtAccounterException malformed) {
            throw new IOException("Corrupt toaster receipt at " + pos, malformed);
        }
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                || !ReefLife.REACH.location().toString().equals(tag.getString("Dimension"))
                || !tag.contains("Position", Tag.TAG_LONG) || tag.getLong("Position") != pos.asLong()
                || !tag.hasUUID("Token")) throw new IOException("Invalid toaster receipt at " + pos);
        return tag.getUUID("Token");
    }
}
