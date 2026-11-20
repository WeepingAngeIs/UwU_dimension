package dev.uwu.dimension;

import com.mojang.serialization.MapCodec;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Air-only light sources with absolute cast times, including when an area unloads. */
final class RevivalLotusTerrainLight {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "uwudimension");
    private static final String ID = "revival_lotus_light";
    private static final java.util.function.Supplier<SourceBlock> LIGHT = BLOCKS.register(ID, SourceBlock::new);
    private static final java.util.function.Supplier<BlockEntityType<Source>> SOURCE = ENTITIES.register(ID,
            () -> BlockEntityType.Builder.of(Source::new, LIGHT.get()).build(null));
    // Whole-tick samples of Astra's lighting.json. The remaining frames emit no block light.
    private static final int[] LEVELS = {1, 4, 10, 13, 13, 12, 12, 11, 10, 9, 8, 7, 5, 4, 3, 2, 1, 1};
    private static final int UPDATE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Map<ServerLevel, Set<BlockPos>> LOADED = new IdentityHashMap<>();

    private RevivalLotusTerrainLight() {}

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ENTITIES.register(bus);
        NeoForge.EVENT_BUS.addListener(RevivalLotusTerrainLight::chunkLoaded);
        NeoForge.EVENT_BUS.addListener(RevivalLotusTerrainLight::chunkUnloaded);
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> LOADED.remove(event.getLevel()));
        NeoForge.EVENT_BUS.addListener(RevivalLotusTerrainLight::tick);
    }

    static void illuminate(ServerLevel level, Vec3 feet, long start) {
        BlockPos origin = BlockPos.containing(feet);
        if (startAt(level, origin, start) || startAt(level, origin.above(), start)) return;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (startAt(level, origin.relative(side), start)) return;
        }
    }

    private static boolean startAt(ServerLevel level, BlockPos at, long start) {
        if (!level.isInWorldBounds(at) || !level.getWorldBorder().isWithinBounds(at)) return false;
        LevelChunk chunk = chunkAt(level, at);
        if (chunk == null) return false;
        BlockState state = chunk.getBlockState(at);
        boolean cave = state.is(Blocks.CAVE_AIR);
        if (!state.is(LIGHT.get()) && !state.is(Blocks.AIR) && !cave) return false;
        if (!state.is(LIGHT.get()) && !level.setBlock(at, LIGHT.get().defaultBlockState(), UPDATE)) return false;
        if (!(chunk.getBlockEntity(at) instanceof Source source)) return false;
        if (!state.is(LIGHT.get())) source.caveAir = cave;
        source.starts.add(start);
        chunk.setUnsaved(true);
        LOADED.computeIfAbsent(level, unused -> new HashSet<>()).add(at);
        update(level, chunk, at);
        return true;
    }

    private static LevelChunk chunkAt(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        // Load callbacks only record locations. The next server-level tick cleans expired sources.
        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            if (chunk.getBlockState(pos).is(LIGHT.get())) {
                LOADED.computeIfAbsent(level, unused -> new HashSet<>()).add(pos.immutable());
            }
        }
    }

    private static void chunkUnloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Set<BlockPos> positions = LOADED.get(level);
        if (positions == null) return;
        var chunk = event.getChunk().getPos();
        positions.removeIf(pos -> (pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z);
    }

    private static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Set<BlockPos> positions = LOADED.get(level);
        if (positions == null) return;
        positions.removeIf(pos -> {
            LevelChunk chunk = chunkAt(level, pos);
            return chunk == null || !update(level, chunk, pos);
        });
        if (positions.isEmpty()) LOADED.remove(level);
    }

    private static boolean update(ServerLevel level, LevelChunk chunk, BlockPos at) {
        BlockState state = chunk.getBlockState(at);
        if (!state.is(LIGHT.get())) return false; // A replacement belongs to the player or the world.
        if (!(chunk.getBlockEntity(at) instanceof Source source)) {
            level.setBlock(at, Blocks.AIR.defaultBlockState(), UPDATE);
            return false;
        }
        long time = level.getGameTime();
        if (source.starts.removeIf(start -> time < start || time - start >= LEVELS.length)) chunk.setUnsaved(true);
        if (source.starts.isEmpty()) {
            level.setBlock(at, (source.caveAir ? Blocks.CAVE_AIR : Blocks.AIR).defaultBlockState(), UPDATE);
            return false;
        }
        int brightness = 0;
        for (long start : source.starts) brightness = Math.max(brightness, LEVELS[(int) (time - start)]);
        if (state.getValue(BlockStateProperties.LEVEL) != brightness) {
            level.setBlock(at, state.setValue(BlockStateProperties.LEVEL, brightness), UPDATE);
        }
        return true;
    }

    private static final class SourceBlock extends BaseEntityBlock {
        private static final MapCodec<SourceBlock> CODEC = simpleCodec(SourceBlock::new);

        SourceBlock() {
            this(BlockBehaviour.Properties.of().noCollission().noOcclusion().replaceable().noLootTable()
                    .lightLevel(state -> state.getValue(BlockStateProperties.LEVEL)));
        }

        SourceBlock(Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(BlockStateProperties.LEVEL, 0));
        }

        @Override protected MapCodec<SourceBlock> codec() { return CODEC; }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.LEVEL);
        }

        @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos at,
                CollisionContext context) {
            return Shapes.empty();
        }

        @Override public BlockEntity newBlockEntity(BlockPos at, BlockState state) {
            return new Source(at, state);
        }
    }

    private static final class Source extends BlockEntity {
        private final Set<Long> starts = new HashSet<>();
        private boolean caveAir;

        Source(BlockPos at, BlockState state) { super(SOURCE.get(), at, state); }

        @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putLongArray("Starts", starts.stream().mapToLong(Long::longValue).sorted().toArray());
            tag.putBoolean("CaveAir", caveAir);
        }

        @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            starts.clear();
            for (long start : tag.getLongArray("Starts")) starts.add(start);
            caveAir = tag.getBoolean("CaveAir");
        }
    }
}
