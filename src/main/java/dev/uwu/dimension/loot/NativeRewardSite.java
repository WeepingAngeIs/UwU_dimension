package dev.uwu.dimension.loot;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Identifies one authored reward slot in one world and binds its saved descriptor to its placement. */
public record NativeRewardSite(UUID worldNamespace, ResourceLocation dimension, BlockPos structureAnchor,
                               ResourceLocation slot, BlockPos placement) {
    private static final int FORMAT_VERSION = 1;
    private static final String ID_DOMAIN = "uwudimension/native_reward_site/identity/v1";
    private static final String PLACEMENT_DOMAIN = "uwudimension/native_reward_site/placement/v1";

    public NativeRewardSite {
        Objects.requireNonNull(worldNamespace, "worldNamespace");
        Objects.requireNonNull(dimension, "dimension");
        structureAnchor = Objects.requireNonNull(structureAnchor, "structureAnchor").immutable();
        Objects.requireNonNull(slot, "slot");
        placement = Objects.requireNonNull(placement, "placement").immutable();
    }

    public UUID id() {
        return namedId(ID_DOMAIN + "\n" + worldNamespace + "\n" + dimension + "\n"
                + coordinates(structureAnchor) + "\n" + slot);
    }

    public boolean matches(UUID trustedWorldNamespace, ResourceLocation observedDimension, BlockPos observedPosition) {
        return worldNamespace.equals(trustedWorldNamespace)
                && dimension.equals(observedDimension)
                && placement.equals(observedPosition);
    }

    public CompoundTag save() {
        UUID siteId = id();
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", FORMAT_VERSION);
        tag.putUUID("World", worldNamespace);
        tag.putString("Dimension", dimension.toString());
        tag.putIntArray("Anchor", toArray(structureAnchor));
        tag.putString("Slot", slot.toString());
        tag.putIntArray("Placement", toArray(placement));
        tag.putUUID("Id", siteId);
        tag.putUUID("Binding", placementBinding(siteId));
        return tag;
    }

    /**
     * Validates encoded data and its checksums. Call the context-checked overload before using a loaded site.
     * An authored placement or a trusted registry is the authority for newly created descriptors.
     */
    public static Optional<NativeRewardSite> load(CompoundTag tag) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != FORMAT_VERSION
                || !tag.hasUUID("World") || !tag.hasUUID("Id") || !tag.hasUUID("Binding")
                || !tag.contains("Dimension", Tag.TAG_STRING) || !tag.contains("Slot", Tag.TAG_STRING)
                || !tag.contains("Anchor", Tag.TAG_INT_ARRAY) || !tag.contains("Placement", Tag.TAG_INT_ARRAY)) {
            return Optional.empty();
        }
        ResourceLocation dimension = canonicalLocation(tag.getString("Dimension"));
        ResourceLocation slot = canonicalLocation(tag.getString("Slot"));
        int[] anchor = tag.getIntArray("Anchor");
        int[] placement = tag.getIntArray("Placement");
        if (dimension == null || slot == null || anchor.length != 3 || placement.length != 3) {
            return Optional.empty();
        }
        NativeRewardSite site = new NativeRewardSite(tag.getUUID("World"), dimension, fromArray(anchor),
                slot, fromArray(placement));
        UUID siteId = site.id();
        if (!siteId.equals(tag.getUUID("Id")) || !site.placementBinding(siteId).equals(tag.getUUID("Binding"))) {
            return Optional.empty();
        }
        return Optional.of(site);
    }

    public static Optional<NativeRewardSite> load(CompoundTag tag, UUID trustedWorldNamespace,
                                                  ResourceLocation dimension, BlockPos observedPosition) {
        return load(tag).filter(site -> site.matches(trustedWorldNamespace, dimension, observedPosition));
    }

    private UUID placementBinding(UUID siteId) {
        return namedId(PLACEMENT_DOMAIN + "\n" + siteId + "\n" + coordinates(placement));
    }

    private static ResourceLocation canonicalLocation(String raw) {
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        return parsed != null && parsed.toString().equals(raw) ? parsed : null;
    }

    private static int[] toArray(BlockPos position) {
        return new int[]{position.getX(), position.getY(), position.getZ()};
    }

    private static BlockPos fromArray(int[] position) {
        return new BlockPos(position[0], position[1], position[2]);
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static UUID namedId(String name) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8));
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x80); // Custom deterministic version 8.
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
            ByteBuffer bits = ByteBuffer.wrap(hash);
            return new UUID(bits.getLong(), bits.getLong());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
