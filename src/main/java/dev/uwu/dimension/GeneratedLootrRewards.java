package dev.uwu.dimension;

import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Optional adapter for the stock chest and on-disk ID format of one verified Lootr release. */
public final class GeneratedLootrRewards {
    private static final String LOOTR_VERSION = "1.21.1-1.11.38.125";
    private static final String API_CLASS = "noobanidus.mods.lootr.common.api.LootrAPI";
    private static final String CONFIG_CLASS = "noobanidus.mods.lootr.neoforge.config.ConfigManager";
    private static final String INFO_CLASS = "noobanidus.mods.lootr.common.api.data.ILootrInfo";
    private static final String CHEST_ENTITY_CLASS = "noobanidus.mods.lootr.common.block.entity.LootrChestBlockEntity";
    private static final String MANAGED_REWARD = "uwudimension:generated_lootr_reward";
    private static final String LOOTR_ID = "LootrId";
    static final ResourceLocation CHEST_ID = ResourceLocation.fromNamespaceAndPath("lootr", "lootr_chest");
    private static final TagKey<Structure> REFRESH_TAG = structureTag("refresh");
    private static final TagKey<Structure> DECAY_TAG = structureTag("decay");

    private GeneratedLootrRewards() {}

    /** Checks and initializes a detached chest before the caller reserves its backend. */
    static Optional<BlockEntity> prepare(ServerLevel level, Direction facing,
            NativeRewardAssignment assignment, ResourceKey<LootTable> table) {
        if (!compatible(level, table)) return Optional.empty();
        try {
            var candidate = state(facing);
            if (candidate.isEmpty() || !(candidate.get().getBlock() instanceof EntityBlock block)) return Optional.empty();
            if (!(block.newBlockEntity(assignment.site().placement(), candidate.get())
                    instanceof RandomizableContainerBlockEntity chest)
                    || !initialize(chest, assignment, table, level.registryAccess())) return Optional.empty();
            chest.getPersistentData().putBoolean("uwu_lore_natural_chest", true);
            return Optional.of(chest);
        } catch (LinkageError | RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    static boolean compatible(ServerLevel level, ResourceKey<LootTable> table) {
        try {
            var state = state(Direction.NORTH);
            if (state.isEmpty()) return false;
            Class<?> api = Class.forName(API_CLASS);
            Object config = Class.forName(CONFIG_CLASS).getField("COMMON_CONFIG").get(null);
            if (!(config instanceof ModConfigSpec spec) || !spec.isLoaded()) return false;
            if (level.getServer().reloadableRegistries().get().lookupOrThrow(Registries.LOOT_TABLE)
                    .get(table).isEmpty()) return false;

            ResourceKey<Level> dimension = level.dimension();
            String namespace = table.location().getNamespace();
            var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            // A generation worker must not make Lootr access structure references in surrounding chunks.
            // Any configured structure match anywhere therefore makes this backend ineligible.
            return !flag(api, "isDisabled") && !flag(api, "shouldDiscard") && !flag(api, "shouldDropPlayerLoot")
                    && !((Boolean) api.getMethod("isDimensionBlocked", ResourceKey.class).invoke(null, dimension))
                    && !((Boolean) api.getMethod("isLootTableBlacklisted", ResourceKey.class).invoke(null, table))
                    && !flag(api, "shouldRefreshAll") && !flag(api, "shouldDecayAll")
                    && !contains(api, "getRefreshWhitelist", table) && !contains(api, "getDecayWhitelist", table)
                    && !contains(api, "getRefreshModids", namespace) && !contains(api, "getModidDecayWhitelist", namespace)
                    && !contains(api, "getRefreshDimensions", dimension) && !contains(api, "getDecayDimensions", dimension)
                    && structures.getTag(REFRESH_TAG).map(HolderSet::size).orElse(0) == 0
                    && structures.getTag(DECAY_TAG).map(HolderSet::size).orElse(0) == 0
                    && lifecycleGuardWorks(api, state.get());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return false;
        }
    }

    private static Optional<BlockState> state(Direction facing) {
        try {
            if (!supportedRelease() || !facing.getAxis().isHorizontal()) return Optional.empty();
            var block = BuiltInRegistries.BLOCK.getOptional(CHEST_ID).orElse(null);
            if (!(block instanceof ChestBlock chest)) return Optional.empty();
            return Optional.of(chest.defaultBlockState().setValue(ChestBlock.FACING, facing)
                    .setValue(ChestBlock.TYPE, ChestType.SINGLE).setValue(ChestBlock.WATERLOGGED, false));
        } catch (LinkageError | RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    /** Seeds a newly constructed, detached chest. The caller discards it if validation fails. */
    private static boolean initialize(RandomizableContainerBlockEntity chest, NativeRewardAssignment assignment,
                              ResourceKey<LootTable> table, HolderLookup.Provider registries) {
        try {
            if (!supportedRelease() || !isStockChest(chest) || chest.getLevel() != null
                    || chest.getLootTable() != null || chest.getLootTableSeed() != 0L || hasMarker(chest)
                    || !assignment.site().placement().equals(chest.getBlockPos())
                    || !assignment.kind().table.equals(table.location())) return false;

            var id = assignment.site().id();
            var persistent = new CompoundTag();
            persistent.put(MANAGED_REWARD, assignment.save());

            var staged = new CompoundTag();
            staged.putUUID(LOOTR_ID, id);
            staged.putString(RandomizableContainer.LOOT_TABLE_TAG, table.location().toString());
            staged.putLong(RandomizableContainer.LOOT_TABLE_SEED_TAG, id.getLeastSignificantBits());
            staged.put("NeoForgeData", persistent);
            chest.loadWithComponents(staged, registries);

            var saved = chest.saveWithoutMetadata(registries);
            return saved.hasUUID(LOOTR_ID) && id.equals(saved.getUUID(LOOTR_ID)) && matches(chest, assignment, table);
        } catch (LinkageError | RuntimeException unavailable) {
            return false;
        }
    }

    static boolean hasMarker(BlockEntity chest) {
        return chest.getPersistentData().contains(MANAGED_REWARD);
    }

    /** Called by the optional Lootr mixin using its own provider, with no Lootr types in this method's descriptor. */
    public static boolean suppressAutomaticReset(Object provider) {
        return provider instanceof BlockEntity chest && isStockChest(chest) && hasMarker(chest);
    }

    static Optional<NativeRewardAssignment> assignment(BlockEntity chest) {
        var persistent = chest.getPersistentData();
        if (!persistent.contains(MANAGED_REWARD, Tag.TAG_COMPOUND)) return Optional.empty();
        return NativeRewardAssignment.load(persistent.getCompound(MANAGED_REWARD));
    }

    static boolean matches(BlockEntity chest, NativeRewardAssignment assignment, ResourceKey<LootTable> table) {
        try {
            if (!supportedRelease() || !isStockChest(chest)
                    || !assignment.site().placement().equals(chest.getBlockPos())
                    || !assignment.kind().table.equals(table.location())
                    || GeneratedLootrRewards.assignment(chest).filter(assignment::equals).isEmpty()) return false;

            Class<?> api = Class.forName(API_CLASS);
            Class<?> info = Class.forName(INFO_CLASS);
            Object provider = resolve(api, chest);
            if (!info.isInstance(provider)) return false;
            var id = assignment.site().id();
            return lifecycleFlags(info, provider, false)
                    && id.equals(info.getMethod("getInfoUUID").invoke(provider))
                    && table.equals(info.getMethod("getInfoLootTable").invoke(provider))
                    && id.getLeastSignificantBits() == (Long) info.getMethod("getInfoLootSeed").invoke(provider);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return false;
        }
    }

    private static boolean supportedRelease() {
        return ModList.get().getModContainerById("lootr")
                .map(container -> LOOTR_VERSION.equals(container.getModInfo().getVersion().toString())).orElse(false);
    }

    private static boolean isStockChest(BlockEntity chest) {
        return CHEST_ENTITY_CLASS.equals(chest.getClass().getName())
                && CHEST_ID.equals(BuiltInRegistries.BLOCK.getKey(chest.getBlockState().getBlock()));
    }

    private static boolean lifecycleGuardWorks(Class<?> api, BlockState state) throws ReflectiveOperationException {
        BlockEntity chest = ((ChestBlock) state.getBlock()).newBlockEntity(BlockPos.ZERO, state);
        Class<?> info = Class.forName(INFO_CLASS);
        Object provider = resolve(api, chest);
        if (!info.isInstance(provider) || !lifecycleFlags(info, provider, true)) return false;
        chest.getPersistentData().putBoolean(MANAGED_REWARD, true);
        return lifecycleFlags(info, provider, false);
    }

    private static Object resolve(Class<?> api, BlockEntity chest) throws ReflectiveOperationException {
        return api.getMethod("resolveBlockEntity", BlockEntity.class).invoke(null, chest);
    }

    private static boolean lifecycleFlags(Class<?> info, Object provider, boolean enabled)
            throws ReflectiveOperationException {
        return (Boolean) info.getMethod("canRefresh").invoke(provider) == enabled
                && (Boolean) info.getMethod("canDecay").invoke(provider) == enabled;
    }

    private static boolean flag(Class<?> api, String name) throws ReflectiveOperationException {
        return (Boolean) api.getMethod(name).invoke(null);
    }

    private static boolean contains(Class<?> api, String name, Object value) throws ReflectiveOperationException {
        return ((Set<?>) api.getMethod(name).invoke(null)).contains(value);
    }

    private static TagKey<Structure> structureTag(String name) {
        return TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("lootr", name));
    }
}
