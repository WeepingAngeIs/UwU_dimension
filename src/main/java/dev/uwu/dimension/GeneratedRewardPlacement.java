package dev.uwu.dimension;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Places rewards in the unpublished center chunk only, after the backend decision is durable. */
final class GeneratedRewardPlacement {
    static void place(WorldGenLevel world, ChunkAccess chunk, BlockPos position, BlockPos anchor, Direction facing,
                      NativeRewardAssignment.Kind kind, BlockPos bell) {
        if (!(world instanceof WorldGenRegion region) || !region.getCenter().equals(new ChunkPos(position))
                || !chunk.getPos().equals(region.getCenter())
                || chunk.getPersistedStatus().getChunkType() != ChunkType.PROTOCHUNK) return;
        if (chunk.getBlockState(position).hasBlockEntity() || chunk.getBlockEntityNbt(position) != null) return;

        var level = region.getLevel();
        if (!ReefLife.isReach(level)) return;
        ResourceKey<LootTable> table = ResourceKey.create(Registries.LOOT_TABLE, kind.table);
        var preparedLootr = new AtomicReference<>(Optional.<BlockEntity>empty());
        NativeRewardIntents.Receipt receipt;
        try {
            receipt = NativeRewardIntents.forWorld(level.getServer()).reserve(level.dimension().location(),
                    anchor, kind, position, bell, assignment -> kind != NativeRewardAssignment.Kind.MIRROR_COURT
                            && AcquisitionPolicy.useLootrForNewPersonalChest(() -> {
                                var candidate = GeneratedLootrRewards.prepare(level, facing, assignment, table);
                                preparedLootr.set(candidate);
                                return candidate.isPresent();
                            }));
        } catch (IOException failure) {
            LogUtils.getLogger().warn("Could not reserve reward at {}", position, failure);
            return;
        }

        BlockEntity staged;
        BlockState state;
        if (receipt.backend() == NativeRewardIntents.Backend.NATIVE) {
            var block = switch (kind) {
                case ROSE_BATHHOUSE -> NativeRewardChests.PEARL.get();
                case TIDE_ARCHIVE, MIRROR_COURT -> NativeRewardChests.DUSK.get();
                case INVERTED_SHRINE -> NativeRewardChests.PETAL.get();
            };
            state = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            var chest = block.newBlockEntity(position, state);
            chest.stage(receipt.assignment());
            staged = chest;
        } else {
            // Reuse the validated draft for a new site. An existing receipt keeps its backend on retry.
            var candidate = preparedLootr.get()
                    .or(() -> GeneratedLootrRewards.prepare(level, facing, receipt.assignment(), table));
            if (candidate.isEmpty()) return;
            staged = candidate.get();
            state = staged.getBlockState();
        }

        if (!region.setBlock(position, state, 2)) return;
        // Replaces the DUMMY marker with the already initialized entity in the same chunk.
        chunk.removeBlockEntity(position);
        chunk.setBlockEntity(staged);
        chunk.setUnsaved(true);
    }

    static boolean isRewardChest(BlockState state) {
        return state.getBlock() instanceof NativeRewardChests.RewardChestBlock
                || BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(GeneratedLootrRewards.CHEST_ID);
    }

    static void checkManagedLootr(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var level = player.serverLevel();
        var position = event.getPos();
        if (!level.hasChunkAt(position) || !BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock())
                .equals(GeneratedLootrRewards.CHEST_ID)) return;
        var chest = level.getBlockEntity(position);
        if (chest == null || !GeneratedLootrRewards.hasMarker(chest)) return;
        try {
            var assignment = GeneratedLootrRewards.assignment(chest);
            if (assignment.isPresent()) {
                var expected = assignment.get();
                var site = expected.site();
                var table = ResourceKey.create(Registries.LOOT_TABLE, expected.kind().table);
                if (site.matches(site.worldNamespace(), level.dimension().location(), position)
                        && expected.kind() != NativeRewardAssignment.Kind.MIRROR_COURT
                        && NativeRewardIntents.forWorld(level.getServer()).find(expected)
                                .filter(receipt -> receipt.backend() == NativeRewardIntents.Backend.LOOTR).isPresent()
                        && GeneratedLootrRewards.compatible(level, table)
                        && GeneratedLootrRewards.matches(chest, expected, table)
                        && SakuraAccess.mayInteract(player, position)) return;
            }
        } catch (IOException failure) {
            LogUtils.getLogger().warn("Could not verify Lootr reward at {}", position, failure);
        }
        event.setCanceled(true);
    }

    private GeneratedRewardPlacement() {}
}
