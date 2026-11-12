package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/** Invisible, no-collision light that fades away a few ticks after an ability flash. */
final class HeartFlashLightBlock extends Block {
    static final IntegerProperty AGE = IntegerProperty.create("age", 0, 5);

    HeartFlashLightBlock() {
        super(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.NONE)
                .strength(0.0F).noCollission().noOcclusion().replaceable().lightLevel(state -> 15 - state.getValue(AGE) * 2));
        registerDefaultState(defaultBlockState().setValue(AGE, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return true; }

    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos).is(this)) return;
        if (state.getValue(AGE) >= 5) {
            level.removeBlock(pos, false);
            return;
        }
        BlockState next = state.setValue(AGE, state.getValue(AGE) + 1);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        level.scheduleTick(pos, this, 1);
    }

    @Override public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            RandomSource random) {
        if (random.nextInt(2) == 0) {
            level.addParticle(MagicGlowParticles.HEART.get(),
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
                    0, 0, 0);
        }
    }
}
