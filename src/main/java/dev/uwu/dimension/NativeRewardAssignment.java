package dev.uwu.dimension;

import dev.uwu.dimension.loot.NativeRewardSite;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Server-authored storage and lock information for a single native reward chest. */
record NativeRewardAssignment(NativeRewardSite site, BlockPos courtBell) {
    enum Kind {
        ROSE_BATHHOUSE("rose_bathhouse", "pearl_reward_chest"),
        TIDE_ARCHIVE("tide_archive", "dusk_reward_chest"),
        INVERTED_SHRINE("inverted_shrine", "petal_reward_chest"),
        MIRROR_COURT("mirror_court", "dusk_reward_chest");

        final ResourceLocation slot;
        final ResourceLocation table;
        final String theme;

        Kind(String path, String theme) {
            this.slot = ResourceLocation.fromNamespaceAndPath("uwudimension", path);
            this.table = ResourceLocation.fromNamespaceAndPath("uwudimension", "chests/" + path);
            this.theme = theme;
        }

        static Optional<Kind> forSlot(ResourceLocation slot) {
            for (Kind kind : values()) if (kind.slot.equals(slot)) return Optional.of(kind);
            return Optional.empty();
        }
    }

    NativeRewardAssignment {
        Objects.requireNonNull(site);
        Kind kind = Kind.forSlot(site.slot()).orElseThrow(() -> new IllegalArgumentException("Unknown reward slot"));
        if (kind == Kind.MIRROR_COURT) {
            if (courtBell == null || !adjacentCourt(site.placement(), courtBell)) {
                throw new IllegalArgumentException("Mirror Court reward requires its paired bell");
            }
            courtBell = courtBell.immutable();
        } else if (courtBell != null) {
            throw new IllegalArgumentException("Only the Mirror Court reward uses a court bell");
        }
    }

    Kind kind() {
        return Kind.forSlot(site.slot()).orElseThrow();
    }

    private static boolean adjacentCourt(BlockPos chest, BlockPos bell) {
        long dx = Math.abs((long) chest.getX() - bell.getX());
        long dz = Math.abs((long) chest.getZ() - bell.getZ());
        return (long) bell.getY() - chest.getY() == 1 && (dx == 0 && dz == 13 || dz == 0 && dx == 13);
    }

    boolean authorizes(UUID worldNamespace, ResourceLocation dimension, BlockPos position,
                       NativeRewardAssignment original) {
        return equals(original) && site.matches(worldNamespace, dimension, position);
    }

    boolean unlocked(Predicate<BlockPos> defeatedCourt) {
        return courtBell == null || defeatedCourt.test(courtBell);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Site", site.save());
        if (courtBell != null) tag.putLong("CourtBell", courtBell.asLong());
        return tag;
    }

    static Optional<NativeRewardAssignment> load(CompoundTag tag) {
        if (!tag.contains("Site", Tag.TAG_COMPOUND)
                || tag.contains("CourtBell") && !tag.contains("CourtBell", Tag.TAG_LONG)) return Optional.empty();
        var site = NativeRewardSite.load(tag.getCompound("Site"));
        if (site.isEmpty()) return Optional.empty();
        BlockPos bell = tag.contains("CourtBell") ? BlockPos.of(tag.getLong("CourtBell")) : null;
        try {
            return Optional.of(new NativeRewardAssignment(site.get(), bell));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
