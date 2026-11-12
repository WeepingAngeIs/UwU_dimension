package dev.uwu.dimension;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Soft pastel fruit leaves that regrow fruit after players pick the visible clusters. */
final class FruitingLeavesBlock extends LeavesBlock {
    static final BooleanProperty FRUITING = BooleanProperty.create("fruiting");

    private final Supplier<? extends Item> fruit;

    FruitingLeavesBlock(Properties properties, Supplier<? extends Item> fruit) {
        super(properties);
        this.fruit = fruit;
        registerDefaultState(defaultBlockState().setValue(FRUITING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FRUITING);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return super.isRandomlyTicking(state) || !state.getValue(FRUITING);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        BlockState current = level.getBlockState(pos);
        if (!current.is(this) || current.getValue(FRUITING) || !ReefLife.isReach(level)) return;
        if (level.getMaxLocalRawBrightness(pos) < 9 || random.nextInt(8) != 0) return;
        level.setBlock(pos, current.setValue(FRUITING, true), Block.UPDATE_CLIENTS);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + .5, pos.getY() + .55, pos.getZ() + .5,
                2, .18, .18, .18, .015);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!state.getValue(FRUITING) || !ReefLife.isReach(level)) return super.useWithoutItem(state, level, pos, player, hit);
        if (!level.isClientSide) {
            popResource(level, pos, new ItemStack(fruit.get(), 1 + player.getRandom().nextInt(2)));
            level.setBlock(pos, state.setValue(FRUITING, false), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS,
                    .75F, .9F + player.getRandom().nextFloat() * .2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
