package dev.uwu.test;

import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/** Loads an original local test avatar; never uploads the owner's private avatar. */
final class FiguraPivotProof {
    private static boolean requested;
    private static long requestedAt;
    private static UUID mirrored;
    static boolean ready(Minecraft mc, UUID remote) throws Exception {
        if(!net.neoforged.fml.ModList.get().isLoaded("figura"))return true;
        Class<?> manager=Class.forName("org.figuramc.figura.avatar.AvatarManager");
        if(!requested){
            requested=true;
            requestedAt=System.nanoTime();
            Path fixture=mc.gameDirectory.toPath().resolve("pivot-avatar");
            if(!java.nio.file.Files.isRegularFile(fixture.resolve("avatar.json")))throw new AssertionError("Missing original Figura fixture "+fixture);
            manager.getMethod("loadLocalAvatar",Path.class).invoke(null,fixture);
            return false;
        }
        if(System.nanoTime()-requestedAt>30_000_000_000L&&mirrored==null)throw new AssertionError("Figura local fixture failed to initialize within 30s");
        Object avatar=manager.getMethod("getLoadedAvatar",UUID.class).invoke(null,mc.player.getUUID());
        if(avatar==null)return false;
        Field nbt=avatar.getClass().getField("nbt");
        Object data=nbt.get(avatar);
        if(data==null)return false;
        Field error=avatar.getClass().getField("scriptError");
        if(error.getBoolean(avatar))throw new AssertionError("Figura test avatar script failed");
        if(!remote.equals(mirrored)){
            manager.getMethod("setAvatar",UUID.class,net.minecraft.nbt.CompoundTag.class).invoke(null,remote,data);
            mirrored=remote;
            System.out.println("FIGURA PIVOT FIXTURE LOADED "+mc.player.getUUID()+" remote="+remote);
            return false;
        }
        return true;
    }
}
