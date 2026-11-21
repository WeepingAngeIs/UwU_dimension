package dev.uwu.dimension;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Contains the authored assignment only. Personal items are stored with the player. */
public final class NativeRewardChestBlockEntity extends BlockEntity {
    private static final int LID_EVENT = 1;
    private static final int VIEWER_RECHECK_TICKS = 5;
    private final Set<NativeRewardChestMenu> viewers = new HashSet<>();
    private boolean lidOpen;
    private float lidStart;
    private long lidStartTick;
    private NativeRewardAssignment assignment;
    private Tag invalidAssignment;

    NativeRewardChestBlockEntity(BlockPos pos, BlockState state) {
        super(NativeRewardChests.BLOCK_ENTITY.get(), pos, state);
    }

    NativeRewardAssignment assignment() {
        return assignment;
    }

    boolean canBind(NativeRewardAssignment proposed) {
        return invalidAssignment == null && (assignment == null || assignment.equals(proposed));
    }

    void stage(NativeRewardAssignment proposed) {
        if (!canBind(proposed)) throw new IllegalStateException("Conflicting reward block assignment");
        assignment = proposed;
    }

    void viewerOpened(NativeRewardChestMenu menu) {
        if (level instanceof ServerLevel server && menu.isViewerOf(this) && viewers.add(menu) && viewers.size() == 1) {
            updateLid(server, true);
            server.scheduleTick(worldPosition, getBlockState().getBlock(), VIEWER_RECHECK_TICKS);
        }
    }

    void viewerClosed(NativeRewardChestMenu menu) {
        if (level instanceof ServerLevel server && viewers.remove(menu) && viewers.isEmpty()) updateLid(server, false);
    }

    void recheckViewers(ServerLevel server) {
        if (viewers.isEmpty()) return;
        viewers.removeIf(menu -> !menu.isViewerOf(this));
        if (viewers.isEmpty()) updateLid(server, false);
        else server.scheduleTick(worldPosition, getBlockState().getBlock(), VIEWER_RECHECK_TICKS);
    }

    private void updateLid(ServerLevel server, boolean open) {
        if (isRemoved() || !server.hasChunkAt(worldPosition) || server.getBlockEntity(worldPosition) != this) return;
        BlockState state = getBlockState();
        if (!state.is(NativeRewardChests.DUSK.get()) || lidOpen == open) return;
        beginMotion(open);
        server.playSound(null, worldPosition, open ? NativeRewardChests.DUSK_OPEN.get() : NativeRewardChests.DUSK_CLOSE.get(),
                SoundSource.BLOCKS, .5F, 1F);
        server.blockEvent(worldPosition, state.getBlock(), LID_EVENT, open ? 1 : 0);
        // Block events have a distance cutoff. Send the same motion to every tracker of the chunk.
        server.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private void beginMotion(boolean open) {
        lidStart = lidOpenness(0F);
        lidStartTick = level.getGameTime();
        lidOpen = open;
    }

    /** Linear 0..1 lid travel; the renderer chooses its hinge angle and easing. */
    public float lidOpenness(float partialTick) {
        if (level == null) return 0F;
        float elapsed = Math.max(0F, (level.getGameTime() - lidStartTick) + partialTick);
        return Mth.clamp(lidStart + (lidOpen ? elapsed : -elapsed) / 10F, 0F, 1F);
    }

    @Override public boolean triggerEvent(int event, int value) {
        if (event != LID_EVENT || level == null || !getBlockState().is(NativeRewardChests.DUSK.get())) {
            return super.triggerEvent(event, value);
        }
        boolean open = value != 0;
        // A rapid reversal can leave an older event in the server's deduplicated queue.
        if (!level.isClientSide) return open == lidOpen;
        if (open != lidOpen) beginMotion(open);
        return true;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (getBlockState().is(NativeRewardChests.DUSK.get())) {
            tag.putBoolean("LidOpen", lidOpen);
            tag.putFloat("LidStart", lidStart);
            tag.putLong("LidStartTick", lidStartTick);
        }
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return getBlockState().is(NativeRewardChests.DUSK.get()) ? ClientboundBlockEntityDataPacket.create(this) : null;
    }

    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (getBlockState().is(NativeRewardChests.DUSK.get())) {
            lidOpen = tag.getBoolean("LidOpen");
            lidStart = tag.getFloat("LidStart");
            lidStartTick = tag.getLong("LidStartTick");
        } else {
            super.handleUpdateTag(tag, registries);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (assignment != null) tag.put("Reward", assignment.save());
        else if (invalidAssignment != null) tag.put("Reward", invalidAssignment.copy());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        assignment = tag.contains("Reward", Tag.TAG_COMPOUND)
                ? NativeRewardAssignment.load(tag.getCompound("Reward")).orElse(null) : null;
        invalidAssignment = assignment == null && tag.contains("Reward") ? tag.get("Reward").copy() : null;
    }
}
