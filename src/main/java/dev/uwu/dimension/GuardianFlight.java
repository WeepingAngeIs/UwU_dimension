package dev.uwu.dimension;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Real velocity flight. Position changes occur only for measured obstruction recovery. */
public final class GuardianFlight {
    public static final String STATE="uwu.guardian_flight", RECOVERIES="uwu.guardian_recoveries";
    public static final String CLIENT="uwu.guardian_thrust";
    public record Thrust(double x,double y,double z,boolean active) implements CustomPacketPayload {
        public static final Type<Thrust> TYPE=new Type<>(UwUDimension.id("guardian_thrust"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Thrust> CODEC=StreamCodec.of(
            (b,v)->{b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);b.writeBoolean(v.active);},
            b->new Thrust(b.readDouble(),b.readDouble(),b.readDouble(),b.readBoolean()));
        @Override public Type<Thrust> type(){return TYPE;}
    }
    public static void register(IEventBus bus){
        bus.addListener(GuardianFlight::network);
        NeoForge.EVENT_BUS.addListener(GuardianFlight::tick);
        NeoForge.EVENT_BUS.addListener(GuardianFlight::logout);
    }
    private static void network(RegisterPayloadHandlersEvent event){
        event.registrar("1").playToClient(Thrust.TYPE,Thrust.CODEC,(m,ctx)->ctx.enqueueWork(()->{
            var p=ctx.player();
            if(!m.active){if(p.getPersistentData().contains(CLIENT))p.noCulling=p.getPersistentData().getCompound(CLIENT).getBoolean("OriginalCulling");p.getPersistentData().remove(CLIENT);p.setDeltaMovement(Vec3.ZERO);return;}
            boolean original=p.getPersistentData().contains(CLIENT)?p.getPersistentData().getCompound(CLIENT).getBoolean("OriginalCulling"):p.noCulling;
            var n=new CompoundTag();n.putBoolean("OriginalCulling",original);n.putDouble("X",m.x);n.putDouble("Y",m.y);n.putDouble("Z",m.z);n.putInt("Until",p.tickCount+6);
            p.getPersistentData().put(CLIENT,n);
        }));
    }
    public static void start(LivingEntity target,Vec3 destination){
        if(!(target.level() instanceof ServerLevel))return;
        target.stopRiding();target.fallDistance=0;
        var n=new CompoundTag();n.putString("Dimension",target.level().dimension().location().toString());
        n.putDouble("X",destination.x);n.putDouble("Y",destination.y);n.putDouble("Z",destination.z);
        n.putDouble("WindowY",target.getY());n.putInt("Age",0);n.putInt("Window",0);
        target.getPersistentData().put(STATE,n);
        thrust(target,new Vec3(0,3.6,0));
    }
    private static void thrust(LivingEntity target,Vec3 velocity){
        target.setDeltaMovement(velocity);
        // The dedicated payload is applied before client movement each tick, after vanilla's
        // clamped tracking packets. Server expected velocity matches client-authoritative motion.
        if(target instanceof ServerPlayer p){
            // Incoming movement can still reflect an earlier authorized impulse while a braking
            // message is in transit. Validate against this launch's finite 48-block/tick ceiling,
            // keeping native collision and distance checks enabled throughout the flight.
            p.setDeltaMovement(velocity.x,48,velocity.z);
            PacketDistributor.sendToPlayer(p,new Thrust(velocity.x,velocity.y,velocity.z,true));
        }
        else target.hurtMarked=true;
    }
    private static void stop(LivingEntity target){
        target.getPersistentData().remove(STATE);target.setDeltaMovement(Vec3.ZERO);target.fallDistance=0;
        if(target instanceof ServerPlayer p)PacketDistributor.sendToPlayer(p,new Thrust(0,0,0,false));
        else target.hurtMarked=true;
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity().getPersistentData().contains(STATE))stop(event.getEntity());
    }
    private static void tick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof LivingEntity target)||!(target.level() instanceof ServerLevel level)||!target.getPersistentData().contains(STATE))return;
        var n=target.getPersistentData().getCompound(STATE);
        if(!target.isAlive()||!n.getString("Dimension").equals(level.dimension().location().toString())){stop(target);return;}
        double remaining=n.getDouble("Y")-target.getY();
        if(remaining<=1){stop(target);return;}
        int age=n.getInt("Age")+1;n.putInt("Age",age);
        level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT,target.chunkPosition(),2,target.getId());
        target.fallDistance=0;
        int window=n.getInt("Window")+1;
        if(window>=20){
            double rise=target.getY()-n.getDouble("WindowY");
            if(age>=40&&rise<.05){
                // Twenty measured stuck ticks after the initial grace, not a journey timeout.
                var destination=new Vec3(n.getDouble("X"),n.getDouble("Y"),n.getDouble("Z"));
                if(target instanceof ServerPlayer p)p.connection.teleport(destination.x,destination.y,destination.z,p.getYRot(),p.getXRot());
                else target.teleportTo(destination.x,destination.y,destination.z);
                target.getPersistentData().putInt(RECOVERIES,target.getPersistentData().getInt(RECOVERIES)+1);
                stop(target);return;
            }
            n.putDouble("WindowY",target.getY());window=0;
        }
        n.putInt("Window",window);
        double speed=Math.min(48,3.6+age*1.8);
        speed=Math.min(speed,Math.max(.8,remaining*.20));
        double vx=Math.clamp((n.getDouble("X")-target.getX())*.06,-1.2,1.2);
        double vz=Math.clamp((n.getDouble("Z")-target.getZ())*.06,-1.2,1.2);
        thrust(target,new Vec3(vx,Math.min(speed,Math.max(.8,remaining)),vz));
        if(age%3==0)level.sendParticles(ParticleTypes.CHERRY_LEAVES,target.getX(),target.getY(),target.getZ(),3,.35,.2,.35,.03);
    }
}
