package dev.uwu.dimension;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A stationary moth conducting a fight from the court's mosaic rose. */
public final class VelvetVesper extends PathfinderMob {
    public static final int SEQUENCE=280;
    private static final int OPEN=0, NEEDLE=1, FOLDING=2, MIRROR=3, POLLEN=4;
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(VelvetVesper.class,EntityDataSerializers.INT);
    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("entity.uwudimension.velvet_vesper"),
            BossEvent.BossBarColor.PINK,BossEvent.BossBarOverlay.PROGRESS);
    private final Map<UUID,Long> charmEnd=new HashMap<>();
    private BlockPos bell;
    private int beat;
    private Vec3 aim=Vec3.ZERO;

    public VelvetVesper(EntityType<? extends PathfinderMob> type,Level world) {
        super(type,world);
        setPersistenceRequired();
        setNoGravity(true);
        xpReward=65;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE,OPEN);
    }
    @Override public boolean isPushable() {return false;}
    @Override public boolean removeWhenFarAway(double distance) {return false;}
    public void bindBell(BlockPos pos) {bell=pos.immutable();}
    public boolean guarding() {return entityData.get(PHASE)==MIRROR;}
    public boolean folding() {return entityData.get(PHASE)==FOLDING;}
    public boolean casting() {int p=entityData.get(PHASE);return p==NEEDLE || p==POLLEN;}
    public boolean isCharmed(LivingEntity attacker) {
        return level().getGameTime()<charmEnd.getOrDefault(attacker.getUUID(),0L);
    }
    /** Publicly inspectable server-side values also used by the hosted expedition probe. */
    public int beat() {return beat;}
    public ServerBossEvent encounterBar() {return bar;}

    boolean refusesAttack(LivingEntity source) {
        if (!(level() instanceof ServerLevel server)) return false;
        if (isCharmed(source)) return true;
        if (!guarding()) return false;
        charmEnd.put(source.getUUID(),server.getGameTime()+200);
        source.addEffect(new MobEffectInstance(MirrorCourt.DEVOTION,200,0,false,true,true));
        if (source instanceof Mob mob && mob.getTarget()==this) mob.setTarget(null);
        if (source instanceof Player player) player.displayClientMessage(Component.translatable("message.uwudimension.mirror_devotion"),true);
        server.sendParticles(ParticleTypes.HEART,source.getX(),source.getEyeY(),source.getZ(),9,.28,.14,.28,.02);
        server.playSound(null,source.blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.HOSTILE,.9F,.74F);
        return true;
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        // getEntity is the credited attacker, including arrows, thrown relics and magic.
        if (amount>0 && source.getEntity() instanceof LivingEntity attacker && refusesAttack(attacker)) return false;
        boolean accepted=super.hurt(source,amount);
        if (!level().isClientSide && accepted) bar.setProgress(getHealth()/getMaxHealth());
        return accepted;
    }
    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }
    @Override public void remove(Entity.RemovalReason reason) {
        if (level() instanceof ServerLevel server) {
            bar.removeAllPlayers();
            if (reason.shouldDestroy()) MirrorCourt.release(server,bell,getUUID());
        }
        super.remove(reason);
    }
    @Override public void die(DamageSource source) {
        super.die(source);
        if (isDeadOrDying() && level() instanceof ServerLevel server) {
            bar.removeAllPlayers();
            MirrorCourt.release(server,bell,getUUID());
            server.sendParticles(ParticleTypes.CHERRY_LEAVES,getX(),getY()+1,getZ(),65,1,1,1,.09);
        }
    }
    private static int phaseAt(int t) {
        if(t>=40 && t<=75) return NEEDLE;
        if(t>=104 && t<132) return FOLDING;
        if(t>=132 && t<180) return MIRROR;
        if(t>=198 && t<=236) return POLLEN;
        return OPEN;
    }
    private void syncPhase() {
        int phase=phaseAt(beat);
        entityData.set(PHASE,phase);
        bar.setColor(phase==MIRROR ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.PINK);
        String key=switch(phase) {
            case NEEDLE -> "needle";
            case FOLDING -> "folding";
            case MIRROR -> "guard";
            case POLLEN -> "pollen";
            default -> "open";
        };
        bar.setName(Component.translatable("boss.uwudimension.vesper."+key));
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel server=(ServerLevel)level();
        charmEnd.values().removeIf(end->server.getGameTime()>=end);
        bar.setProgress(getHealth()/getMaxHealth());
        var viewers=new HashSet<>(server.getPlayers(p->p.isAlive() && p.distanceToSqr(this)<20*20 && Math.abs(p.getY()-getY())<9));
        for (ServerPlayer watching:new HashSet<>(bar.getPlayers())) if(!viewers.contains(watching)) bar.removePlayer(watching);
        ServerPlayer focus=null;
        for (ServerPlayer watcher:viewers) {
            bar.addPlayer(watcher);
            if (watcher.isCreative() || watcher.isSpectator() || !hasLineOfSight(watcher)) continue;
            if(focus==null || watcher.distanceToSqr(this)<focus.distanceToSqr(this)) focus=watcher;
        }
        if(focus==null) return;
        getLookControl().setLookAt(focus,20,20);
        beat=(beat+1)%SEQUENCE;
        if (entityData.get(PHASE)!=phaseAt(beat)) syncPhase();
        if (beat==40) {
            aim=focus.getEyePosition().subtract(getEyePosition()).normalize();
            chime(server,1.45F);
        }
        if (beat>=40 && beat<75 && beat%4==0) needleTrail(server,ParticleTypes.END_ROD);
        if (beat==75) {
            needleTrail(server,ParticleTypes.ELECTRIC_SPARK);
            Vec3 start=getEyePosition(), end=start.add(aim.scale(18));
            for (ServerPlayer p:viewers) {
                if (validVictim(p) && p.getBoundingBox().inflate(.42).clip(start,end).isPresent()) p.hurt(damageSources().mobAttack(this),8);
            }
        }
        if (beat==104 || beat==119 || beat==131) chime(server,.68F+(beat-104)*.014F);
        if ((folding() || guarding()) && beat%6==0)
            ring(server,position().add(0,1.35,0),folding()?1.9-(beat-104)*.025:1.35,guarding()?ParticleTypes.HEART:ParticleTypes.WAX_ON);
        if(beat==198) {
            aim=focus.position().add(0,.13,0);
            chime(server,.58F);
        }
        if(beat>=198 && beat<236 && beat%4==0) ring(server,aim,2.8,ParticleTypes.CHERRY_LEAVES);
        if(beat==236) {
            ring(server,aim,2.8,ParticleTypes.GLOW);
            server.sendParticles(ParticleTypes.CHERRY_LEAVES,aim.x,aim.y+1,aim.z,32,.8,.35,.8,.08);
            for (ServerPlayer p:viewers) if(validVictim(p) && p.position().distanceToSqr(aim)<2.8*2.8)
                p.hurt(damageSources().mobAttack(this),10);
        }
    }
    private boolean validVictim(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator() && hasLineOfSight(player);
    }
    private void chime(ServerLevel level,float pitch) {
        level.playSound(null,blockPosition(),SoundEvents.AMETHYST_BLOCK_RESONATE,SoundSource.HOSTILE,1.25F,pitch);
    }
    private void needleTrail(ServerLevel server,ParticleOptions particle) {
        Vec3 start=getEyePosition();
        for (int i=1;i<=24;i++) {
            Vec3 p=start.add(aim.scale(i*.7));
            server.sendParticles(particle,p.x,p.y,p.z,1,0,0,0,0);
        }
    }
    private static void ring(ServerLevel server,Vec3 c,double r,ParticleOptions particle) {
        for(int i=0;i<18;i++) {
            double a=i*Math.PI/9;
            server.sendParticles(particle,c.x+r*Math.cos(a),c.y,c.z+r*Math.sin(a),1,0,0,0,0);
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CourtBeat",beat);
        tag.putDouble("AimX",aim.x);tag.putDouble("AimY",aim.y);tag.putDouble("AimZ",aim.z);
        if (bell!=null) tag.putLong("CourtBell",bell.asLong());
        var rows=new ListTag();
        charmEnd.forEach((id,end)->{var r=new CompoundTag();r.putUUID("Attacker",id);r.putLong("Until",end);rows.add(r);});
        tag.put("MirrorBonds",rows);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        beat=Math.floorMod(tag.getInt("CourtBeat"),SEQUENCE);
        aim=new Vec3(tag.getDouble("AimX"),tag.getDouble("AimY"),tag.getDouble("AimZ"));
        bell=tag.contains("CourtBell")?BlockPos.of(tag.getLong("CourtBell")):null;
        charmEnd.clear();
        for (Tag t:tag.getList("MirrorBonds",Tag.TAG_COMPOUND)) {
            var r=(CompoundTag)t;
            if(r.hasUUID("Attacker")) charmEnd.put(r.getUUID("Attacker"),r.getLong("Until"));
        }
        syncPhase();
        bar.setProgress(getHealth()/getMaxHealth());
    }
}
