package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashSet;
import java.util.Set;

public final class Bellwake extends Axolotl {
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,"uwudimension");
    static final DeferredHolder<EntityType<?>,EntityType<Bellwake>> TYPE=TYPES.register("bellwake",
            ()->EntityType.Builder.<Bellwake>of(Bellwake::new,MobCategory.AXOLOTLS).sized(.75F,.42F)
                    .clientTrackingRange(8).build("uwudimension:bellwake"));
    Bellwake(EntityType<? extends Axolotl> type,Level level){super(type,level);}
    static void register(IEventBus bus){
        TYPES.register(bus);bus.addListener(Bellwake::attributes);
    }
    private static void attributes(EntityAttributeCreationEvent event){event.put(TYPE.get(),Axolotl.createAttributes().build());}
    private static final int[][] POOLS={{42,0,115,23},{16,-19,129,22},{-26,-48,127,20}};
    static void reveal(ServerLevel level,Entity rabbit,Entity keeper){
        boolean legacy=level.dimension().location().equals(ResourceLocation.fromNamespaceAndPath("uwudimension","heartwater"));
        if(!legacy && !ReefLife.isReach(level))return;
        int rx=rabbit.getBlockX(),rz=rabbit.getBlockZ();
        int cx,cz,t;
        if(legacy){
            cx=384*Math.floorDiv(rx+192,384);cz=384*Math.floorDiv(rz+192,384);
            t=cx==0&&cz==0?0:HeartwaterFeature.Valley.mix(cx,cz,level.getSeed())&3;
        } else {
            var site=GroveSites.forChunk(level,new ChunkPos(rabbit.blockPosition()));
            if(!site.usable()||(!level.dimension().equals(ReefLife.PREVIEW)&&!GroveSites.adventure(level,site)))return;
            cx=site.x();cz=site.z();t=site.turns();
        }
        for(int i=0;i<POOLS.length;i++){
            int[] p=POOLS[i];int x=cx+switch(t){case 1->-p[2];case 2->-p[0];case 3->p[2];default->p[0];};
            int z=cz+switch(t){case 1->p[0];case 2->-p[2];case 3->-p[0];default->p[2];};
            int dx=rx-x,dz=rz-z;
            if(dx*dx+dz*dz>p[3]*p[3] || Math.abs(rabbit.getY()-p[1])>12)continue;
            long key=BlockPos.asLong(x,p[1],z);
            Memory memory=level.getDataStorage().computeIfAbsent(Memory.FACTORY,"uwudimension_bellwakes");
            if(memory.pools.contains(key))return;
            // Search from the lure toward the lake's center so the player sees the answer nearby.
            for(int step=0;step<=20;step++){
                int bx=(int)Math.round(rx+(x-rx)*step/20.),bz=(int)Math.round(rz+(z-rz)*step/20.);
                for(int j=0;j<9;j++){
                    BlockPos at=new BlockPos(bx+j%3-1,p[1],bz+j/3-1);
                    if(!level.hasChunkAt(at)||!level.getFluidState(at).is(FluidTags.WATER)||!level.getFluidState(at.below()).is(FluidTags.WATER))continue;
                    Bellwake animal=TYPE.get().create(level);if(animal==null)return;
                    animal.moveTo(at.getX()+.5,at.getY()+.35,at.getZ()+.5,0,0);
                    animal.setCustomName(Component.translatable("entity.uwudimension.bellwake"));
                    animal.setPersistenceRequired();
                    animal.addEffect(new MobEffectInstance(MobEffects.GLOWING,140,0,false,false));
                    if(level.addFreshEntity(animal)){
                        memory.pools.add(key);memory.setDirty();
                        level.sendParticles(ParticleTypes.GLOW,animal.getX(),animal.getY()+.35,animal.getZ(),32,.6,.12,.6,.045);
                        keeper.sendSystemMessage(Component.literal("A Bellwake rises to answer the thump."));
                    }
                    return;
                }
            }
        }
    }
    private static final class Memory extends SavedData {
        static final Factory<Memory> FACTORY=new Factory<>(Memory::new,Memory::load,null);
        final Set<Long> pools=new HashSet<>();
        static Memory load(CompoundTag tag,HolderLookup.Provider lookups){
            Memory m=new Memory();for(long key:tag.getLongArray("AnsweredPools"))m.pools.add(key);return m;
        }
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookups){
            tag.putLongArray("AnsweredPools",pools.stream().mapToLong(Long::longValue).toArray());return tag;
        }
    }
}
