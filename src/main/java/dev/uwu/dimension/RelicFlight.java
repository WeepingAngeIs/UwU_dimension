package dev.uwu.dimension;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

public final class RelicFlight extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(RelicFlight.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ELEMENT=SynchedEntityData.defineId(RelicFlight.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WAVE=SynchedEntityData.defineId(RelicFlight.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> COUNTER=SynchedEntityData.defineId(RelicFlight.class,EntityDataSerializers.STRING);
    private boolean returning;
    public RelicFlight(EntityType<? extends RelicFlight> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(COUNTER,"");b.define(KIND,0);b.define(ELEMENT,0);b.define(WAVE,false);}
    @Override protected Item getDefaultItem(){return Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get();}
    public Arsenal.Relic relic(){return Arsenal.Relic.values()[entityData.get(KIND)];}
    public int element(){return entityData.get(ELEMENT);}
    public boolean wave(){return entityData.get(WAVE);}
    static void cast(ServerLevel level,Player owner,Arsenal.Relic kind,boolean wave){
        RelicFlight f=RangedRelics.FLIGHT.get().create(level);if(f==null)return;
        f.entityData.set(KIND,kind.ordinal());f.entityData.set(WAVE,wave);
        f.setOwner(owner);f.setPos(owner.getX(),owner.getEyeY()-.15,owner.getZ());
        f.setItem(new ItemStack(Arsenal.WEAPONS.get(kind).get()));f.setNoGravity(true);
        int element=owner.getPersistentData().getInt("uwu.scythe_element");
        if(kind==Arsenal.Relic.VESPER_SCYTHE&&!wave){element=(element+1)%4;owner.getPersistentData().putInt("uwu.scythe_element",element);owner.getPersistentData().putLong("uwu.scythe_flight",level.getGameTime()+60);}
        f.entityData.set(ELEMENT,element);var display=f.getItem();final int activeElement=element;
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,display,n->n.putInt("uwu_element",activeElement));f.setItem(display);f.shootFromRotation(owner,owner.getXRot(),owner.getYRot(),0,wave?1.5F:1.8F,0);
        level.addFreshEntity(f);
    }
    public String counter(){return entityData.get(COUNTER);}
    static void castCounter(ServerLevel level,Player p,CounterArsenal.Entry entry,boolean wave){
        RelicFlight f=RangedRelics.FLIGHT.get().create(level);if(f==null)return;
        f.entityData.set(KIND,Arsenal.Relic.VESPER_SCYTHE.ordinal());f.entityData.set(COUNTER,entry.id());f.entityData.set(WAVE,wave);
        int element=p.getPersistentData().getInt("uwu.scythe_element");
        if(!wave){element=(element+1)%4;p.getPersistentData().putInt("uwu.scythe_element",element);p.getPersistentData().putLong("uwu.scythe_flight",level.getGameTime()+60);}
        f.entityData.set(ELEMENT,element);var stack=new ItemStack(CounterArsenal.ITEMS.get(entry.id()).get());final int e=element;
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,stack,n->n.putInt("uwu_element",e));
        f.setItem(stack);f.setOwner(p);f.setPos(p.getX(),p.getEyeY()-.15,p.getZ());f.setNoGravity(true);f.shootFromRotation(p,p.getXRot(),p.getYRot(),0,wave?1.5F:1.8F,0);level.addFreshEntity(f);
    }
    @Override public void tick(){
        if(returning){
            if(!(getOwner() instanceof Player p)||!p.isAlive()){discard();return;}
            Vec3 delta=p.getEyePosition().subtract(position());
            if(delta.lengthSqr()<2){p.getPersistentData().remove("uwu.scythe_flight");discard();return;}
            noPhysics=true;setDeltaMovement(delta.normalize().scale(2));
        }
        super.tick();
        if(level() instanceof ServerLevel s){
            if(!wave()&&relic()==Arsenal.Relic.VESPER_SCYTHE&&tickCount>=12)returning=true;
            if(tickCount>55){if(getOwner()!=null)getOwner().getPersistentData().remove("uwu.scythe_flight");discard();}
            if(tickCount%2==0)s.sendParticles(element()==1?ParticleTypes.SNOWFLAKE:element()==2?ParticleTypes.SOUL_FIRE_FLAME:element()==3?ParticleTypes.ELECTRIC_SPARK:ParticleTypes.CHERRY_LEAVES,getX(),getY(),getZ(),2,.15,.15,.15,0);
        }
    }
    @Override protected void onHitEntity(EntityHitResult hit){
        if(returning)return;
        if(level() instanceof ServerLevel s&&getOwner() instanceof Player p&&hit.getEntity() instanceof LivingEntity target){
            if(!RangedRelics.enemy(p,target)){finish();return;}
            if(counter().isEmpty())RangedRelics.strike(s,p,target,relic());
            else CounterEffects.hit(s,p,target,CounterArsenal.entry(counter()));
            if(relic()==Arsenal.Relic.RIBBONHARE_SCEPTER)summonHare(s,p,target.blockPosition());
            if(relic()==Arsenal.Relic.VESPER_SCYTHE){
                switch(element()){
                    case 1->target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,60,2));
                    case 2->target.igniteForSeconds(3);
                    case 3->{target.setDeltaMovement(target.getDeltaMovement().add(0,.45,0));target.hurtMarked=true;}
                    default->p.heal(1);
                }
            }
        }
        finish();
    }
    @Override protected void onHitBlock(BlockHitResult hit){
        if(relic()==Arsenal.Relic.RIBBONHARE_SCEPTER && level() instanceof ServerLevel level && getOwner() instanceof Player p)summonHare(level,p,hit.getBlockPos().relative(hit.getDirection()));
        finish();
    }
    private void summonHare(ServerLevel level,Player p,net.minecraft.core.BlockPos pos){
        var hare=Arsenal.HARE.get().create(level);if(hare==null)return;
        hare.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        if(level.noCollision(hare)&&level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP))HeartMagic.callHare(level,p,hare);
    }
    private void finish(){if(!wave()&&relic()==Arsenal.Relic.VESPER_SCYTHE){returning=true;setDeltaMovement(Vec3.ZERO);}else discard();}
    @Override public void addAdditionalSaveData(CompoundTag n){super.addAdditionalSaveData(n);n.putString("counter",counter());n.putInt("relic",entityData.get(KIND));n.putInt("element",element());n.putBoolean("wave",wave());n.putBoolean("returning",returning);}
    @Override public void readAdditionalSaveData(CompoundTag n){super.readAdditionalSaveData(n);entityData.set(COUNTER,n.getString("counter"));entityData.set(KIND,Math.max(0,Math.min(Arsenal.Relic.values().length-1,n.getInt("relic"))));entityData.set(ELEMENT,n.getInt("element")%4);entityData.set(WAVE,n.getBoolean("wave"));returning=n.getBoolean("returning");}
}
