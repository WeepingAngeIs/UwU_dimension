package dev.uwu.test;

import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.OwlExpression;
import dev.uwu.dimension.People;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.SpawnEggItem;

public final class OwlResidentsProof {
    public static void run(ServerLevel level) {
        var center = level.getSharedSpawnPos();
        var spot = new BlockPos(center.getX(),level.getMaxBuildHeight()-12,center.getZ());
        var spawned = new ArrayList<Entity>();
        try {
            var girls = People.guardianEgg(true);
            var boys = People.guardianEgg(false);
            check(girls.getItem()==boys.getItem() && girls.getItem()==SpawnEggItem.byId(People.BLOSSOM_WARDEN.get()),"legacy egg type lookup");
            check(girls.get(DataComponents.CUSTOM_MODEL_DATA).value()!=boys.get(DataComponents.CUSTOM_MODEL_DATA).value(),"distinct guardian egg models");
            var girl=People.BLOSSOM_WARDEN.get().spawn(level,girls,null,spot,MobSpawnType.SPAWN_EGG,false,false);
            var male=People.BLOSSOM_WARDEN.get().spawn(level,boys,null,spot.east(2),MobSpawnType.DISPENSER,false,false);
            if (girl!=null) spawned.add(girl);
            if (male!=null) spawned.add(male);
            check(girl!=null && male!=null && girl.female() && !male.female(),"actual guardian spawn egg sex");
            check(girl.getBbHeight()<male.getBbHeight() && Math.abs(girl.getBbHeight()-1.98F)<.001,"female hitbox");
            check(girl.getBbWidth()<male.getBbWidth() && girl.getEyeHeight()<girl.getBbHeight(),"female width/eyes");
            check(!male.getName().equals(girl.getName()),"variant names");
            for (BlossomWarden guard : List.of(girl,male)) {
                guard.setCustomName(Component.literal("Apple"));
                CompoundTag tag=guard.saveWithoutId(new CompoundTag());
                check(tag.getBoolean("KeeperSummonedGuardian"),"manually spawned guardian persisted");
                var loaded=People.BLOSSOM_WARDEN.get().create(level);
                loaded.load(tag);
                check(loaded.female()==guard.female() && loaded.getName().getString().equals("Apple"),"sex and custom name after load");
            }
            var eggs=List.of(People.PETAL_EGG,People.DUSK_EGG,People.PETAL_ZOMBIE_EGG,People.DUSK_ZOMBIE_EGG);
            var types=List.of(People.PETAL_KEEPER.get(),People.DUSK_KEEPER.get(),People.PETAL_ZOMBIE.get(),People.DUSK_ZOMBIE.get());
            for(int i=0;i<eggs.size();i++) {
                var egg=eggs.get(i).get();
                var type=types.get(i);
                check(SpawnEggItem.byId(type)==egg && egg.getType(egg.getDefaultInstance())==type,"keeper egg lookup "+i);
                var resident=type.spawn(level, egg.getDefaultInstance(),null, spot.east(4+2*i),MobSpawnType.SPAWN_EGG,false,false);
                check(resident!=null && resident.getType()==type,"actual keeper egg spawn "+i);
                spawned.add(resident);
            }
            int[] frames=new int[3];
            for(int tick=0;tick<300;tick++) frames[OwlExpression.frame(new UUID(0,3),tick)]++;
            check(frames[0]>240 && frames[1]>0 && frames[2]>0,"short individualized blink");
            System.out.println("OWL RESIDENTS PASS | 6 native egg stacks, stable IDs and names, shorter girl hitbox, save/load, three blink frames");
        } finally {
            spawned.forEach(Entity::discard);
        }
    }

    private static void check(boolean ok,String message) {
        if (!ok) throw new AssertionError(message);
    }
}
