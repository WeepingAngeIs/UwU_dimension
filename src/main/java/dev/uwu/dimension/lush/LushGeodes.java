package dev.uwu.dimension.lush;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.lush.client.GeodeClientIndex;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

/** Observes native amethyst placement and growth. The original operations still own every block write. */
@EventBusSubscriber(modid = "uwudimension")
public final class LushGeodes {
    private static final ResourceKey<Biome> GROTTO = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("uwudimension", "lantern_grotto"));
    private static final ResourceLocation VANILLA_GEODE = ResourceLocation.withDefaultNamespace("amethyst_geode");
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "uwudimension");
    private static final Supplier<AttachmentType<GeodeMarks>> MARKS =
            TYPES.register("lush_geode_marks", () -> AttachmentType.builder(GeodeMarks::new)
                    .serialize(GeodeMarks.CODEC, marks -> !marks.isEmpty()).sync(new Sync()).build());

    private record Chamber(WorldGenLevel level, int theme) {}
    private record Write(ChunkAccess owner, long pos, BlockState state, int theme) {}
    private static final ThreadLocal<Chamber> CHAMBER = new ThreadLocal<>();
    private static final ThreadLocal<Write> EXPECTED = new ThreadLocal<>();
    private static final Set<LevelChunk> DIRTY = ConcurrentHashMap.newKeySet();

    private LushGeodes() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    public static boolean configured(ConfiguredFeature<?, ?> feature, WorldGenLevel level, ChunkGenerator generator,
                                     RandomSource random, BlockPos pos, Operation<Boolean> original) {
        Chamber previous = CHAMBER.get();
        if (feature.feature() != Feature.GEODE && previous == null) return original.call(level, generator, random, pos);
        Chamber chamber = null;
        if (feature.feature() == Feature.GEODE && level instanceof WorldGenRegion
                && level.getLevel().dimension().equals(ReefLife.REACH)
                && VANILLA_GEODE.equals(level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getKey(feature))) {
            var originBiome = level.getBiome(pos);
            if (originBiome.is(GROTTO) || originBiome.is(Biomes.LUSH_CAVES)) {
                // One theme per origin. Never advance Minecraft's worldgen random sequence.
                long bits = RandomSupport.mixStafford13(level.getSeed() ^ pos.asLong());
                chamber = new Chamber(level, (bits & 1) == 0 ? GeodeMarks.PINK : GeodeMarks.SILVER);
            }
        }
        if (chamber == null && previous == null) return original.call(level, generator, random, pos);
        if (chamber == null) CHAMBER.remove(); else CHAMBER.set(chamber);
        try {
            return original.call(level, generator, random, pos);
        } finally {
            if (previous == null) CHAMBER.remove(); else CHAMBER.set(previous);
        }
    }

    public static boolean writeFeature(Feature<?> feature, WorldGenLevel level, BlockPos pos, BlockState state,
                                       int flags, Operation<Boolean> original) {
        Chamber chamber = CHAMBER.get();
        if (feature != Feature.GEODE || chamber == null || chamber.level != level || GeodeMarks.role(state) == 0) {
            return original.call(level, pos, state, flags);
        }
        // safeSetBlock has already read this position from the same WorldGenRegion cache.
        ChunkAccess owner = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (owner instanceof ImposterProtoChunk wrapper) owner = wrapper.getWrapped();
        Write before = EXPECTED.get();
        EXPECTED.set(new Write(owner, pos.asLong(), state, chamber.theme));
        try {
            return original.call(level, pos, state, flags);
        } finally {
            restore(before);
        }
    }

    public static boolean grow(ServerLevel level, BlockPos target, BlockState next, Operation<Boolean> original,
                               BlockPos support) {
        if (!level.dimension().equals(ReefLife.REACH)) return original.call(level, target, next);
        LevelChunk source = level.getChunkSource().getChunkNow(support.getX() >> 4, support.getZ() >> 4);
        if (source == null) return original.call(level, target, next);
        GeodeMarks marks = source.getExistingDataOrNull(MARKS.get());
        BlockState supportState = source.getBlockState(support);
        int theme = marks == null ? -1 : marks.themeNumber(support, supportState);
        if (theme < 0 || !supportState.is(Blocks.BUDDING_AMETHYST)) return original.call(level, target, next);
        // Preserve a player-placed, unmarked bud even if a native budding block later advances it.
        LevelChunk owner = level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4);
        if (owner == null) return original.call(level, target, next);
        if (!next.is(Blocks.SMALL_AMETHYST_BUD)) {
            GeodeMarks old = owner.getExistingDataOrNull(MARKS.get());
            if (old == null || old.themeNumber(target, owner.getBlockState(target)) != theme) {
                return original.call(level, target, next);
            }
        }
        Write before = EXPECTED.get();
        EXPECTED.set(new Write(owner, target.asLong(), next, theme));
        try {
            return original.call(level, target, next);
        } finally {
            restore(before);
        }
    }

    private static void restore(@Nullable Write before) {
        if (before == null) EXPECTED.remove(); else EXPECTED.set(before);
    }

    /** Called after the actual chunk setter returns a prior state, including writes from other features. */
    public static void afterWrite(ChunkAccess chunk, BlockPos pos, BlockState state, @Nullable BlockState old) {
        if (old == null || old == state) return;
        if (old.getBlock() == state.getBlock()
                && (!state.hasProperty(BlockStateProperties.FACING)
                || state.getValue(BlockStateProperties.FACING) == old.getValue(BlockStateProperties.FACING))) return;
        LevelChunk full = chunk instanceof LevelChunk levelChunk ? levelChunk : null;
        if (full != null && (full.getLevel().isClientSide() || !full.getLevel().dimension().equals(ReefLife.REACH))) return;
        Write expected = EXPECTED.get();
        if (expected == null && !chunk.hasAttachments()) return;
        int datum = 0;
        if (expected != null && expected.owner == chunk && expected.pos == pos.asLong()
                && expected.state == state && chunk.getBlockState(pos) == state) {
            datum = GeodeMarks.mark(expected.theme, GeodeMarks.role(state));
        } else if (GeodeMarks.role(old) == 0 && GeodeMarks.role(state) == 0) {
            return;
        }
        updateMark(chunk, pos, datum);
    }

    private static void updateMark(ChunkAccess chunk, BlockPos pos, int datum) {
        synchronized (chunk) {
            GeodeMarks marks = chunk.getExistingDataOrNull(MARKS.get());
            if (marks == null && datum == 0) return;
            if (marks == null) marks = chunk.getData(MARKS.get());
            if (marks.update(pos, datum)) {
                chunk.setUnsaved(true);
                if (chunk instanceof LevelChunk full) DIRTY.add(full);
            }
        }
    }

    /** Implemented by native snapshots so the canceled item-use path can restore their original provenance. */
    public interface RollbackSnapshot {
        void uwu$restoreGeodeMark();
    }

    public record SavedMark(WeakReference<LevelChunk> owner, int datum) {
        public void restore(BlockSnapshot snapshot) {
            LevelChunk chunk = owner.get();
            BlockPos pos = snapshot.getPos();
            if (chunk == null || !(snapshot.getLevel() instanceof ServerLevel level) || chunk.getLevel() != level
                    || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != chunk
                    || chunk.getBlockState(pos) != snapshot.getState()) return;
            updateMark(chunk, pos, datum);
        }
    }

    public static @Nullable SavedMark captureMark(@Nullable LevelAccessor source, BlockPos pos, BlockState state) {
        if (!(source instanceof ServerLevel level) || !level.captureBlockSnapshots
                || !level.dimension().equals(ReefLife.REACH)) return null;
        int role = GeodeMarks.role(state);
        if (role == 0) return null;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        GeodeMarks marks = chunk.getExistingDataOrNull(MARKS.get());
        int theme = marks == null ? -1 : marks.themeNumber(pos, state);
        return new SavedMark(new WeakReference<>(chunk), theme < 0 ? 0 : GeodeMarks.mark(theme, role));
    }

    /** NeoForge also auto-syncs the first getData. A generation worker cannot enumerate chunk watchers. */
    public static boolean deferSync(LevelChunk chunk, AttachmentType<?> type) {
        if (type != MARKS.get() || !(chunk.getLevel() instanceof ServerLevel level) || level.getServer().isSameThread()) return false;
        DIRTY.add(chunk);
        return true;
    }

    @SubscribeEvent
    public static void flush(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(ReefLife.REACH) || DIRTY.isEmpty()) return;
        for (LevelChunk chunk : DIRTY) {
            if (chunk.getLevel() != level || !DIRTY.remove(chunk)) continue;
            if (level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) == chunk) chunk.syncData(MARKS.get());
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) DIRTY.removeIf(chunk -> chunk.getLevel() == level);
    }

    private static final class Sync implements AttachmentSyncHandler<GeodeMarks> {
        @Override
        public void write(RegistryFriendlyByteBuf buf, GeodeMarks marks, boolean initial) {
            buf.writeVarIntArray(marks.encoded());
        }

        @Override
        public GeodeMarks read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable GeodeMarks previous) {
            int capacity = holder instanceof LevelChunk owner ? owner.getHeight() * 256 : 0;
            GeodeMarks marks = GeodeMarks.decode(buf.readVarIntArray(Math.min(capacity, buf.readableBytes())));
            if (holder instanceof LevelChunk chunk && chunk.getLevel().isClientSide()) GeodeClientIndex.accept(chunk, marks);
            return marks;
        }
    }
}
