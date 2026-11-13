package dev.uwu.dimension;

import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** A point-first thrown weapon with an accelerated Loyalty-style return flight. */
public final class RelicFlight extends ThrowableItemProjectile {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> KIND =
            net.minecraft.network.syncher.SynchedEntityData.defineId(RelicFlight.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> ELEMENT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(RelicFlight.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> WAVE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(RelicFlight.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> HARE_ORB =
            net.minecraft.network.syncher.SynchedEntityData.defineId(RelicFlight.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> LANDED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(RelicFlight.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> RETURNING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(RelicFlight.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> COUNTER =
            net.minecraft.network.syncher.SynchedEntityData.defineId(RelicFlight.class,
                    net.minecraft.network.syncher.EntityDataSerializers.STRING);

    private boolean physicalWeapon;
    private UUID physicalOwner;
    private InteractionHand returnHand = InteractionHand.MAIN_HAND;
    private int landedTicks;

    public RelicFlight(net.minecraft.world.entity.EntityType<? extends RelicFlight> type, Level level) {
        super(type, level);
    }

    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COUNTER, "");
        builder.define(KIND, 0);
        builder.define(ELEMENT, 0);
        builder.define(WAVE, false);
        builder.define(HARE_ORB, false);
        builder.define(LANDED, false);
        builder.define(RETURNING, false);
    }

    @Override protected net.minecraft.world.item.Item getDefaultItem() {
        return Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get();
    }

    public Arsenal.Relic relic() { return Arsenal.Relic.values()[entityData.get(KIND)]; }
    public int element() { return entityData.get(ELEMENT); }
    public boolean wave() { return entityData.get(WAVE); }
    public boolean hareOrb() { return entityData.get(HARE_ORB); }
    public boolean landed() { return entityData.get(LANDED); }
    public boolean returning() { return entityData.get(RETURNING); }

    /** Legacy visual cast used for the Glitch Bow and scythe's detached wave. */
    static void cast(ServerLevel level, Player owner, Arsenal.Relic kind, boolean wave) {
        ItemStack display = new ItemStack(Arsenal.WEAPONS.get(kind).get());
        launch(level, owner, kind, wave, "", display, 0, false, InteractionHand.MAIN_HAND,
                kind == Arsenal.Relic.VESPER_SCYTHE && !wave);
    }

    /** Throws the held weapon itself; it is removed from the hand until caught. */
    static boolean castWeapon(ServerLevel level, Player owner, Arsenal.Relic kind, InteractionHand hand) {
        ItemStack held = owner.getItemInHand(hand);
        if (held.isEmpty() || held.getItem() != Arsenal.WEAPONS.get(kind).get()) return false;
        ItemStack carried = held.copy();
        carried.hurtAndBreak(1, owner, LivingEntity.getSlotForHand(hand));
        if (carried.isEmpty()) {
            owner.setItemInHand(hand, ItemStack.EMPTY);
            return true;
        }
        return launch(level, owner, kind, false, "", carried, 0, true, hand,
                kind == Arsenal.Relic.VESPER_SCYTHE) != null;
    }

    static void castCounter(ServerLevel level, Player player, CounterArsenal.Entry entry, boolean wave) {
        ItemStack display = new ItemStack(CounterArsenal.ITEMS.get(entry.id()).get());
        launch(level, player, Arsenal.Relic.VESPER_SCYTHE, wave, entry.id(), display,
                entry.theme().equals("dusk") ? 2 : 0, false, InteractionHand.MAIN_HAND, !wave);
    }

    static boolean castCounterWeapon(ServerLevel level, Player player, CounterArsenal.Entry entry,
            InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || held.getItem() != CounterArsenal.ITEMS.get(entry.id()).get()) return false;
        ItemStack carried = held.copy();
        carried.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        if (carried.isEmpty()) {
            player.setItemInHand(hand, ItemStack.EMPTY);
            return true;
        }
        boolean cycleScythe = entry.shape().equals("scythe");
        return launch(level, player, Arsenal.Relic.VESPER_SCYTHE, false, entry.id(), carried,
                entry.theme().equals("dusk") ? 2 : 0, true, hand, cycleScythe) != null;
    }

    private static RelicFlight launch(ServerLevel level, Player owner, Arsenal.Relic kind, boolean wave,
            String counter, ItemStack display, int fallbackElement, boolean physical, InteractionHand hand,
            boolean cycleScythe) {
        RelicFlight flight = RangedRelics.FLIGHT.get().create(level);
        if (flight == null) return null;
        flight.entityData.set(KIND, kind.ordinal());
        flight.entityData.set(COUNTER, counter);
        flight.entityData.set(WAVE, wave);
        int element = fallbackElement;
        if (cycleScythe) {
            element = Math.floorMod(owner.getPersistentData().getInt("uwu.scythe_element") + (wave ? 0 : 1), 4);
            if (!wave) {
                owner.getPersistentData().putInt("uwu.scythe_element", element);
                owner.getPersistentData().putLong("uwu.scythe_flight", level.getGameTime() + 60);
            }
        }
        flight.entityData.set(ELEMENT, element);
        flight.entityData.set(HARE_ORB, kind == Arsenal.Relic.RIBBONHARE_SCEPTER);
        if (kind == Arsenal.Relic.VESPER_SCYTHE || !counter.isEmpty()) {
            final int selectedElement = element;
            net.minecraft.world.item.component.CustomData.update(
                    net.minecraft.core.component.DataComponents.CUSTOM_DATA, display,
                    tag -> tag.putInt("uwu_element", selectedElement));
        }
        flight.setItem(display);
        flight.setOwner(owner);
        flight.setPos(owner.getX(), owner.getEyeY() - .15, owner.getZ());
        // Match a thrown trident's real flight arc. The rapid Loyalty return has its
        // own no-gravity phase after impact or timeout.
        flight.setNoGravity(wave);
        flight.physicalWeapon = physical;
        flight.physicalOwner = physical ? owner.getUUID() : null;
        flight.returnHand = hand;
        flight.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0, wave ? 1.5F : physical ? 2.05F : 1.8F, 0);
        if (!level.addFreshEntity(flight)) return null;
        if (physical) {
            owner.setItemInHand(hand, ItemStack.EMPTY);
            HeartCombatGlow.trail(level, flight.position(), element);
        }
        return flight;
    }

    private Player findOwner(ServerLevel server) {
        if (getOwner() instanceof Player player) return player;
        return physicalOwner == null ? null : server.getServer().getPlayerList().getPlayer(physicalOwner);
    }

    private void beginReturn() {
        entityData.set(LANDED, false);
        entityData.set(RETURNING, true);
        setNoGravity(true);
        noPhysics = true;
        setDeltaMovement(Vec3.ZERO);
    }

    private void landOnBlock(ServerLevel server, BlockHitResult hit) {
        entityData.set(LANDED, true);
        landedTicks = 0;
        setNoGravity(true);
        noPhysics = true;
        setDeltaMovement(Vec3.ZERO);
        var face = hit.getDirection();
        setPos(hit.getLocation().x + face.getStepX() * .16,
                hit.getLocation().y + face.getStepY() * .16,
                hit.getLocation().z + face.getStepZ() * .16);
        server.playSound(null, hit.getBlockPos(), SoundEvents.TRIDENT_HIT_GROUND.value(), SoundSource.PLAYERS,
                .85F, .92F + server.random.nextFloat() * .16F);
    }

    private void returnTo(Player player) {
        ItemStack carried = getItem().copy();
        if (physicalWeapon && !carried.isEmpty()) {
            ItemStack inHand = player.getItemInHand(returnHand);
            if (inHand.isEmpty()) player.setItemInHand(returnHand, carried);
            else {
                player.getInventory().add(carried);
                if (!carried.isEmpty()) player.drop(carried, false);
            }
        }
        if (relic() == Arsenal.Relic.VESPER_SCYTHE)
            player.getPersistentData().remove("uwu.scythe_flight");
        discard();
    }

    @Override public void tick() {
        ServerLevel server = level() instanceof ServerLevel s ? s : null;
        if (server == null && (landed() || returning())) {
            setNoGravity(true);
            noPhysics = true;
            setDeltaMovement(Vec3.ZERO);
        }
        if (server != null && landed() && !returning()) {
            landedTicks++;
            setNoGravity(true);
            noPhysics = true;
            setDeltaMovement(Vec3.ZERO);
            if (landedTicks >= 16) beginReturn();
        }
        if (returning() && server != null) {
            Player owner = findOwner(server);
            if (owner == null || !owner.isAlive()) {
                // Keep the item attached to its projectile until its owner returns;
                // Loyalty must not silently delete a weapon during logout or death.
                setNoGravity(true);
                noPhysics = true;
                setDeltaMovement(Vec3.ZERO);
                if (!physicalWeapon && tickCount > 80) {
                    discard();
                    return;
                }
            } else {
                if (owner.level() != level()) {
                    returnTo(owner);
                    return;
                }
                Vec3 delta = owner.getEyePosition().subtract(position());
                if (delta.lengthSqr() < 5.3) {
                    returnTo(owner);
                    return;
                }
                double distance = delta.length();
                double speed = Math.min(3.6, Math.max(2.2, distance * .48));
                noPhysics = true;
                setNoGravity(true);
                setDeltaMovement(delta.scale(speed / distance));
            }
        }

        super.tick();
        if (server == null || !isAlive()) return;

        if (physicalWeapon && !returning() && tickCount >= 120) beginReturn();
        else if (!physicalWeapon && !wave() && relic() == Arsenal.Relic.VESPER_SCYTHE && tickCount >= 12)
            beginReturn();
        HeartCombatGlow.trail(server, position(), element());
        if (tickCount > 240 && !physicalWeapon) discard();
    }

    @Override protected boolean canHitEntity(Entity target) {
        return !returning() && super.canHitEntity(target)
                && (!(getOwner() instanceof Player owner) || !(target instanceof LivingEntity living)
                        || RangedRelics.enemy(owner, living));
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        if (returning()) return;
        if (level() instanceof ServerLevel server && getOwner() instanceof Player player
                && hit.getEntity() instanceof LivingEntity target) {
            if (!RangedRelics.enemy(player, target)) {
                finish();
                return;
            }
            if (counter().isEmpty()) RangedRelics.strike(server, player, target, relic());
            else CounterEffects.hit(server, player, target, CounterArsenal.entry(counter()));
            if (relic() == Arsenal.Relic.RIBBONHARE_SCEPTER)
                summonHare(server, player, target.blockPosition());
            if (relic() == Arsenal.Relic.VESPER_SCYTHE) {
                switch (element()) {
                    case 1 -> target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                    case 2 -> target.igniteForSeconds(3);
                    case 3 -> { target.setDeltaMovement(target.getDeltaMovement().add(0, .45, 0)); target.hurtMarked = true; }
                    default -> player.heal(1);
                }
            }
            HeartCombatGlow.burst(server, target.position().add(0, target.getBbHeight() * .55, 0), 1.25, element());
        }
        finish();
    }

    @Override protected void onHitBlock(BlockHitResult hit) {
        if (level() instanceof ServerLevel server)
            HeartCombatGlow.burst(server, hit.getLocation(), .9, element());
        if (relic() == Arsenal.Relic.RIBBONHARE_SCEPTER && level() instanceof ServerLevel server
                && getOwner() instanceof Player player)
            summonHare(server, player, hit.getBlockPos().relative(hit.getDirection()));
        if (physicalWeapon && level() instanceof ServerLevel server) {
            landOnBlock(server, hit);
            return;
        }
        finish();
    }

    private void summonHare(ServerLevel server, Player player, net.minecraft.core.BlockPos pos) {
        Rabbit hare = Arsenal.HARE.get().create(server);
        if (hare == null) return;
        hare.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        if (server.noCollision(hare) && server.getBlockState(pos.below()).isFaceSturdy(server, pos.below(),
                net.minecraft.core.Direction.UP)) HeartMagic.callHare(server, player, hare);
    }

    private void finish() {
        if (physicalWeapon || (!wave() && relic() == Arsenal.Relic.VESPER_SCYTHE)) beginReturn();
        else discard();
    }

    private String counter() { return entityData.get(COUNTER); }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("counter", counter());
        tag.putInt("relic", entityData.get(KIND));
        tag.putInt("element", element());
        tag.putBoolean("wave", wave());
        tag.putBoolean("hare_orb", hareOrb());
        tag.putBoolean("landed", landed());
        tag.putInt("landed_ticks", landedTicks);
        tag.putBoolean("returning", returning());
        tag.putBoolean("physical_weapon", physicalWeapon);
        tag.putInt("return_hand", returnHand == InteractionHand.OFF_HAND ? 1 : 0);
        if (physicalOwner != null) tag.putUUID("physical_owner", physicalOwner);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(COUNTER, tag.getString("counter"));
        entityData.set(KIND, Math.max(0, Math.min(Arsenal.Relic.values().length - 1, tag.getInt("relic"))));
        entityData.set(ELEMENT, Math.floorMod(tag.getInt("element"), 4));
        entityData.set(WAVE, tag.getBoolean("wave"));
        entityData.set(HARE_ORB, tag.getBoolean("hare_orb"));
        entityData.set(LANDED, tag.getBoolean("landed"));
        landedTicks = Math.max(0, Math.min(15, tag.getInt("landed_ticks")));
        entityData.set(RETURNING, tag.getBoolean("returning"));
        physicalWeapon = tag.getBoolean("physical_weapon");
        returnHand = tag.getInt("return_hand") == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        physicalOwner = tag.hasUUID("physical_owner") ? tag.getUUID("physical_owner") : null;
        if (returning() || landed()) {
            setNoGravity(true);
            noPhysics = true;
        }
    }
}
