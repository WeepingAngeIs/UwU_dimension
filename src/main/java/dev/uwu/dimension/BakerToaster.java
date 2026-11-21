package dev.uwu.dimension;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

final class BakerToaster extends HorizontalDirectionalBlock implements EntityBlock {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String NAME = "baker_toaster";
    private static final MapCodec<BakerToaster> CODEC = simpleCodec(BakerToaster::new);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "uwudimension");
    private static final DeferredBlock<BakerToaster> BLOCK = BLOCKS.register(NAME, () -> new BakerToaster(
            BlockBehaviour.Properties.of().strength(1.5F).noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));
    private static final Supplier<BlockEntityType<ToasterEntity>> TYPE = ENTITIES.register(NAME,
            () -> BlockEntityType.Builder.of(ToasterEntity::new, BLOCK.get()).build(null));
    private static final Supplier<Item> BREAD = UwUDimension.ITEMS.registerSimpleItem("uwu_bread",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(10).saturationModifier(.4F).build()));
    private static final Supplier<SoundEvent> DING = UwUDimension.SOUNDS.register("toaster_ding",
            () -> SoundEvent.createVariableRangeEvent(UwUDimension.id("toaster_ding")));
    private static final VoxelShape NORTH_SHAPE = box(1.5, 0, 3, 15.62, 10, 13);
    private static final VoxelShape EAST_SHAPE = box(3, 0, 1.5, 13, 10, 15.62);
    private static final VoxelShape SOUTH_SHAPE = box(.38, 0, 3, 14.5, 10, 13);
    private static final VoxelShape WEST_SHAPE = box(3, 0, .38, 13, 10, 14.5);

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ENTITIES.register(bus);
    }

    private BakerToaster(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    static boolean placeGenerated(WorldGenLevel world, ChunkAccess chunk, BlockPos position, Direction facing) {
        if (!(world instanceof WorldGenRegion region) || !ReefLife.isReach(region.getLevel())
                || !(chunk instanceof ProtoChunk) || chunk instanceof ImposterProtoChunk
                || chunk.getPersistedStatus().getChunkType() != ChunkType.PROTOCHUNK
                || !region.getCenter().equals(new ChunkPos(position)) || !chunk.getPos().equals(region.getCenter())
                || !facing.getAxis().isHorizontal() || !chunk.getBlockState(position).isAir()
                || chunk.getBlockEntityNbt(position) != null || chunk.getBlockEntity(position) != null) return false;
        var level = region.getLevel();
        try {
            var token = ToastClaims.forServer(level.getServer()).issue(position);
            if (token.isEmpty()) return false;
            BlockState state = BLOCK.get().defaultBlockState().setValue(FACING, facing);
            var toaster = new ToasterEntity(position, state);
            toaster.token = token.get();
            var tag = toaster.saveWithFullMetadata(level.registryAccess());
            if (!region.setBlock(position, state, Block.UPDATE_CLIENTS)) return false;
            chunk.removeBlockEntity(position); // Replace the worldgen DUMMY tag.
            chunk.setBlockEntityNbt(tag);
            chunk.setUnsaved(true);
            return true;
        } catch (IOException failure) {
            LOG.warn("Could not reserve the generated baker toaster at {}", position, failure);
            return false;
        }
    }

    @Override protected MapCodec<BakerToaster> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ToasterEntity(pos, state); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof ToasterEntity toaster) toaster.checkNearby(level);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        // Snapshot rollback can restore the original BE. A committed replacement has no generated token.
        if (!state.is(next.getBlock()) && level instanceof ServerLevel server
                && !server.captureBlockSnapshots && !server.restoringBlockSnapshots
                && level.getBlockEntity(pos) instanceof ToasterEntity toaster) toaster.retire(server);
        super.onRemove(state, level, pos, next, moved);
    }

    private static final class ToasterEntity extends BlockEntity {
        private static final String TOKEN = "UwUGeneratedToast";
        private static final int INTERVAL = 10;
        private UUID token;
        private boolean verified;
        private boolean activating;

        ToasterEntity(BlockPos pos, BlockState state) { super(TYPE.get(), pos, state); }

        @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            token = tag.hasUUID(TOKEN) ? tag.getUUID(TOKEN) : null;
            verified = false;
            // Snapshot rollback can load saved data into a BE that is already attached.
            wakeIfBound();
        }

        @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (token != null) tag.putUUID(TOKEN, token);
        }

        @Override public void removeComponentsFromTag(CompoundTag tag) {
            super.removeComponentsFromTag(tag);
            tag.remove(TOKEN);
        }

        @Override public void onLoad() {
            super.onLoad();
            wakeIfBound();
        }

        private void wakeIfBound() {
            if (token != null && !isRemoved() && level instanceof ServerLevel server
                    && server.getServer().isSameThread() && ReefLife.isReach(server)) {
                schedule(server, 1 + Math.floorMod(worldPosition.hashCode(), INTERVAL));
            }
        }

        private void schedule(ServerLevel level, int delay) {
            level.scheduleTick(worldPosition, BLOCK.get(), delay);
        }

        private boolean current(ServerLevel level) {
            return !isRemoved() && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this;
        }

        private void disarm() {
            token = null;
            verified = false;
            setChanged();
        }

        void checkNearby(ServerLevel level) {
            if (token == null || activating || !ReefLife.isReach(level) || !current(level)) return;
            if (level.captureBlockSnapshots || level.restoringBlockSnapshots) {
                schedule(level, INTERVAL);
                return;
            }
            ToastClaims claims = ToastClaims.forServer(level.getServer());
            try {
                if (!verified && !claims.ready(worldPosition, token)) {
                    disarm();
                    return;
                }
                verified = true;
                Vec3 center = Vec3.atCenterOf(worldPosition);
                boolean nearby = !level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(4),
                        p -> p.isAlive() && !p.isFakePlayer() && !p.hasDisconnected()
                                && (p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL
                                    || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
                                && p.distanceToSqr(center) <= 16 && SakuraAccess.mayInteract(p, worldPosition)).isEmpty();
                if (token == null || !current(level)) return;
                if (!nearby) {
                    schedule(level, INTERVAL);
                    return;
                }
                if (!claims.take(worldPosition, token)) {
                    disarm();
                    return;
                }
                pop(level, claims, token);
            } catch (IOException failure) {
                LOG.warn("Could not verify baker toaster receipt at {}", worldPosition, failure);
                verified = false;
                if (token != null && current(level)) schedule(level, 200);
            }
        }

        private void pop(ServerLevel level, ToastClaims claims, UUID claimed) throws IOException {
            activating = true;
            try {
                Vec3 slot = Vec3.atLowerCornerOf(worldPosition).add(.5, .65, .5);
                var bread = new ItemEntity(level, slot.x, slot.y, slot.z, new ItemStack(BREAD.get()), 0, .22, 0);
                bread.setDefaultPickUpDelay();
                boolean accepted;
                try {
                    accepted = level.addFreshEntity(bread);
                } catch (RuntimeException uncertain) {
                    disarm();
                    LOG.warn("Bread spawn at {} ended without a confirmed outcome; retaining spent receipt", worldPosition, uncertain);
                    return;
                }
                if (accepted) {
                    disarm();
                    level.playSound(null, slot.x, slot.y, slot.z, DING.get(), SoundSource.BLOCKS, .55F, 1F);
                } else if (!bread.isRemoved() && !bread.isAddedToLevel() && level.getEntity(bread.getUUID()) == null
                        && current(level) && claimed.equals(token) && claims.retryRejectedSpawn(worldPosition, claimed)) {
                    schedule(level, 200);
                } else {
                    disarm();
                }
            } finally {
                activating = false;
            }
        }

        void retire(ServerLevel level) {
            UUID old = token;
            if (old == null || !ReefLife.isReach(level)) return;
            try {
                ToastClaims.forServer(level.getServer()).retire(worldPosition, old);
            } catch (IOException failure) {
                LOG.warn("Could not persist baker toaster removal at {}; position is disabled for this server session", worldPosition, failure);
            }
            token = null;
            verified = false;
        }
    }
}
