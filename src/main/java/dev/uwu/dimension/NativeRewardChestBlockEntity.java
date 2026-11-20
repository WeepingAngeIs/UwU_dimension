package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Contains the authored assignment only. Personal items are stored with the player. */
final class NativeRewardChestBlockEntity extends BlockEntity {
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
