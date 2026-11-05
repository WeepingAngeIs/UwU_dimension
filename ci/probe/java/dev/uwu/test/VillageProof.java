package dev.uwu.test;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.People;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;

/** True server ticks, generated houses and POI tickets. Only time-of-day is advanced. */
public final class VillageProof {
    private static long start = -1, nightAt = -1, dayAt = -1, accusationAt;
    private static boolean work, slept, woke, stock, fed, pickup, grab, finishedThrow;
    private static final Set<UUID> sleepingResidents = new HashSet<>();
    private static final Set<UUID> emptied = new HashSet<>();
    private static final Set<UUID> emptiedJob = new HashSet<>();
    private static final Set<ChunkPos> tickets = new HashSet<>();
    private static BlossomWarden guard;
    private static Mob attacker, witness;
    private static BlockPos platform;
    private static double takeoff, startX, startZ;
    private static ItemEntity bread;
    private static Keeper collector, sheltered;
    private static Mob threat;
    private static boolean panic, sawChild;
    private static int lesson;
    private static final net.minecraft.world.level.block.Block[] STATIONS = {
        Blocks.COMPOSTER, Blocks.LECTERN, Blocks.BARREL, Blocks.LOOM, Blocks.BLAST_FURNACE,
        Blocks.SMOKER, Blocks.CARTOGRAPHY_TABLE, Blocks.BREWING_STAND, Blocks.FLETCHING_TABLE,
        Blocks.CAULDRON, Blocks.STONECUTTER, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE
    };


    public static boolean tick(ServerLevel level, BlockPos village) {
        long now = level.getGameTime();
        if (start < 0) {
            start = now;
            ChunkPos center = new ChunkPos(village);
            for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) {
                var chunk=new ChunkPos(center.x+x,center.z+z);
                if (level.setChunkForced(chunk.x,chunk.z,true)) tickets.add(chunk);
                level.getChunk(chunk.x,chunk.z);
            }
            level.getServer().overworld().setDayTime(3000);
        }
        long age=now-start;
        List<Keeper> families=level.getEntitiesOfClass(Keeper.class,new AABB(village).inflate(48),
                k->k.getType()==People.PETAL_KEEPER.get());
        var keepers=families.stream().filter(k->!k.isBaby()).toList();
        for (Keeper member : families) {
            float expected=member.isBaby()?1.00125F:1.78F;
            if (member.isSleeping() || !member.hasPose(net.minecraft.world.entity.Pose.STANDING)) continue;
            require(Math.abs(member.getBbHeight()-expected)<.015, "Standing height "+member.getBbHeight()+" expected "+expected);
            require(Math.abs(member.getEyeHeight()-(member.isBaby()?.91125F:1.62F))<.015,"Standing eyes "+member.getEyeHeight());
            if(member.isBaby()) sawChild=true;
        }
        if(age>=2 && lesson<STATIONS.length) checkLessons(level,village,age);
        if(keepers.isEmpty() && age>240) throw new AssertionError("No generated petal villagers at "+village);
        if (!keepers.isEmpty() && !fed) {
            require(keepers.size()>=2,"Generated village should have at least two residents");
            var chosen=keepers.getFirst();
            FakePlayer guest=new FakePlayer(level, new GameProfile(UUID.fromString("474723a4-02b7-4bb9-b944-26e80d6ef07c"),"Gardener"));
            guest.moveTo(chosen.getX(),chosen.getY(),chosen.getZ(),0,0);
            guest.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BREAD,3));
            chosen.setHealth(chosen.getMaxHealth()-8);
            float before=chosen.getHealth();
            chosen.mobInteract(guest,InteractionHand.MAIN_HAND);
            require(chosen.getHealth()>before && guest.getMainHandItem().getCount()==2,"Survival bread must heal and consume one");
            fed=true;
            collector=keepers.get(1);
            bread=new ItemEntity(level,collector.getX(),collector.getY()+.15,collector.getZ(),new ItemStack(Items.CARROT,6));
            bread.setNoPickUpDelay();
            level.addFreshEntity(bread);
        }
        if (fed && bread.isRemoved() && keepers.stream().anyMatch(k->k.getInventory().countItem(Items.CARROT)>=1)) pickup=true;
        if (guard == null && age>25 && !keepers.isEmpty()) {
            require(!level.getEntitiesOfClass(BlossomWarden.class,new AABB(village).inflate(48)).isEmpty(),
                    "Generated village missing guardian");
            startGuardian(level, village);
        }
        if (guard!=null && !finishedThrow) inspectGuardian(level,now);

        for (Keeper k:keepers) {
            if (emptied.add(k.getUUID())) k.getOffers().getFirst().setToOutOfStock();
            if (k.getOffers().size()>4 && emptiedJob.add(k.getUUID())) k.getOffers().get(4).setToOutOfStock();
            var brain=k.getBrain();
            var job=brain.getMemory(MemoryModuleType.JOB_SITE);
            var lastWorked=brain.getMemory(MemoryModuleType.LAST_WORKED_AT_POI);
            if (job.isPresent() && lastWorked.isPresent() && lastWorked.get()>=start &&
                    k.getVillagerData().getProfession()!=VillagerProfession.NONE && level.getPoiManager().getType(job.get().pos()).isPresent()) {
                if (!work) System.out.println("VILLAGE WORK resident="+k.getUUID()+" job="+k.getVillagerData().getProfession()+" at "+job.get().pos()+" worked="+lastWorked.get());
                work=true;
                if (emptiedJob.contains(k.getUUID()) && !k.getOffers().getFirst().isOutOfStock()
                        && k.getOffers().size()>4 && !k.getOffers().get(4).isOutOfStock()) stock=true;
            }
            if (nightAt>=0 && k.isSleeping() && k.getSleepingPos().isPresent() &&
                    level.getBlockState(k.getSleepingPos().get()).getBlock() instanceof BedBlock &&
                    brain.getMemory(MemoryModuleType.HOME).map(p->p.pos().equals(k.getSleepingPos().get())).orElse(false)) {
                if(!slept) System.out.println("VILLAGE SLEEP resident="+k.getUUID()+" in generated bed at "+k.getSleepingPos().get());
                slept=true; sleepingResidents.add(k.getUUID());
            }
            if (dayAt>=0 && sleepingResidents.contains(k.getUUID()) && !k.isSleeping()
                    && brain.getMemory(MemoryModuleType.LAST_WOKEN).orElse(-1L)>=dayAt) woke=true;
        }
        if (work && stock && nightAt<0) { nightAt=now;level.getServer().overworld().setDayTime(14000); }
        if (slept && dayAt<0) { dayAt=now;level.getServer().overworld().setDayTime(26000); }
        if (woke && !panic && !keepers.isEmpty()) {
            if (threat==null) {
                sheltered=keepers.stream().filter(k->sleepingResidents.contains(k.getUUID())).findFirst().orElse(keepers.getFirst());
                threat=EntityType.ZOMBIE.create(level);
                require(threat!=null,"Zombie factory");
                threat.setNoAi(true); threat.setInvulnerable(true); threat.setNoGravity(true);
                threat.moveTo(sheltered.getX()+.4,sheltered.getY(),sheltered.getZ(),0,0);
                level.addFreshEntity(threat);
            }
            panic=sheltered.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC);
            if (panic) {
                System.out.println("VILLAGE SHELTER resident entered vanilla PANIC after sensing zombie");
                threat.discard();
            }
        }
        if (age%400==0) {
            var plan=dev.uwu.dimension.BlossomVillage.first(level,false);
            long beds=plan.houses().stream().filter(h->level.getPoiManager().getType(plan.bedHead(h)).isPresent()).count();
            long jobs=plan.houses().stream().filter(h->level.getPoiManager().getType(plan.workstation(h)).isPresent()).count();
            System.out.println("VILLAGE FURNITURE indexed beds="+beds+" jobs="+jobs+" houses="+plan.houses().size()+" daytime="+level.getDayTime());
            System.out.println("VILLAGE OBSERVATION ticks="+age+" villagers="+keepers.size()+" fed="+fed+" picked="+pickup+
                    " worked="+work+" restocked="+stock+" slept="+slept+" woke="+woke+" panic="+panic+" launch="+finishedThrow+
                    " first="+(keepers.isEmpty()?"pending":status(keepers.getFirst())));
        }
        if (fed && pickup && work && stock && slept && woke && panic && finishedThrow && sawChild && lesson==STATIONS.length) {
            tickets.forEach(c->level.setChunkForced(c.x,c.z,false));
            System.out.println("VILLAGE LIFE PASS: generated POIs claimed, actual work, restock, bed sleep and dawn wake; consumed hand feed and world food pickup; sensed nearby zombie and entered vanilla PANIC.");
            return true;
        }
        if(age>4000) throw new AssertionError("Village schedule or pursuit timed out: "+keepers.stream().map(VillageProof::status).toList());
        return false;
    }

    private static void checkLessons(ServerLevel level, BlockPos village, long age) {
        var block=STATIONS[lesson];
        var jobType=net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(block.defaultBlockState()).orElseThrow();
        var job=net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.stream()
                .filter(p->p!=VillagerProfession.NONE && p.heldJobSite().test(jobType)).findFirst().orElseThrow();
        for (boolean dusk : new boolean[]{false,true}) {
            var pos=new BlockPos(village.getX()+(dusk?19:-19),level.getMaxBuildHeight()-110,village.getZ()+28);
            if (age%2==0) {
                if (lesson==0) for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++)
                    level.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(pos,block.defaultBlockState());
                continue;
            }
            var type=(dusk?People.DUSK_KEEPER:People.PETAL_KEEPER).get();
            var worker=type.create(level);
            var rival=type.create(level);
            require(worker!=null && rival!=null,"Lesson participants");
            worker.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5,0,0);
            rival.moveTo(worker.getX(),worker.getY(),worker.getZ(),0,0);
            FakePlayer teacher=new FakePlayer(level,new GameProfile(UUID.fromString("4bd24d02-928d-4933-a47b-f79cd85c3de5"),"Teacher"));
            teacher.moveTo(worker.getX(),worker.getY(),worker.getZ(),0,0);
            teacher.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block));
            teacher.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.EMERALD));
            worker.mobInteract(teacher,InteractionHand.MAIN_HAND);
            var site=worker.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            require(worker.getVillagerData().getProfession()==job && site.map(p->p.pos().equals(pos)).orElse(false),
                    "Could not teach "+job+" to "+type+" at "+pos+" POI="+level.getPoiManager().getType(pos));
            rival.mobInteract(teacher,InteractionHand.MAIN_HAND);
            require(rival.getVillagerData().getProfession()==VillagerProfession.NONE,"Rival stole claimed "+job+" station");
            require(teacher.getMainHandItem().getCount()==1,"Teaching consumed workstation");
            var trades=worker.getOffers();
            require(trades.size()>4 && !trades.get(4).getResult().isEmpty(),"Missing profession trades for "+job);
            worker.notifyTrade(trades.get(4));
            require(worker.getVillagerXp()>0,"Job trade did not grant experience");
            var tag=new net.minecraft.nbt.CompoundTag();
            worker.saveWithoutId(tag);
            var restored=type.create(level);
            restored.load(tag);
            require(restored.getVillagerData().getProfession()==job && restored.getVillagerXp()==worker.getVillagerXp()
                    && restored.getOffers().size()==trades.size() && restored.getOffers().get(4).getUses()==1
                    && restored.getBrain().getMemory(MemoryModuleType.JOB_SITE).equals(site),"Lost job or offers on reload for "+job);
            teacher.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block==Blocks.BARREL?Blocks.LOOM:Blocks.BARREL));
            restored.mobInteract(teacher,InteractionHand.MAIN_HAND);
            require(restored.getVillagerData().getProfession()==job,"Previously traded keeper changed job");
            worker.releasePoi(MemoryModuleType.JOB_SITE);
            level.removeBlock(pos,false);
        }
        if (age%2!=0 && ++lesson==STATIONS.length)
            System.out.println("PROFESSION PASS: both factions accepted all 13 actual stations, rejected a second claimant, retained trade XP, uses and job after save/load.");
    }

    private static String status(Keeper k) {
        return k.blockPosition()+" activity="+k.getBrain().getActiveNonCoreActivity()+
                " home="+k.getBrain().getMemory(MemoryModuleType.HOME)+" job="+k.getBrain().getMemory(MemoryModuleType.JOB_SITE)+
                " potential="+k.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE)+" work="+k.getBrain().getMemory(MemoryModuleType.LAST_WORKED_AT_POI)+
                " uses="+k.getOffers().getFirst().getUses();
    }

    private static void startGuardian(ServerLevel level,BlockPos village) {
        platform=new BlockPos(village.getX()+52,level.getMaxBuildHeight()-78,village.getZ());
        for(int dx=-3;dx<=3;dx++) for(int dz=-3;dz<=3;dz++) level.setBlockAndUpdate(platform.offset(dx,-1,dz),Blocks.STONE.defaultBlockState());
        guard=People.BLOSSOM_WARDEN.get().create(level);
        Keeper ward=People.PETAL_KEEPER.get().create(level);
        attacker=EntityType.WARDEN.create(level);
        witness=EntityType.COW.create(level);
        require(guard!=null && ward!=null && attacker!=null && witness!=null,"Arena entities");
        guard.bindVillage(platform); ward.bindVillage(platform);
        guard.moveTo(platform.getX()+.5,platform.getY(),platform.getZ()+.5,0,0);
        ward.moveTo(guard.getX()+1,guard.getY(),guard.getZ()+1,0,0);
        attacker.moveTo(guard.getX()+2,guard.getY()+45,guard.getZ(),0,0);
        witness.moveTo(guard.getX()+1,guard.getY(),guard.getZ()-1,0,0);
        ward.setNoAi(true);attacker.setNoAi(true);attacker.setNoGravity(true);attacker.setPersistenceRequired();witness.setNoAi(true);witness.setNoGravity(true);
        level.addFreshEntity(guard);level.addFreshEntity(ward);level.addFreshEntity(attacker);level.addFreshEntity(witness);
        require(guard.offender()==null,"Unprovoked guardian targeted bystander");
        require(attacker.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)>=1,"Boss-like target must resist knockback");
        ward.hurt(level.damageSources().mobAttack(attacker),3);
        require(attacker.getUUID().equals(guard.offender()),"Actual harm did not identify offender");
        takeoff=attacker.getY();startX=attacker.getX();startZ=attacker.getZ();accusationAt=level.getGameTime();
        System.out.println("GUARDIAN START: bystander ignored; actual unarmored villager hurt by knockback-immune Warden; elevation="+takeoff);
    }

    private static void inspectGuardian(ServerLevel level,long now) {
        long elapsed=now-accusationAt;
        if(elapsed<600) require(!guard.gripping() && guard.getY()<takeoff-20,"Guardian intercepted before >30 seconds");
        if(guard.gripping() && !grab) {
            require(elapsed>=600 && guard.getY()>=takeoff-4,"Guardian failed to teleport to inaccessible attacker after "+elapsed+" ticks: guardian="+guard.position()+" attacker="+attacker.position()+" starting elevation="+takeoff);
            grab=true;
        }
        if(grab && guard.offender()==null) {
            double rise=attacker.getY()-takeoff;
            double away=Math.hypot(attacker.getX()-startX,attacker.getZ()-startZ);
            require(rise>=7998 && rise<=8010 && away>=85,"Actual Warden went "+rise+" up and "+away+" across");
            require(witness.getY()<platform.getY()+3 && witness.isAlive(),"Bystander disturbed");
            System.out.println("GUARDIAN PASS: 30-second pursuit; visible grip; launch/fallback displacement "+rise+" up, "+away+" horizontal. Non-offender safe.");
            attacker.discard();witness.discard();guard.discard();finishedThrow=true;
        }
        if(elapsed>700) throw new AssertionError("Guardian did not finish pursuit: "+guard.blockPosition()+" offender="+guard.offender()+" target="+attacker.blockPosition());
    }
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}

