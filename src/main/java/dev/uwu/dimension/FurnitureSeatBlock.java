package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Common interaction for a furniture seat. The block keeps its identity while occupied. */
public class FurnitureSeatBlock extends Block {
    private final float height;

    public FurnitureSeatBlock(Properties properties, float height) {
        super(properties);
        this.height = height;
    }

    public float seatHeight(BlockState state) {
        return height;
    }

    boolean canSit(BlockState state) {
        return true;
    }

    static boolean clearAbove(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState state = level.getBlockState(above);
        return state.is(BlockTags.TRAPDOORS) || state.getCollisionShape(level, above).isEmpty();
    }

    static boolean mayUse(ServerPlayer player, BlockPos pos) {
        return player.isAlive() && !player.isSpectator() && !(player instanceof FakePlayer)
                && player.canInteractWithBlock(pos, 1.0) && player.serverLevel().getWorldBorder().isWithinBounds(pos)
                && SakuraAccess.mayInteract(player, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive() || player.isPassenger() || !canSit(state) || !clearAbove(level, pos)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer actor) || !mayUse(actor, pos)) {
            return InteractionResult.FAIL;
        }
        return NativeSeatMount.sit(server, pos, actor) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return NativeSeatMount.occupied(level, pos) ? 15 : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide && !next.is(this)) NativeSeatMount.releaseAt(level, pos);
        super.onRemove(state, level, pos, next, moving);
    }
}
