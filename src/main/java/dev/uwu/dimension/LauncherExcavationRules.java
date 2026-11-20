package dev.uwu.dimension;

import java.lang.reflect.Method;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.neoforged.fml.ModList;

/** Launcher-only terrain checks. Ordinary block breaks and damage use their existing rules. */
final class LauncherExcavationRules {
    enum ClaimState { UNCLAIMED, CLAIMED, UNVERIFIED }

    private static final Set<String> OTHER_CLAIM_PROVIDERS = Set.of(
            "ftbchunks", "flan", "argonauts", "cadmus", "claimchunk", "chunkclaim",
            "chunkclaims", "claimchunks", "simpleclaims", "simple_claims", "teamclaims",
            "landclaim", "land_claim", "landclaims", "griefdefender", "griefprevention", "worldguard");

    static boolean mayBreak(ServerLevel level, ServerPlayer owner, BlockPos position) {
        if (!level.getServer().isSameThread() || owner.serverLevel() != level || owner.isSpectator()
                || !level.isInWorldBounds(position) || !level.hasChunkAt(position)) return false;
        var state = level.getBlockState(position);
        if (!mayBreak(true, true, true, state.is(BlockTags.MINEABLE_WITH_PICKAXE), state.hasBlockEntity(),
                owner.getMainHandItem().isCorrectToolForDrops(state), state.getDestroySpeed(level, position),
                ClaimState.UNCLAIMED)) return false;
        if (claims(level, position) != ClaimState.UNCLAIMED) return false;
        try {
            return SakuraAccess.mayInteract(owner, position);
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    static boolean mayBreak(boolean loaded, boolean insideWorld, boolean nativeAccess, boolean pickaxeBlock,
                            boolean hasBlockEntity, boolean correctTool, float hardness, ClaimState claims) {
        return loaded && insideWorld && nativeAccess && pickaxeBlock && !hasBlockEntity && correctTool
                && Float.isFinite(hardness) && hardness >= 0.0F && claims == ClaimState.UNCLAIMED;
    }

    private static ClaimState claims(ServerLevel level, BlockPos position) {
        var mods = ModList.get();
        if (OTHER_CLAIM_PROVIDERS.stream().anyMatch(mods::isLoaded)) return ClaimState.UNVERIFIED;
        if (!mods.isLoaded("openpartiesandclaims")) {
            return mods.isLoaded("opacbonusclaims") ? ClaimState.UNVERIFIED : ClaimState.UNCLAIMED;
        }
        if (!mods.getModContainerById("openpartiesandclaims")
                .map(mod -> mod.getModInfo().getVersion().toString().equals("0.33.0")).orElse(false)) {
            return ClaimState.UNVERIFIED;
        }
        return OpenPac.claim(level, position);
    }

    private record ClaimLookup(Method api, Method manager, Method dimension, Method region, Method getClaim) {
        static ClaimLookup resolve() {
            try {
                Class<?> type = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
                Method api = type.getMethod("get", MinecraftServer.class);
                Method manager = type.getMethod("getServerClaimsManager");
                // The manager's convenience get() can hide saved claims in disabled dimensions.
                Method dimension = manager.getReturnType().getMethod("getDimension", ResourceLocation.class);
                Method region = dimension.getReturnType().getMethod("getRegion", int.class, int.class);
                Method get = region.getReturnType().getMethod("get", int.class, int.class);
                if (!get.getReturnType().getName().equals("xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI")) return null;
                return new ClaimLookup(api, manager, dimension, region, get);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                return null;
            }
        }
    }

    private static final class OpenPac {
        private static final ClaimLookup LOOKUP = ClaimLookup.resolve();

        static ClaimState claim(ServerLevel level, BlockPos position) {
            if (LOOKUP == null) return ClaimState.UNVERIFIED;
            try {
                Object api = LOOKUP.api().invoke(null, level.getServer());
                if (api == null) return ClaimState.UNVERIFIED;
                Object manager = LOOKUP.manager().invoke(api);
                if (manager == null) return ClaimState.UNVERIFIED;
                Object dimension = LOOKUP.dimension().invoke(manager, level.dimension().location());
                if (dimension == null) return ClaimState.UNCLAIMED;
                int chunkX = SectionPos.blockToSectionCoord(position.getX());
                int chunkZ = SectionPos.blockToSectionCoord(position.getZ());
                Object region = LOOKUP.region().invoke(dimension, Math.floorDiv(chunkX, 32), Math.floorDiv(chunkZ, 32));
                if (region == null) return ClaimState.UNCLAIMED;
                Object claim = LOOKUP.getClaim().invoke(region, Math.floorMod(chunkX, 32), Math.floorMod(chunkZ, 32));
                return claim == null ? ClaimState.UNCLAIMED : ClaimState.CLAIMED;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                return ClaimState.UNVERIFIED;
            }
        }
    }

    private LauncherExcavationRules() {}
}
