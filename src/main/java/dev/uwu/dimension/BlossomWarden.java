package dev.uwu.dimension;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** An elder flowering hedge. Protects residents from approaching undead and recorded attackers. */
public final class BlossomWarden extends PathfinderMob {
    public static final int LAUNCH_HEIGHT = 8000;
    /** Ticks to wait for a queued offender that has vanished (exploded, killed, despawned) before dropping it. */
    private static final int MISSING_GRACE = 10;
    /** Ticks without closing the gap before the guardian intercepts instead of waiting on a blocked route. */
    private static final int STALL_LIMIT = 60;
    private static final int PATROL_RETURN_RANGE = 50;
    private static final byte IDLE = 0, CHASE = 1, GRIP = 2, THROW = 3;
    private static final EntityDataAccessor<Byte> POSE = SynchedEntityData.defineId(BlossomWarden.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> FEMALE =
            SynchedEntityData.defineId(BlossomWarden.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> JUVENILE=SynchedEntityData.defineId(BlossomWarden.class,EntityDataSerializers.BOOLEAN);
    private final GuardianFamily family=new GuardianFamily();
    public boolean isBaby() {return entityData.get(JUVENILE);}
    public int familyGrowthTicks(){return family.growth;}
    public long familyReadyUntil(){return family.readyUntil;}
    public long familyRestUntil(){return family.restUntil;}
    GuardianFamily family(){return family;}
    void juvenile(boolean value){entityData.set(JUVENILE,value);}
    @Override protected net.minecraft.world.InteractionResult mobInteract(Player p,net.minecraft.world.InteractionHand hand){
        if(p.getItemInHand(hand).is(GuardianFamily.FOOD)){
            if(level().isClientSide)return net.minecraft.world.InteractionResult.SUCCESS;
            if(p instanceof ServerPlayer player)return family.feed(this,player,hand);
        }
        return super.mobInteract(p,hand);
    }
    public boolean female() { return entityData.get(FEMALE); }
    void setFemale(boolean girl) { entityData.set(FEMALE,girl); }

    @Override protected net.minecraft.network.chat.Component getTypeName() {
        return net.minecraft.network.chat.Component.translatable(female()
                ? "entity.uwudimension.blossom_warden.girl" : "entity.uwudimension.blossom_warden.male");
    }

    @Override protected net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        if (female()) return net.minecraft.world.entity.EntityDimensions.scalable(.78F,1.98F).withEyeHeight(1.64F).scale(getAgeScale());
        return super.getDefaultDimensions(pose);
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if(JUVENILE.equals(key)) {
            var max=getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if(max!=null)((GuardianVitality)max).uwu$guardianHealth(isBaby());
        }
        if (FEMALE.equals(key)||JUVENILE.equals(key)) refreshDimensions();
    }

    private boolean summoned;
    @Override public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
            net.minecraft.world.level.ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
            net.minecraft.world.entity.MobSpawnType reason, @Nullable net.minecraft.world.entity.SpawnGroupData group) {
        summoned = reason == net.minecraft.world.entity.MobSpawnType.SPAWN_EGG
                || reason == net.minecraft.world.entity.MobSpawnType.DISPENSER
                || reason == net.minecraft.world.entity.MobSpawnType.COMMAND;
        return super.finalizeSpawn(level,difficulty,reason,group);
    }

    private final ArrayDeque<UUID> offenders = new ArrayDeque<>();
    private final Map<UUID, Long> unreachableUntil = new HashMap<>();
    private BlockPos court;
    private int pursueTicks, motionTicks, missingTicks, stallTicks;
    private double bestGap = Double.MAX_VALUE;
    private double takeoffY;
    private boolean ownCombatSpeed;
    private Vec3 destination = Vec3.ZERO;

    public BlossomWarden(EntityType<? extends BlossomWarden> type, Level level) {
        super(type, level);
        ((GuardianVitality)getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)).uwu$guardianHealth(false);
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(6666);
        setHealth(6666);
        setPersistenceRequired();
        if (!level.isClientSide) entityData.set(FEMALE, random.nextBoolean());

    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(POSE, IDLE);
        builder.define(FEMALE, false);
        builder.define(JUVENILE,false);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // Own MOVE/LOOK throughout pursuit so wandering/home goals cannot overwrite the chase.
        goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.Goal(){
            {setFlags(java.util.EnumSet.of(Flag.MOVE,Flag.LOOK));}
            @Override public boolean canUse(){return !offenders.isEmpty();}
            @Override public boolean canContinueToUse(){return !offenders.isEmpty();}
            @Override public void start(){getNavigation().stop();}
        });
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, .85));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, .7));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 7));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    public void bindVillage(BlockPos center) {
        court = center.immutable();
        restrictTo(court, 22);
    }
    public boolean gripping() { return entityData.get(POSE) == GRIP; }
    public boolean throwing() { return entityData.get(POSE) == THROW; }
    @Nullable public UUID offender() { return offenders.peekFirst(); }

    String combatStatus() {
        String phase = switch (entityData.get(POSE)) {
            case CHASE -> "pursuing";
            case GRIP -> "holding";
            case THROW -> "releasing";
            default -> "patrolling";
        };
        return phase + ", " + offenders.size() + " queued, pursuit " + (pursueTicks / 20.0)
                + "s, stalled " + (stallTicks / 20.0) + "s, target missing " + (missingTicks / 20.0) + "s.";
    }

    static void alert(ServerLevel level, Keeper victim, LivingEntity culprit) {
        if (culprit == victim) return;
        BlockPos home = victim.villageCourt();
        BlossomWarden guard = null;
        if (home != null) {
            guard = directory(level).active(level, home);
            if (guard == null) guard = ensureVillageGuard(level, home);
        }
        if (guard == null) {
            guard = level.getEntitiesOfClass(BlossomWarden.class, victim.getBoundingBox().inflate(64),
                e -> e.isAlive() && (home == null || home.equals(e.court))).stream()
                .min(Comparator.comparingDouble(victim::distanceToSqr)).orElse(null);
        }
        if (guard != null) guard.accuse(culprit);
        else if (home != null) directory(level).report(home, culprit.getUUID());
    }

    @Nullable static BlossomWarden ensureVillageGuard(ServerLevel level, BlockPos center) {
        if (!level.hasChunkAt(center)) return null;
        Registry directory = directory(level);
        BlossomWarden known = directory.active(level, center);
        if (known != null) return known;
        for (BlossomWarden guard : level.getEntitiesOfClass(BlossomWarden.class, new AABB(center).inflate(42),
                g -> g.isAlive() && !g.summoned && !g.family.offspring && center.equals(g.court))) {
            directory.remember(center, guard);
            return guard;
        }
        // Entity storage catches up a few ticks after terrain loads.
        if (directory.guards.containsKey(center) && level.getGameTime() -
                directory.firstMissing.computeIfAbsent(center, ignored -> level.getGameTime()) < 200) return null;
        BlossomWarden replacement = People.BLOSSOM_WARDEN.get().create(level);
        if (replacement == null) return null;
        for (BlockPos at : BlockPos.withinManhattan(center, 6, 5, 6)) {
            if (!level.hasChunkAt(at) || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) continue;
            replacement.moveTo(at.getX()+.5, at.getY(), at.getZ()+.5, 0, 0);
            if (!level.noCollision(replacement) || level.containsAnyLiquid(replacement.getBoundingBox())) continue;
            replacement.bindVillage(center);
            if (level.addFreshEntity(replacement)) { directory.remember(center, replacement); return replacement; }
        }
        return null;
    }

    private void accuse(LivingEntity culprit) {
        if (culprit == this || !culprit.isAlive() || temporarilyUnreachable(culprit) || culprit.getPersistentData().contains(GuardianFlight.STATE) || offenders.contains(culprit.getUUID())) return;
        accuse(culprit.getUUID());
    }

    private void accuse(UUID id) {
        if (offenders.contains(id)) return;
        offenders.addLast(id);
        if (offenders.size()==1) transition(CHASE);
    }

    private void transition(byte phase) {
        entityData.set(POSE, phase);
        motionTicks = 0;
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        float previous = getHealth();
        boolean hurt = super.hurt(source, amount);
        if (hurt && getHealth() < previous && !level().isClientSide && source.getEntity() instanceof LivingEntity foe) accuse(foe);
        return hurt;
    }

    @Override public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            var regen=getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
            if(regen==null||regen.getAmplifier()<3) addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION,-1,3,true,false,true));
            var speed=getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
            if(!offenders.isEmpty()) {
                if(speed==null||speed.getAmplifier()<3||(ownCombatSpeed&&speed.getAmplifier()==3&&speed.getDuration()<=10&&!speed.isInfiniteDuration())) {
                    addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,10,3,true,false,true)); ownCombatSpeed=true;
                }
            } else if(ownCombatSpeed) {
                if(speed!=null&&speed.getAmplifier()==3&&speed.getDuration()<=10&&!speed.isInfiniteDuration())removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
                ownCombatSpeed=false;
            }
        }
        if (!(level() instanceof ServerLevel level)) return;
        family.tick(this,level);
        if (court == null) bindVillage(blockPosition());
        else if (family.offspring && distanceToSqr(Vec3.atBottomCenterOf(court)) > PATROL_RETURN_RANGE*PATROL_RETURN_RANGE)
            relocateFamilyPatrol(level);
        if (!summoned && !family.offspring && (tickCount == 1 || tickCount % 120 == 0)) {
            Registry registry = directory(level);
            BlossomWarden other = registry.active(level, court);
            if (other != null && other != this) { discard(); return; }
            registry.remember(court, this);
        }
        if (offenders.isEmpty() && tickCount%10==0) acquireThreat(level);
        if (offenders.isEmpty()) return;
        Entity tracked = level.getEntity(offenders.getFirst());
        if (!(tracked instanceof LivingEntity culprit) || !culprit.isAlive()) {
            // Still present but dead: drop now. Vanished (exploded, killed, despawned): drop after a short grace.
            if (tracked != null || ++missingTicks >= MISSING_GRACE) finish(level);
            return;
        }
        missingTicks = 0;
        setTarget(culprit);
        byte phase = entityData.get(POSE);
        if(culprit.getPersistentData().contains(GuardianFlight.STATE)&&phase!=GRIP&&phase!=THROW){finish(level);return;}
        if (phase == IDLE || phase == CHASE) {
            getLookControl().setLookAt(culprit, 30, 30);
            ++pursueTicks;
            boolean inReach = distanceToSqr(culprit) <= reachSquared(culprit);
            // If the gap stops closing (water, a cliff, no usable route), intercept now instead of waiting 600 ticks.
            double gap = Math.sqrt(distanceToSqr(culprit));
            if (gap < bestGap - .5) { bestGap = gap; stallTicks = 0; }
            else if (!inReach) stallTicks++;
            if (!inReach && (stallTicks >= STALL_LIMIT || pursueTicks >= 600)) {
                Vec3 landing = interceptPosition(level, culprit);
                if (landing == null) {
                    // Give the other threats a turn while this target remains boxed in.
                    unreachableUntil.put(culprit.getUUID(), level.getGameTime()+100);
                    finish(level);
                    return;
                }
                teleportTo(landing.x, landing.y, landing.z);
                setDeltaMovement(Vec3.ZERO);
                fallDistance = 0;
                stallTicks = 0;
                petals(level, position());
            }
            if (distanceToSqr(culprit) <= reachSquared(culprit)) {
                getNavigation().stop();
                culprit.stopRiding();
                takeoffY = culprit.getY();
                Vec3 outward = culprit.position().subtract(Vec3.atBottomCenterOf(court)).multiply(1,0,1).normalize();
                if (outward.lengthSqr() < .1) outward = new Vec3(1,0,0);
                double power=isBaby()?GuardianFamily.COMBAT_FRACTION:1.0;
                Vec3 away = culprit.position().add(outward.scale(96*power));
                var border = level.getWorldBorder().clampToBounds(away);
                destination = new Vec3(border.getX()+.5, takeoffY+LAUNCH_HEIGHT*power, border.getZ()+.5);
                transition(GRIP);
                sound(level, SoundEvents.CHERRY_WOOD_BREAK, .65F);
            } else if (pursueTicks % 10 == 1) getNavigation().moveTo(culprit, 1.0);
            return;
        }
        if (phase == GRIP) {
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            fallDistance = 0;
            Vec3 front = getLookAngle().multiply(1,0,1).normalize().scale(1.25*getAgeScale());
            Vec3 hands = position().add(front).add(0,(1.5+.4*Math.min(1,motionTicks/12.0))*getAgeScale(),0);
            if (culprit.distanceToSqr(hands) > .04) move(culprit,hands);
            culprit.setDeltaMovement(Vec3.ZERO);
            culprit.fallDistance=0;
            if (motionTicks % 4 == 0) petals(level,hands);
            if (++motionTicks >= 16) {
                transition(THROW);
                GuardianFlight.start(culprit,destination);
                level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL,getX(),getY()+2,getZ(),1,0,0,0,0);
                sound(level, SoundEvents.IRON_GOLEM_ATTACK, .72F);
            }
        } else if (phase == THROW) {
            // Migrate a saved pre-1.2.2 throw, or hand off the released target immediately.
            if(!culprit.getPersistentData().contains(GuardianFlight.STATE)&&culprit.getY()<destination.y-1)
                GuardianFlight.start(culprit,destination);
            finish(level);
        }
    }

    /** Family guardians patrol where their players have brought them, including after a saved move. */
    private void relocateFamilyPatrol(ServerLevel level) {
        UUID previous=offender();
        bindVillage(blockPosition());
        offenders.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity foe)
                || !foe.isAlive() || distanceToSqr(foe) > PATROL_RETURN_RANGE*PATROL_RETURN_RANGE);
        unreachableUntil.clear();
        if(previous!=null && !previous.equals(offender())){
            setTarget(null);
            getNavigation().stop();
            pursueTicks=0; missingTicks=0; stallTicks=0; bestGap=Double.MAX_VALUE;
            transition(offenders.isEmpty()?IDLE:CHASE);
        }
    }

    private boolean temporarilyUnreachable(LivingEntity target) {
        return unreachableUntil.getOrDefault(target.getUUID(), 0L) > level().getGameTime()
                && distanceToSqr(target) > reachSquared(target);
    }

    private void acquireThreat(ServerLevel level) {
        unreachableUntil.values().removeIf(expiry -> expiry <= level.getGameTime());
        AABB area = new AABB(this.court).inflate(48.0D);
        List<Keeper> keepers = level.getEntitiesOfClass(Keeper.class, area,
                keeper -> keeper.isAlive()
                        && (keeper.villageCourt() == null || this.court.equals(keeper.villageCourt())));
        level.getEntitiesOfClass(Mob.class, area, mob -> isHostileThreat(mob, keepers))
                .stream()
                .min(Comparator.comparingDouble((Mob mob) -> this.distanceToSqr(mob)))
                .ifPresent(this::accuse);
    }

    private boolean isHostileThreat(Mob mob, List<Keeper> keepers) {
        if (mob == this || mob instanceof BlossomWarden || mob instanceof Keeper || !mob.isAlive() || temporarilyUnreachable(mob)) return false;
        if (mob.getPersistentData().contains(GuardianFlight.STATE) || !(mob instanceof Enemy)) return false;
        boolean guardsNearby = distanceToSqr(mob) <= 24 * 24 && hasLineOfSight(mob);
        return guardsNearby || keepers.stream().anyMatch(keeper -> mob.distanceToSqr(keeper) <= 24.0D * 24.0D);
    }

    @Nullable private Vec3 interceptPosition(ServerLevel level, LivingEntity target) {
        double rear = Math.atan2(-target.getLookAngle().z, -target.getLookAngle().x);
        double clearance = (getBbWidth() + target.getBbWidth()) * .5 + .4;
        for (int i = 0; i < 8; i++) {
            double angle = rear + i * Math.PI / 4;
            Vec3 landing = target.position().add(Math.cos(angle)*clearance, 0, Math.sin(angle)*clearance);
            AABB bounds = getBoundingBox().move(landing.subtract(position()));
            if (level.hasChunksAt(BlockPos.containing(bounds.minX,bounds.minY,bounds.minZ),
                    BlockPos.containing(bounds.maxX,bounds.maxY,bounds.maxZ))
                    && level.getWorldBorder().isWithinBounds(bounds) && level.noCollision(this, bounds)) return landing;
        }
        return null;
    }

    private double reachSquared(LivingEntity foe) {
        double reach = 1.95 + getBbWidth()/2 + foe.getBbWidth()/2;
        return reach*reach;
    }

    private static void move(LivingEntity target,Vec3 spot) {
        // The player connection synchronizes the acknowledged client position even outside
        // build height. Vanilla Entity.teleportTo handles mobs and their chunk tracking.
        if (target instanceof ServerPlayer player) {
            player.connection.teleport(spot.x,spot.y,spot.z,player.getYRot(),player.getXRot());
        } else {
            target.teleportTo(spot.x,spot.y,spot.z);
        }
    }
    private void finish(ServerLevel level) {
        offenders.removeFirst();
        setTarget(null);
        getNavigation().stop();
        pursueTicks=0; missingTicks=0; stallTicks=0; bestGap=Double.MAX_VALUE;
        transition(offenders.isEmpty()?IDLE:CHASE);
        if (offenders.isEmpty() && court != null && level.hasChunkAt(court)) {
            Vec3 home = Vec3.atBottomCenterOf(court);
            if (distanceToSqr(home) > PATROL_RETURN_RANGE*PATROL_RETURN_RANGE && level.noCollision(this, getBoundingBox().move(home.subtract(position())))) {
                teleportTo(home.x, home.y, home.z);
                petals(level,position());
            }
        }
    }

    private void petals(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.CHERRY_LEAVES,pos.x,pos.y+.3,pos.z,12,.65,.35,.65,.03);
    }
    private void sound(ServerLevel level, net.minecraft.sounds.SoundEvent effect, float pitch) {
        level.playSound(null, blockPosition(),effect,SoundSource.NEUTRAL,1.2F,pitch);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        family.save(tag);
        tag.putInt("GuardianVitalityVersion",1);
        tag.putBoolean("FemaleGuardian", female());
        tag.putBoolean("KeeperSummonedGuardian", summoned);
        if (court != null) tag.putLong("VillageCourt",court.asLong());
        ListTag list = new ListTag();
        for (UUID id:offenders) { var entry = new CompoundTag(); entry.putUUID("Offender",id); list.add(entry); }
        tag.put("Offenders",list); tag.putByte("GripPhase",entityData.get(POSE)); tag.putInt("PursueTicks",pursueTicks);
        tag.putInt("MotionTicks",motionTicks); tag.putDouble("Takeoff",takeoffY);
        tag.putDouble("TossX",destination.x); tag.putDouble("TossY",destination.y); tag.putDouble("TossZ",destination.z);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setFemale(tag.getBoolean("FemaleGuardian"));
        summoned = tag.getBoolean("KeeperSummonedGuardian");
        double oldMaximum=320;
        var attributes=tag.getList("attributes",Tag.TAG_COMPOUND);
        for(int i=0;i<attributes.size();i++) if(attributes.getCompound(i).getString("id").equals("minecraft:generic.max_health"))oldMaximum=attributes.getCompound(i).getDouble("base");
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(6666);
        if(!tag.contains("GuardianVitalityVersion"))setHealth((float)(6666*Math.clamp(tag.getFloat("Health")/Math.max(1,oldMaximum),0,1)));
        family.read(this,tag);
        if(tag.contains("VillageCourt")) bindVillage(BlockPos.of(tag.getLong("VillageCourt")));
        offenders.clear();
        var list=tag.getList("Offenders",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++) if(list.getCompound(i).hasUUID("Offender")) offenders.add(list.getCompound(i).getUUID("Offender"));
        entityData.set(POSE,offenders.isEmpty()?IDLE:tag.getByte("GripPhase")); pursueTicks=tag.getInt("PursueTicks");
        motionTicks=tag.getInt("MotionTicks"); takeoffY=tag.getDouble("Takeoff");
        destination=new Vec3(tag.getDouble("TossX"),tag.getDouble("TossY"),tag.getDouble("TossZ"));
    }

    private static Registry directory(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Registry.FACTORY,"uwudimension_guardians");
    }
    private static final class Registry extends SavedData {
        static final Factory<Registry> FACTORY=new Factory<>(Registry::new,Registry::load,null);
        final Map<BlockPos,UUID> guards=new HashMap<>();
        final Map<BlockPos,Long> firstMissing=new HashMap<>();
        final Map<BlockPos,LinkedHashSet<UUID>> pending=new HashMap<>();

        void report(BlockPos home, UUID culprit) {
            if (pending.computeIfAbsent(home, ignored -> new LinkedHashSet<>()).add(culprit)) setDirty();
        }
        @Nullable BlossomWarden active(ServerLevel level,BlockPos home) {
            UUID id=guards.get(home);
            return id!=null && level.getEntity(id) instanceof BlossomWarden w && w.isAlive()?w:null;
        }
        void remember(BlockPos home,BlossomWarden guardian) {
            if (!guardian.getUUID().equals(guards.put(home,guardian.getUUID()))) setDirty();
            firstMissing.remove(home);
            var reports = pending.remove(home);
            if (reports != null) {
                reports.forEach(guardian::accuse);
                setDirty();
            }
        }
        static Registry load(CompoundTag tag,HolderLookup.Provider registry) {
            Registry saved=new Registry();
            ListTag list=tag.getList("Courts",Tag.TAG_COMPOUND);
            for(int i=0;i<list.size();i++) {
                CompoundTag c=list.getCompound(i);
                saved.guards.put(BlockPos.of(c.getLong("At")),c.getUUID("Guard"));
            }
            var reports = tag.getList("Reports",Tag.TAG_COMPOUND);
            for (int i=0;i<reports.size();i++) {
                var report=reports.getCompound(i);
                saved.pending.computeIfAbsent(BlockPos.of(report.getLong("At")), ignored -> new LinkedHashSet<>())
                        .add(report.getUUID("Offender"));
            }
            return saved;
        }
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registry) {
            var list=new ListTag();
            guards.forEach((p,g)->{var c=new CompoundTag(); c.putLong("At",p.asLong()); c.putUUID("Guard",g); list.add(c);});
            tag.put("Courts",list);
            var reports = new ListTag();
            pending.forEach((home, ids) -> ids.forEach(id -> {
                var report = new CompoundTag();
                report.putLong("At",home.asLong());
                report.putUUID("Offender",id);
                reports.add(report);
            }));
            tag.put("Reports",reports);
            return tag;
        }
    }
}



