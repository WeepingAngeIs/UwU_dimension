package dev.uwu.dimension;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Checks native interaction protection and, when present, the server's OpenPAC claim access. */
final class SakuraAccess {
    private SakuraAccess() {}
    static boolean mayInteract(ServerPlayer p, BlockPos pos) {
        var level=p.serverLevel();
        if(!level.mayInteract(p,pos))return false;
        if(!ModList.get().isLoaded("openpartiesandclaims"))return true;
        return OpenPac.allowed(p,pos);
    }
    /** Unattributed random/dispenser growth may use only land open to outsiders. */
    static boolean mayGrow(net.minecraft.server.level.ServerLevel level, ServerPlayer actor, BlockPos pos) {
        if(actor!=null)return actor.serverLevel()==level&&mayInteract(actor,pos);
        if(!ModList.get().isLoaded("openpartiesandclaims"))return true;
        return OpenPac.allowed(level,new UUID(0L,0L),pos);
    }
    private static final class OpenPac {
        private static Method api,protection,access;
        private static boolean attempted;
        static boolean allowed(ServerPlayer p,BlockPos pos) {
            return allowed(p.serverLevel(),p.getUUID(),pos);
        }
        static boolean allowed(net.minecraft.server.level.ServerLevel level,UUID actor,BlockPos pos) {
            try {
                if(!attempted){
                    attempted=true;
                    Class<?> server=Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
                    api=server.getMethod("get",MinecraftServer.class); protection=server.getMethod("getChunkProtection");
                    access=protection.getReturnType().getMethod("hasChunkAccess",UUID.class,ResourceLocation.class,int.class,int.class);
                }
                if(access==null)return false;
                Object rights=protection.invoke(api.invoke(null,level.getServer()));
                return Boolean.TRUE.equals(access.invoke(rights,actor,level.dimension().location(),pos.getX()>>4,pos.getZ()>>4));
            } catch(ReflectiveOperationException | LinkageError ex) { return false; }
        }
    }
}

