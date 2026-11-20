package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.registries.DeferredRegister;

final class SacredSpring {
    private static final DustParticleOptions[] SHIMMER = {
            new DustParticleOptions(new org.joml.Vector3f(1.0F,.68F,.83F),.55F),
            new DustParticleOptions(new org.joml.Vector3f(.88F,.78F,1.0F),.45F),
            new DustParticleOptions(new org.joml.Vector3f(.72F,1.0F,.88F),.45F),
            new DustParticleOptions(new org.joml.Vector3f(1.0F,.94F,.78F),.45F)
    };
    enum Overlay { FULL, REDUCED, OFF }
    static final ModConfigSpec CLIENT_SPEC;
    static final ModConfigSpec.EnumValue<Overlay> OVERLAY;
    static final ModConfigSpec.EnumValue<Overlay> WEATHER;
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    static final net.neoforged.neoforge.registries.DeferredBlock<Block> BED =
            BLOCKS.register("sacred_spring_bed", () -> new Block(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.SEA_LANTERN).lightLevel(state -> 6)) {
                @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
                    if (random.nextInt(8) == 0 && level.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER)
                            && level.getFluidState(pos.above(2)).isEmpty()) {
                        level.addParticle(SHIMMER[random.nextInt(SHIMMER.length)], pos.getX()+random.nextDouble(),
                                pos.getY()+1.97, pos.getZ()+random.nextDouble(), 0, 0.004, 0);
                    }
                }
            });
    static {
        var builder = new ModConfigSpec.Builder();
        OVERLAY = builder.comment("Sacred spring heart border: FULL, REDUCED, or OFF.").defineEnum("sacredSpringOverlay", Overlay.FULL);
        WEATHER = builder.comment("Sakura weather accents: FULL, REDUCED, or OFF.").defineEnum("weatherAccents", Overlay.FULL);
        CLIENT_SPEC = builder.build();
        ITEMS.registerSimpleBlockItem("sacred_spring_bed", BED);
    }
    private SacredSpring() {}
    static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); }
}

