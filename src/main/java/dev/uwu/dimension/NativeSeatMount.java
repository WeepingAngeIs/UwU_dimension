package dev.uwu.dimension;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One invisible, server-created vehicle per occupied seat. Vanilla synchronizes the passenger relationship. */
public final class NativeSeatMount extends Entity {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "uwudimension");
    private static final TagKey<EntityType<?>> RIDERS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("uwudimension", "furniture_seat_riders"));
    @Nullable private UUID handler;
    public static final DeferredHolder<EntityType<?>, EntityType<NativeSeatMount>> TYPE =
            TYPES.register("furniture_seat", () -> EntityType.Builder.<NativeSeatMount>of(NativeSeatMount::new, MobCategory.MISC)
                    .sized(.125F, .125F).noSummon().clientTrackingRange(8).build("uwudimension:furniture_seat"));

    private NativeSeatMount(EntityType<? extends NativeSeatMount> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    static boolean sit(ServerLevel level, BlockPos pos, ServerPlayer player) {
        NativeSeatMount anchor = null;
        for (NativeSeatMount existing : at(level, pos)) {
            if (existing.isVehicle()) anchor = existing;
            else existing.discard();
        }
        if (anchor != null) {
            if (!(anchor.getFirstPassenger() instanceof Mob mob) || !player.getMainHandItem().isEmpty()
                    || !player.getUUID().equals(anchor.handler) || !ownerAllows(mob, player)
                    || anchor.exitFor(mob) == null) return false;
            anchor.release();
            return !anchor.isVehicle();
        }
        BlockState seat = level.getBlockState(pos);
        LivingEntity rider = player;
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(10),
                mob -> mob.getLeashHolder() == player && mob.isAlive() && !mob.isPassenger() && !mob.isVehicle()
                        && eligible(mob) && ownerAllows(mob, player) && SakuraAccess.mayInteract(player, mob.blockPosition()));
        if (!nearby.isEmpty()) rider = nearby.stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElseThrow();
        NativeSeatMount mount = new NativeSeatMount(TYPE.get(), level);
        mount.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        if (rider instanceof Mob) mount.handler = player.getUUID();
        // Refuse a new rider if every exit is already obstructed.
        if (mount.exitFor(rider) == null || !level.addFreshEntity(mount)) return false;
        if (mount.isRemoved() || level.getBlockState(pos) != seat || occupied(level, pos)
                || rider.isPassenger() || !rider.startRiding(mount)) {
            mount.discard();
            return false;
        }
        return true;
    }

    private static boolean eligible(Mob mob) {
        return mob.getType().is(RIDERS) && (!(mob instanceof TamableAnimal tame) || tame.isTame());
    }

    private static boolean ownerAllows(Mob mob, ServerPlayer player) {
        if (!(mob instanceof OwnableEntity ownable)) return true;
        UUID owner = ownable.getOwnerUUID();
        return owner == null || owner.equals(player.getUUID());
    }

    private static List<NativeSeatMount> at(Level level, BlockPos pos) {
        return level.getEntitiesOfClass(NativeSeatMount.class, new AABB(pos),
                mount -> !mount.isRemoved() && mount.blockPosition().equals(pos));
    }

    static boolean occupied(Level level, BlockPos pos) {
        return at(level, pos).stream().anyMatch(Entity::isVehicle);
    }

    static void releaseAt(Level level, BlockPos pos) {
        for (NativeSeatMount mount : at(level, pos)) mount.release();
    }

    private void release() {
        // Dismount while the vehicle still exists, so LivingEntity asks it to find a safe standing position.
        ejectPassengers();
        if (!isVehicle()) discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        BlockPos pos = blockPosition();
        BlockState state = level().getBlockState(pos);
        if (!isVehicle() || !(state.getBlock() instanceof FurnitureSeatBlock seat)
                || !seat.canSit(state) || !FurnitureSeatBlock.clearAbove(level(), pos)) release();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return (passenger instanceof ServerPlayer || passenger instanceof Mob mob && eligible(mob))
                && super.canAddPassenger(passenger);
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        BlockState state = level().getBlockState(blockPosition());
        float height = state.getBlock() instanceof FurnitureSeatBlock seat ? seat.seatHeight(state) : .4F;
        EntityType<?> type = passenger.getType();
        if (type == EntityType.CAT) height += 2F / 16F;
        if (type == EntityType.WOLF || type == EntityType.PARROT) height += 1F / 16F;
        return position().add(0, height, 0);
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide && passenger instanceof TamableAnimal pet) pet.setInSittingPose(true);
        notifyOccupancy();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && passenger instanceof TamableAnimal pet) pet.setInSittingPose(pet.isOrderedToSit());
        notifyOccupancy();
    }

    private void notifyOccupancy() {
        if (!level().isClientSide && level().hasChunkAt(blockPosition())) {
            BlockPos pos = blockPosition();
            level().updateNeighbourForOutputSignal(pos, level().getBlockState(pos).getBlock());
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 safe = exitFor(passenger);
        if (safe != null) return safe;
        // A player may have built over every original exit after the passenger sat down.
        BlockPos pos = blockPosition();
        for (int dy : new int[] {0, 1, -1}) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != 2) continue;
                    safe = safeAt(passenger, pos.offset(x, dy, z));
                    if (safe != null) return safe;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    private @Nullable Vec3 exitFor(LivingEntity passenger) {
        BlockPos pos = blockPosition();
        BlockState state = level().getBlockState(pos);
        Direction front = state.hasProperty(TeaChairBlock.FACING) ? state.getValue(TeaChairBlock.FACING) : passenger.getDirection();
        List<Direction> sides = List.of(front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite());
        for (int dy : new int[] {0, 1, -1}) {
            for (Direction side : sides) {
                BlockPos beside = pos.offset(0, dy, 0).relative(side);
                Vec3 safe = safeAt(passenger, beside);
                if (safe != null) return safe;
                safe = safeAt(passenger, beside.relative(side.getClockWise()));
                if (safe != null) return safe;
            }
            Vec3 safe = safeAt(passenger, pos.offset(0, dy, 0));
            if (safe != null) return safe;
        }
        return null;
    }

    private @Nullable Vec3 safeAt(LivingEntity passenger, BlockPos pos) {
        if (!level().getWorldBorder().isWithinBounds(pos)) return null;
        var standing = passenger.getDimensions(Pose.STANDING);
        double width = Math.max(standing.width(), passenger.getType().getWidth());
        int margin = (int)Math.ceil((width - 1) / 2);
        if (!level().hasChunksAt(pos.offset(-margin, 0, -margin), pos.offset(margin, 0, margin))) return null;
        Vec3 location = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), pos, true);
        if (location == null) return null;
        AABB bounds = standing.makeBoundingBox(location);
        return level().getWorldBorder().isWithinBounds(bounds) && level().noCollision(passenger, bounds) ? location : null;
    }

    // The anchor is its position. Minecraft saves occupied riders and restores their relationship on load.
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        handler = tag.hasUUID("Handler") ? tag.getUUID("Handler") : null;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (handler != null) tag.putUUID("Handler", handler);
    }
}
