package dev.uwu.dimension;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
/** Factory replacement keeps UUID/NBT intact before registration; deferred fallback covers direct constructors. */
public final class RealmResidents {
    public static final String ORIGIN="uwu.keeper_origin";
    private static final Set<Villager> pending=Collections.newSetFromMap(new IdentityHashMap<>());
    public static boolean applies(Level level){
        var id=level.dimension().location();
        return level instanceof ServerLevel&&id.getNamespace().equals("uwudimension")&&Set.of("heartwater","blossom_reach","sakura_reach").contains(id.getPath());
    }
    public static void register(){
        NeoForge.EVENT_BUS.addListener(RealmResidents::join);
        NeoForge.EVENT_BUS.addListener(RealmResidents::tick);
        NeoForge.EVENT_BUS.addListener(RealmResidents::converted);
    }
    private static void join(EntityJoinLevelEvent event){
        if(applies(event.getLevel())&&event.getEntity().getType()==EntityType.VILLAGER&&event.getEntity() instanceof Villager v)pending.add(v);
    }
    private static void tick(ServerTickEvent.Post event){
        for(var v:List.copyOf(pending)){
            if(v.isRemoved()||!v.isAlive()){pending.remove(v);continue;}
            if(!applies(v.level())){pending.remove(v);continue;}
            if(!(v.level() instanceof ServerLevel level)||level.getServer()!=event.getServer()||!level.hasChunkAt(v.blockPosition()))continue;
            if(level.getEntity(v.getUUID())!=v)continue;
            var keeper=People.PETAL_KEEPER.get().create(level);if(keeper==null)continue;
            var saved=new CompoundTag();v.saveWithoutId(saved);keeper.markAdaptedResident();keeper.load(saved);
            // Preserve exact live POI references; ordinary brain validation still runs afterward.
            for(var memory:List.of(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME,
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.POTENTIAL_JOB_SITE,
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.MEETING_POINT))
                keeper.getBrain().setMemory(memory,v.getBrain().getMemory(memory));
            var passengers=List.copyOf(v.getPassengers());var vehicle=v.getVehicle();
            // Keep the original until replacement is ready. Failed registration restores it for retry.
            // This is a transfer, not a death: LivingEntity.remove clears brain memories and
            // triggers removal effects, corrupting the original if registration needs retry.
            // Entity.setRemoved still performs native tracking/UUID removal and rider detachment.
            v.setRemoved(Entity.RemovalReason.DISCARDED);
            if(level.addFreshEntity(keeper)){
                if(vehicle!=null)keeper.startRiding(vehicle,true);
                passengers.forEach(p->p.startRiding(keeper,true));pending.remove(v);
            }else{
                v.revive();
                if(level.addFreshEntity(v)){
                    if(vehicle!=null)v.startRiding(vehicle,true);
                    passengers.forEach(p->p.startRiding(v,true));
                }
            }
        }
    }
    private static void converted(LivingConversionEvent.Post event){
        if(!applies(event.getEntity().level()))return;
        if(event.getEntity() instanceof Keeper keeper&&event.getOutcome() instanceof ZombieVillager zombie){
            var tag=new CompoundTag();keeper.saveWithoutId(tag);tag.putBoolean("WasDusk",keeper.getType()==People.DUSK_KEEPER.get());
            zombie.getPersistentData().put(ORIGIN,tag);
        }else if(event.getEntity() instanceof ZombieVillager zombie&&event.getOutcome() instanceof Keeper keeper&&zombie.getPersistentData().contains(ORIGIN)){
            var prior=zombie.getPersistentData().getCompound(ORIGIN);var now=new CompoundTag();keeper.saveWithoutId(now);
            for(String key:List.of("Companion","Hearth","Settled","NextGift","OfferedJobLevel","VillageCourt","SeekLegacyCourt","Inventory"))if(prior.contains(key))now.put(key,prior.get(key).copy());
            keeper.readAdditionalSaveData(now);
        }
    }
}
