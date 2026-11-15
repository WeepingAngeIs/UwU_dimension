package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.registries.DeferredRegister;

final class SacredSpring {
    enum Overlay { FULL, REDUCED, OFF }
    static final ModConfigSpec CLIENT_SPEC;
    static final ModConfigSpec.EnumValue<Overlay> OVERLAY;
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    static final net.neoforged.neoforge.registries.DeferredBlock<Block> BED =
            BLOCKS.register("sacred_spring_bed", () -> new Block(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.SEA_LANTERN).lightLevel(state -> 6)) {
                @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
                    if (random.nextInt(120) == 0 && level.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER)) {
                        level.addParticle(ParticleTypes.END_ROD, pos.getX()+random.nextDouble(),
                                pos.getY()+1.08, pos.getZ()+random.nextDouble(), 0, 0.015, 0);
                    }
                }
            });
    static {
        var builder = new ModConfigSpec.Builder();
        OVERLAY = builder.comment("Sacred spring heart border: FULL, REDUCED, or OFF.").defineEnum("sacredSpringOverlay", Overlay.FULL);
        CLIENT_SPEC = builder.build();
        ITEMS.registerSimpleBlockItem("sacred_spring_bed", BED);
    }
    private SacredSpring() {}
    static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); }
}

