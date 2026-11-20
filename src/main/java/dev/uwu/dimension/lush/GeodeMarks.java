package dev.uwu.dimension.lush;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Positions actually written by a themed geode. Unmarked positions always use the normal model. */
public final class GeodeMarks {
    public static final Codec<GeodeMarks> CODEC = Codec.INT_STREAM.xmap(
            entries -> decode(entries.toArray()), marks -> Arrays.stream(marks.encoded()));
    static final int PINK = 0;
    static final int SILVER = 1;
    private final Int2ByteOpenHashMap values = new Int2ByteOpenHashMap();

    private static int key(BlockPos pos) {
        return (pos.getY() << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
    }

    // Persisted role numbers. Avoid ordinal-dependent enums here.
    static int role(BlockState state) {
        if (state.is(Blocks.SMOOTH_BASALT)) return 1;
        if (state.is(Blocks.CALCITE)) return 2;
        if (state.is(Blocks.AMETHYST_BLOCK)) return 3;
        if (state.is(Blocks.BUDDING_AMETHYST)) return 4;
        if (state.is(Blocks.SMALL_AMETHYST_BUD)) return 5;
        if (state.is(Blocks.MEDIUM_AMETHYST_BUD)) return 6;
        if (state.is(Blocks.LARGE_AMETHYST_BUD)) return 7;
        if (state.is(Blocks.AMETHYST_CLUSTER)) return 8;
        return 0;
    }

    static int mark(int theme, int role) {
        return theme * 8 + role;
    }

    public synchronized boolean isEmpty() {
        return values.isEmpty();
    }

    synchronized int themeNumber(BlockPos pos, BlockState state) {
        int datum = values.get(key(pos));
        if (datum == 0 || (datum - 1) % 8 + 1 != role(state)) return -1;
        return (datum - 1) / 8;
    }

    public @Nullable String theme(BlockPos pos, BlockState state) {
        return switch (themeNumber(pos, state)) {
            case PINK -> "pink";
            case SILVER -> "silver";
            default -> null;
        };
    }

    synchronized boolean update(BlockPos pos, int datum) {
        int key = key(pos);
        return datum == 0 ? values.remove(key) != 0 : values.put(key, (byte) datum) != datum;
    }

    public synchronized int[] encoded() {
        int[] entries = new int[values.size()];
        int i = 0;
        for (var it = values.int2ByteEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            entries[i++] = (entry.getIntKey() << 5) | entry.getByteValue();
        }
        Arrays.sort(entries);
        return entries;
    }

    static GeodeMarks decode(int[] entries) {
        GeodeMarks marks = new GeodeMarks();
        for (int entry : entries) {
            int datum = entry & 31;
            if (datum >= 1 && datum <= 16) marks.values.put(entry >> 5, (byte) datum);
        }
        return marks;
    }
}
