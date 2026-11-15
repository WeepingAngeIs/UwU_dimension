package dev.uwu.dimension;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Twelve-tick textured lash: extends for five ticks, lands, then recoils. */
public final class RibbonLash extends Entity {
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(RibbonLash.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RELIC=SynchedEntityData.defineId(RibbonLash.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DX=SynchedEntityData.defineId(RibbonLash.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY=SynchedEntityData.defineId(RibbonLash.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ=SynchedEntityData.defineId(RibbonLash.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> COUNTER=SynchedEntityData.defineId(RibbonLash.class,EntityDataSerializers.STRING);
    private java.util.UUID victim;
    public RibbonLash(EntityType<? extends RibbonLash> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(COUNTER,"");b.define(OWNER,-1);b.define(RELIC,0);b.define(DX,0F);b.define(DY,0F);b.define(DZ,0F);}
    static RibbonLash cast(ServerLevel level,Player player,Arsenal.Relic kind){
        RibbonLash lash=RangedRelics.LASH.get().create(level);if(lash==null)return null;
        Vec3 start=player.getEyePosition().add(0,-.3,0);
        LivingEntity target=RangedRelics.aim(level,player,12);
        Vec3 end=target==null?player.pick(12,1,false).getLocation():target.getBoundingBox().getCenter();
        lash.setPos(start);lash.entityData.set(OWNER,player.getId());lash.entityData.set(RELIC,kind.ordinal());
        Vec3 d=end.subtract(start);lash.entityData.set(DX,(float)d.x);lash.entityData.set(DY,(float)d.y);lash.entityData.set(DZ,(float)d.z);
        if(target!=null)lash.victim=target.getUUID();return level.addFreshEntity(lash)?lash:null;
    }
    static RibbonLash castCounter(ServerLevel level,Player p,CounterArsenal.Entry entry){
        RibbonLash lash=RangedRelics.LASH.get().create(level);if(lash==null)return null;
        Vec3 start=p.getEyePosition().add(0,-.3,0);LivingEntity target=RangedRelics.aim(level,p,16);
        Vec3 end=target==null?p.pick(16,1,false).getLocation():target.getBoundingBox().getCenter();
        lash.setPos(start);lash.entityData.set(OWNER,p.getId());lash.entityData.set(RELIC,Arsenal.Relic.SATIN_WHIP.ordinal());lash.entityData.set(COUNTER,entry.id());
        Vec3 d=end.subtract(start);lash.entityData.set(DX,(float)d.x);lash.entityData.set(DY,(float)d.y);lash.entityData.set(DZ,(float)d.z);
        if(target!=null)lash.victim=target.getUUID();return level.addFreshEntity(lash)?lash:null;
    }
    public String counter(){return entityData.get(COUNTER);}
    public Player owner(){return level().getEntity(entityData.get(OWNER)) instanceof Player p?p:null;}
    public Arsenal.Relic relic(){return Arsenal.Relic.values()[entityData.get(RELIC)];}
    public Vec3 tip(){return position().add(entityData.get(DX),entityData.get(DY),entityData.get(DZ));}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<16384;}
    @Override public void tick(){
        super.tick();
        if(level() instanceof ServerLevel s){
            Player p=owner();if(p==null||!p.isAlive()){discard();return;}
            if(tickCount%2==0)HeartCombatGlow.trail(s,tip(),relic()==Arsenal.Relic.FROST_WHIP?1:0);
            if(tickCount<=5 && victim!=null && s.getEntity(victim) instanceof LivingEntity target && p.distanceToSqr(target)<=289 && p.hasLineOfSight(target)){
                Vec3 delta=target.getBoundingBox().getCenter().subtract(position());entityData.set(DX,(float)delta.x);entityData.set(DY,(float)delta.y);entityData.set(DZ,(float)delta.z);
            }
            if(tickCount==5&&victim!=null&&s.getEntity(victim) instanceof LivingEntity target&&p.distanceToSqr(target)<289&&p.hasLineOfSight(target)){
                if(counter().isEmpty()) {
                    if(RangedRelics.strike(s,p,target,relic(),p.position(),this) && getPersistentData().hasUUID("uwu.glitch_token")) {
                        if(relic()==Arsenal.Relic.FROST_WHIP){GlitchCompletions.track(this,target,"freeze");GlitchCompletions.track(this,target,"slow");}
                        if(relic()==Arsenal.Relic.RIFT_WHIP)GlitchCompletions.track(this,p,"rift_mark");
                        int effect=switch(relic()){case RIFT_WHIP -> 160;default -> 0;};
                        getPersistentData().putInt("uwu.glitch_effect_ticks",effect);
                    }
                } else {
                    var entry=CounterArsenal.entry(counter());
                    if(entry!=null && CounterEffects.holds(target,entry.target()) && getPersistentData().hasUUID("uwu.glitch_token"))
                        getPersistentData().putInt("uwu.glitch_effect_ticks",160);
                    CounterEffects.hit(s,p,target,entry);
                    if(entry!=null && CounterEffects.holds(target,entry.target()))GlitchCompletions.track(this,target,"counter_mark");
                    if(entry!=null && CounterEffects.holds(target,entry.target()) && (entry.school().equals("STAGGER")||entry.school().equals("GUARD")))
                        GlitchCompletions.track(this,target,"slow");
                }
                HeartCombatGlow.burst(s,target.position().add(0,target.getBbHeight()*.5,0),1.1,relic()==Arsenal.Relic.FROST_WHIP?1:0);
            }
            if(tickCount>=12)discard();
        }
    }
    @Override public void remove(Entity.RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel server
                && getPersistentData().hasUUID("uwu.glitch_id") && getPersistentData().hasUUID("uwu.glitch_token"))
            GlitchCompletions.complete(server,getPersistentData().getUUID("uwu.glitch_id"),getPersistentData().getUUID("uwu.glitch_token"),getPersistentData().getInt("uwu.glitch_effect_ticks"));
        super.remove(reason);
    }
    @Override protected void readAdditionalSaveData(CompoundTag n){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag n){}
}
