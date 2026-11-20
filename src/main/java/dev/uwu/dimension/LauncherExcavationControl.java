package dev.uwu.dimension;

import java.nio.file.Files;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Versioned server operator API for the Heart Launcher's world-wide excavation setting. */
public final class LauncherExcavationControl {
    public static final int API_VERSION = 1;
    private static final int FORMAT_VERSION = 1;
    private static final int OPERATOR_PERMISSION = 2;
    private static final String DATA_ID = "uwudimension_launcher_excavation";
    private static final SavedData.Factory<WorldSetting> FACTORY = new SavedData.Factory<>(
            () -> new WorldSetting(Mode.CONFIGURATION), WorldSetting::load, null);

    private LauncherExcavationControl() {}

    public enum Source { CONFIGURATION, WORLD_OVERRIDE, UNAVAILABLE }

    public record Status(boolean enabled, Source source) {}

    /** Empty when the command source lacks authority or is off the server thread. */
    public static Optional<Status> status(CommandSourceStack source) {
        if (!authorized(source)) return Optional.empty();
        return Optional.of(setting(source.getServer()).status(HeartLauncher.configuredExcavationEnabled()));
    }

    /** Saves an explicit world override and returns its effective value. Unreadable data is preserved. */
    public static Optional<Status> set(CommandSourceStack source, boolean enabled) {
        if (!authorized(source)) return Optional.empty();
        var world = setting(source.getServer());
        world.setOverride(enabled);
        return Optional.of(world.status(false));
    }

    static boolean hasPermission(CommandSourceStack source) {
        return source != null && source.hasPermission(OPERATOR_PERMISSION)
                && (source.source == source.getServer() || source.source instanceof ServerPlayer operator
                        && operator == source.getEntity() && operator.hasPermissions(OPERATOR_PERMISSION));
    }

    static boolean enabled(MinecraftServer server, boolean configuredDefault) {
        return setting(server).status(configuredDefault).enabled();
    }

    private static boolean authorized(CommandSourceStack source) {
        if (source == null || !hasPermission(source)) return false;
        MinecraftServer server = source.getServer();
        return server != null && server.isSameThread();
    }

    private static WorldSetting setting(MinecraftServer server) {
        if (server == null || !server.isSameThread() || server.overworld() == null) {
            return new WorldSetting(Mode.UNAVAILABLE);
        }
        try {
            var storage = server.overworld().getDataStorage();
            var saved = storage.get(FACTORY, DATA_ID);
            if (saved != null) return saved;

            // SavedData can return null for an unreadable file as well as a new world.
            var file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATA_ID + ".dat");
            var initial = new WorldSetting(Files.notExists(file) ? Mode.CONFIGURATION : Mode.UNAVAILABLE);
            storage.set(DATA_ID, initial);
            return initial;
        } catch (RuntimeException unavailable) {
            return new WorldSetting(Mode.UNAVAILABLE);
        }
    }

    private enum Mode { CONFIGURATION, WORLD_ON, WORLD_OFF, UNAVAILABLE }

    private static final class WorldSetting extends SavedData {
        private Mode mode;

        private WorldSetting(Mode mode) {
            this.mode = mode;
        }

        private Status status(boolean configuredDefault) {
            return switch (mode) {
                case CONFIGURATION -> new Status(configuredDefault, Source.CONFIGURATION);
                case WORLD_ON -> new Status(true, Source.WORLD_OVERRIDE);
                case WORLD_OFF -> new Status(false, Source.WORLD_OVERRIDE);
                case UNAVAILABLE -> new Status(false, Source.UNAVAILABLE);
            };
        }

        private void setOverride(boolean enabled) {
            if (mode == Mode.UNAVAILABLE) return;
            Mode next = enabled ? Mode.WORLD_ON : Mode.WORLD_OFF;
            if (mode != next) {
                mode = next;
                setDirty();
            }
        }

        private static WorldSetting load(CompoundTag tag, HolderLookup.Provider registries) {
            if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != FORMAT_VERSION
                    || !tag.contains("Enabled", Tag.TAG_BYTE)) return new WorldSetting(Mode.UNAVAILABLE);
            return new WorldSetting(switch (tag.getByte("Enabled")) {
                case 0 -> Mode.WORLD_OFF;
                case 1 -> Mode.WORLD_ON;
                default -> Mode.UNAVAILABLE;
            });
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            if (mode != Mode.WORLD_ON && mode != Mode.WORLD_OFF) {
                throw new IllegalStateException("Cannot save an unresolved Heart Launcher excavation setting");
            }
            tag.putInt("Version", FORMAT_VERSION);
            tag.putBoolean("Enabled", mode == Mode.WORLD_ON);
            return tag;
        }
    }
}
