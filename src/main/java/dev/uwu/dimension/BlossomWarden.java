package dev.uwu.dimension;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** An elder flowering hedge. Protects residents from approaching undead and recorded attackers. */
public final class BlossomWarden extends PathfinderMob {
    public static final int LAUNCH_HEIGHT = 8000;
    private static final byte IDLE = 0, CHASE = 1, GRIP = 2, THROW = 3;
    private static final EntityDataAccessor<Byte> POSE = SynchedEntityData.defineId(BlossomWarden.class, EntityDataSerializers.BYTE);
    private final ArrayDeque<UUID> offenders = new ArrayDeque<>();
    private BlockPos court;
    private int pursueTicks, motionTicks, missingTicks;
    private double takeoffY;
    private Vec3 destination = Vec3.ZERO;

    public BlossomWarden(EntityType<? extends BlossomWarden> type, Level level) {
        super(type, level);
        setPersistenceRequired();

    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(POSE, IDLE);
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
                g -> g.isAlive() && center.equals(g.court))) {
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
        if (culprit == this || !culprit.isAlive() || culprit.getPersistentData().contains(GuardianFlight.STATE) || offenders.contains(culprit.getUUID())) return;
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
        if (!(level() instanceof ServerLevel level)) return;
        if (court == null) bindVillage(blockPosition());
        if (tickCount == 1 || tickCount % 120 == 0) {
            Registry registry = directory(level);
            BlossomWarden other = registry.active(level, court);
            if (other != null && other != this) { discard(); return; }
            registry.remember(court, this);
        }
        if (offenders.isEmpty() && tickCount%10==0) acquireThreat(level);
        if (offenders.isEmpty()) return;
        // This short vanilla ticket expires automatically and keeps an active pursuit ticking.
        if (tickCount % 3 == 0) level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkPosition(), 2, getId());
        Entity tracked = level.getEntity(offenders.getFirst());
        if (!(tracked instanceof LivingEntity culprit) || !culprit.isAlive()) {
            if (tracked != null || ++missingTicks > 600) finish(level);
            return;
        }
        missingTicks = 0;
        setTarget(culprit);
        byte phase = entityData.get(POSE);
        if(culprit.getPersistentData().contains(GuardianFlight.STATE)&&phase!=GRIP&&phase!=THROW){finish(level);return;}
        if (phase == IDLE || phase == CHASE) {
            getLookControl().setLookAt(culprit, 30, 30);
            if (++pursueTicks > 600 && distanceToSqr(culprit) > reachSquared(culprit)) {
                Vec3 side = culprit.getLookAngle().multiply(-1,0,-1).normalize().scale(1.4);
                // Intercept works even on a cliff or in flight. The grip begins on this same tick.
                teleportTo(culprit.getX()+side.x, culprit.getY(), culprit.getZ()+side.z);
                setDeltaMovement(Vec3.ZERO);
                fallDistance = 0;
                petals(level, position());
            }
            if (distanceToSqr(culprit) <= reachSquared(culprit)) {
                getNavigation().stop();
                culprit.stopRiding();
                takeoffY = culprit.getY();
                Vec3 outward = culprit.position().subtract(Vec3.atBottomCenterOf(court)).multiply(1,0,1).normalize();
                if (outward.lengthSqr() < .1) outward = new Vec3(1,0,0);
                Vec3 away = culprit.position().add(outward.scale(96));
                var border = level.getWorldBorder().clampToBounds(away);
                destination = new Vec3(border.getX()+.5, takeoffY+LAUNCH_HEIGHT, border.getZ()+.5);
                transition(GRIP);
                sound(level, SoundEvents.CHERRY_WOOD_BREAK, .65F);
            } else if (pursueTicks % 10 == 1) getNavigation().moveTo(culprit, 1.0);
            return;
        }
        if (phase == GRIP) {
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            fallDistance = 0;
            Vec3 front = getLookAngle().multiply(1,0,1).normalize().scale(1.25);
            Vec3 hands = position().add(front).add(0, 1.5 + .4 * Math.min(1, motionTicks/12.0), 0);
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

    private void acquireThreat(ServerLevel level){
        var residents=level.getEntitiesOfClass(Keeper.class,new AABB(court).inflate(48),k->k.isAlive()&&(k.villageCourt()==null||court.equals(k.villageCourt())));
        if(residents.isEmpty())return;
        level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new AABB(court).inflate(48),mob->{
            if(!mob.isAlive()||mob==this||mob instanceof Keeper||mob instanceof BlossomWarden||mob.getPersistentData().contains(GuardianFlight.STATE))return false;
            boolean hostileUndead=mob instanceof net.minecraft.world.entity.monster.Zombie && !(mob instanceof net.minecraft.world.entity.monster.ZombifiedPiglin);
            return residents.stream().anyMatch(k->mob.distanceToSqr(k)<=24*24&&(hostileUndead||mob.getTarget()==k));
        }).stream().min(Comparator.comparingDouble(this::distanceToSqr)).ifPresent(this::accuse);
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
        pursueTicks=0; missingTicks=0;
        transition(offenders.isEmpty()?IDLE:CHASE);
        if (offenders.isEmpty() && court != null && distanceToSqr(Vec3.atBottomCenterOf(court))>50*50) {
            teleportTo(court.getX()+.5,court.getY(),court.getZ()+.5);
            petals(level,position());
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
        if (court != null) tag.putLong("VillageCourt",court.asLong());
        ListTag list = new ListTag();
        for (UUID id:offenders) { var entry = new CompoundTag(); entry.putUUID("Offender",id); list.add(entry); }
        tag.put("Offenders",list); tag.putByte("GripPhase",entityData.get(POSE)); tag.putInt("PursueTicks",pursueTicks);
        tag.putInt("MotionTicks",motionTicks); tag.putDouble("Takeoff",takeoffY);
        tag.putDouble("TossX",destination.x); tag.putDouble("TossY",destination.y); tag.putDouble("TossZ",destination.z);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
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



