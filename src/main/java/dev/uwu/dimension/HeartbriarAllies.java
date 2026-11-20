package dev.uwu.dimension;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.neoforged.fml.ModList;

final class HeartbriarAllies {
    private HeartbriarAllies() {}

    /** Server-thread exemption check. An unavailable installed party provider also exempts the candidate. */
    static boolean allied(ServerPlayer holder, LivingEntity other) {
        if (holder == other || HeartMagic.friendly(holder, other)) return true;
        UUID member;
        if (other instanceof ServerPlayer player) {
            member = player.getUUID();
        } else if (other instanceof OwnableEntity pet && pet.getOwnerUUID() != null) {
            member = pet.getOwnerUUID();
            if (holder.getUUID().equals(member)) return true;
            var owner = holder.serverLevel().getServer().getPlayerList().getPlayer(member);
            if (owner != null && HeartMagic.friendly(holder, owner)) return true;
        } else {
            return false;
        }
        return ModList.get().isLoaded("openpartiesandclaims")
            && OpenPac.exempt(holder.serverLevel().getServer(), holder.getUUID(), member);
    }

    // This project already treats OpenPAC as optional. Bind the installed
    // 0.33.0 runtime interface once and consult its live providers on each check.
    static final class OpenPac {
        private static final AtomicBoolean WARNED = new AtomicBoolean();
        private static final Methods API = bind();

        private record Methods(Method serverData, Method systems, Method primary, Method sameParty, Method allying) {}

        private static Methods bind() {
            try {
                var data = Class.forName("xaero.pac.common.server.ServerData", false, OpenPac.class.getClassLoader())
                    .getMethod("from", MinecraftServer.class);
                var systems = data.getReturnType().getMethod("getPlayerPartySystemManager");
                var manager = systems.getReturnType();
                return new Methods(data, systems, manager.getMethod("getPrimarySystem"),
                    manager.getMethod("areInSameParty", UUID.class, UUID.class),
                    manager.getMethod("isPlayerAllying", UUID.class, UUID.class));
            } catch (ReflectiveOperationException | LinkageError problem) {
                warn(problem);
                return null;
            }
        }

        static boolean exempt(MinecraftServer server, UUID holder, UUID candidate) {
            if (API == null) return true;
            try {
                var data = API.serverData.invoke(null, server);
                if (data == null) return true;
                var manager = API.systems.invoke(data);
                return manager == null || API.primary.invoke(manager) == null || related(manager, holder, candidate);
            } catch (ReflectiveOperationException | LinkageError problem) {
                warn(problem);
                return true;
            }
        }

        static boolean related(Object manager, UUID holder, UUID candidate) throws ReflectiveOperationException {
            return (boolean) API.sameParty.invoke(manager, holder, candidate)
                || (boolean) API.allying.invoke(manager, holder, candidate);
        }

        private static void warn(Throwable cause) {
            if (WARNED.compareAndSet(false, true)) {
                LogUtils.getLogger().warn("Heartbriar will spare players and their pets while OpenPAC party lookup is unavailable.", cause);
            }
        }
    }
}
