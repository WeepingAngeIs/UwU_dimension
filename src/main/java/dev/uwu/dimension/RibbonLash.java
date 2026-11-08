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
    private java.util.UUID victim;
    public RibbonLash(EntityType<? extends RibbonLash> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(OWNER,-1);b.define(RELIC,0);b.define(DX,0F);b.define(DY,0F);b.define(DZ,0F);}
    static void cast(ServerLevel level,Player player,Arsenal.Relic kind){
        RibbonLash lash=RangedRelics.LASH.get().create(level);if(lash==null)return;
        Vec3 start=player.getEyePosition().add(0,-.3,0);
        LivingEntity target=RangedRelics.aim(level,player,12);
        Vec3 end=target==null?player.pick(12,1,false).getLocation():target.getBoundingBox().getCenter();
        lash.setPos(start);lash.entityData.set(OWNER,player.getId());lash.entityData.set(RELIC,kind.ordinal());
        Vec3 d=end.subtract(start);lash.entityData.set(DX,(float)d.x);lash.entityData.set(DY,(float)d.y);lash.entityData.set(DZ,(float)d.z);
        if(target!=null)lash.victim=target.getUUID();level.addFreshEntity(lash);
    }
    public Player owner(){return level().getEntity(entityData.get(OWNER)) instanceof Player p?p:null;}
    public Arsenal.Relic relic(){return Arsenal.Relic.values()[entityData.get(RELIC)];}
    public Vec3 tip(){return position().add(entityData.get(DX),entityData.get(DY),entityData.get(DZ));}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<16384;}
    @Override public void tick(){
        super.tick();
        if(level() instanceof ServerLevel s){
            Player p=owner();if(p==null||!p.isAlive()){discard();return;}
            if(tickCount==5&&victim!=null&&s.getEntity(victim) instanceof LivingEntity target&&p.distanceToSqr(target)<169&&p.hasLineOfSight(target))RangedRelics.strike(s,p,target,relic());
            if(tickCount>=12)discard();
        }
    }
    @Override protected void readAdditionalSaveData(CompoundTag n){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag n){}
}
