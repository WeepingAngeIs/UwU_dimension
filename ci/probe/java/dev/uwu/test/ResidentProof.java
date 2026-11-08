package dev.uwu.test;
import dev.uwu.dimension.*;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
public final class ResidentProof {
    static int phase,ticks;static BlockPos plaza;static BlossomWarden guard;static Keeper resident;static Zombie threat;
    static UUID migratedId,cureId,guardId,threatId,residentId;static Vec3 start;static boolean moved,early,attacked,reload;
    static Properties checkpoint;static int rejectedReplacement;static final Path FILE=Path.of("resident-checkpoint.properties");
    static void require(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    static boolean tick(ServerLevel level)throws Exception{
        ticks++;
        if(phase==0){
            reload=Boolean.getBoolean("uwu.residentReload");
            if(reload){checkpoint=new Properties();try(var in=Files.newInputStream(FILE)){checkpoint.load(in);}plaza=BlockPos.of(Long.parseLong(checkpoint.getProperty("plaza")));}
            else plaza=BlossomVillage.first(level,false).plaza();
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setChunkForced((plaza.getX()>>4)+x,(plaza.getZ()>>4)+z,true);
            phase=1;ticks=0;return false;
        }
        if(phase==1&&ticks>=50){
            if(reload){
                guard=(BlossomWarden)level.getEntity(UUID.fromString(checkpoint.getProperty("guard")));
                threat=(Zombie)level.getEntity(UUID.fromString(checkpoint.getProperty("threat")));
                require(guard!=null&&threat!=null,"Pursuit entities lost on real server reload");
                require(threat.getUUID().equals(guard.offender()),"Saved offender lost on restart");
                migratedId=UUID.fromString(checkpoint.getProperty("resident"));
                require(level.getEntity(migratedId) instanceof Keeper,"Adapted resident lost on real chunk reload");
                auditResident((Keeper)level.getEntity(migratedId));
                require(level.getEntitiesOfClass(BlossomWarden.class,new AABB(plaza).inflate(45)).size()==1,"Duplicate village guardian after restart");
                System.out.println("REAL RELOAD PASS adapted UUID/data and active pursuit restored once");phase=5;ticks=0;return false;
            }
            residents(level);
            phase=2;ticks=0;return false;
        }
        if(phase==2&&ticks>=15){
            require(level.getEntity(migratedId) instanceof Keeper,"Direct-constructor vanilla villager was not adapted");
            auditResident((Keeper)level.getEntity(migratedId));
            require(rejectedReplacement==1,"Registration rejection/retry path was not exercised");
            require(level.getEntitiesOfClass(Villager.class,new AABB(plaza).inflate(80),v->v.getType()==EntityType.VILLAGER).isEmpty(),"True vanilla villager remained in generated UwU village");
            require(level.getEntitiesOfClass(Keeper.class,new AABB(plaza).inflate(20)).stream().anyMatch(k->k.getCustomName()!=null&&k.getCustomName().getString().equals("Cure Probe")),"Natural ticking cure did not create Keeper");
            var dusk=level.getEntitiesOfClass(Keeper.class,new AABB(plaza).inflate(25)).stream().filter(k->k.getCustomName()!=null&&k.getCustomName().getString().equals("Dusk Cure Probe")).findFirst().orElseThrow();
            require(dusk.getType()==People.DUSK_KEEPER.get(),"Cure lost Dusk faction");
            var restored=new CompoundTag();dusk.saveWithoutId(restored);require(restored.getBoolean("Settled")&&restored.hasUUID("Companion"),"Cure lost recruitment");
            require(dusk.getOffers().size()==1&&dusk.getOffers().getFirst().getResult().is(Items.APPLE),"Cure changed existing trades");
            dusk.discard(); // End the cure fixture before testing a separate, single-threat village attack.
            var guards=level.getEntitiesOfClass(BlossomWarden.class,new AABB(plaza).inflate(60));require(!guards.isEmpty(),"Generated village missing guardian");guard=guards.getFirst();
            for(var other:guards)if(other!=guard)other.discard();
            // Keep the generated houses/beds; prepare a straight, walkable plaza approach.
            for(int x=-12;x<=12;x++)for(int z=-3;z<=3;z++)for(int y=0;y<=5;y++)level.setBlockAndUpdate(plaza.offset(x,y-1,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
            guard.bindVillage(plaza);guard.moveTo(Vec3.atBottomCenterOf(plaza.offset(-10,0,0)));start=guard.position();
            resident=People.PETAL_KEEPER.get().create(level);resident.bindVillage(plaza);resident.moveTo(Vec3.atBottomCenterOf(plaza));resident.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);resident.setHealth(1000);level.addFreshEntity(resident);
            threat=zombie(level,plaza.offset(5,0,0));
            // Passive wildlife is deliberately present and must never become an offender.
            var cow=EntityType.COW.create(level);cow.moveTo(Vec3.atBottomCenterOf(plaza.offset(-8,0,2)));level.addFreshEntity(cow);
            phase=3;ticks=0;return false;
        }
        if(phase==3){
            moved|=guard.position().distanceToSqr(start)>1;
            if(threat.getUUID().equals(guard.offender())&&resident.getHealth()==1000)early=true;
            attacked|=resident.getHealth()<1000;
            if(threat.getY()>plaza.getY()+7900){
                require(moved,"Guardian never walked under normal goals");require(early,"Guardian did not acquire approaching zombie before damage");require(attacked,"Zombie never naturally attacked resident");
                System.out.println("NATURAL DEFENSE PASS proactive acquisition, actual zombie attack, walking pursuit and 8000-block toss");
                threat.discard();guard.moveTo(Vec3.atBottomCenterOf(plaza.offset(-10,0,0)));
                phase=4;ticks=0;return false;
            }
            require(ticks<1000,"Natural village defense stalled; offender="+guard.offender()+" moved="+moved+" hp="+resident.getHealth());
        }
        if(phase==4){
            if(ticks==20){
                resident.moveTo(Vec3.atBottomCenterOf(plaza.offset(5,0,0)));resident.setHealth(1000);
                for(int x=-12;x<=-8;x++)for(int z=-2;z<=2;z++)if(x==-12||x==-8||z==-2||z==2)for(int y=0;y<5;y++)level.setBlockAndUpdate(plaza.offset(x,y,z),Blocks.OBSIDIAN.defaultBlockState());
                guard.moveTo(Vec3.atBottomCenterOf(plaza.offset(-10,0,0)));threat=zombie(level,plaza.offset(8,0,0));
            }
            if(ticks==320){
                require(threat.getUUID().equals(guard.offender()),"Blocked target not pursued");require(threat.getY()<plaza.getY()+50,"Intercept happened before thirty seconds");
                var n=new CompoundTag();guard.saveWithoutId(n);require(n.getInt("PursueTicks")>=290,"Chase timer reset under blocked navigation");
                checkpoint=new Properties();checkpoint.setProperty("plaza",Long.toString(plaza.asLong()));checkpoint.setProperty("guard",guard.getUUID().toString());checkpoint.setProperty("threat",threat.getUUID().toString());checkpoint.setProperty("resident",migratedId.toString());
                try(var out=Files.newOutputStream(FILE)){checkpoint.store(out,"Hosted temporary world expectations");}
                System.out.println("RESIDENT RESTART READY");return true;
            }
        }
        if(phase==5){
            if(threat.getY()>plaza.getY()+7900){
                require(ticks<420,"Blocked pursuit did not retain elapsed timer across reload");
                require(level.getEntitiesOfClass(Villager.class,new AABB(plaza).inflate(80),v->v.getType()==EntityType.VILLAGER).isEmpty(),"Vanilla villager reappeared after reload");
                System.out.println("BLOCKED DEFENSE PASS thirty-second interception, saved pursuit, 8000-block launch");
                System.out.println("UWU RESIDENT PROTECTION PASSED");return true;
            }
            require(ticks<700,"Blocked pursuit failed after real reload");
        }
        return false;
    }
    static Zombie zombie(ServerLevel l,BlockPos p){var z=EntityType.ZOMBIE.create(l);z.moveTo(Vec3.atBottomCenterOf(p));z.setPersistenceRequired();z.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.NETHERITE_HELMET));z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);z.setHealth(1000);l.addFreshEntity(z);return z;}
    static void residents(ServerLevel level){
        for(var realm:level.getServer().getAllLevels())if(RealmResidents.applies(realm))require(EntityType.VILLAGER.create(realm) instanceof Keeper,"Factory missed UwU realm "+realm.dimension());
        require(EntityType.VILLAGER.create(level.getServer().overworld()).getType()==EntityType.VILLAGER,"Overworld villagers changed");
        var vanilla=new Villager(EntityType.VILLAGER,level);vanilla.moveTo(Vec3.atBottomCenterOf(plaza.offset(0,0,15)));vanilla.setAge(-24000);vanilla.setCustomName(Component.literal("Saved Mae"));vanilla.setHealth(12);
        vanilla.setVillagerData(vanilla.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(3));vanilla.getInventory().setItem(0,new ItemStack(Items.BREAD,17));
        var offers=new MerchantOffers();offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,3),new ItemStack(Items.APPLE,5),7,2,.05F));vanilla.getOffers().clear();vanilla.getOffers().addAll(offers);
        var bed=Blocks.PINK_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING,Direction.NORTH);
        level.setBlockAndUpdate(plaza.offset(2,-1,15),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(plaza.offset(2,-1,16),Blocks.STONE.defaultBlockState());
        level.setBlock(plaza.offset(2,0,16),bed,2);
        level.setBlock(plaza.offset(2,0,15),bed.setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD),2);
        vanilla.getBrain().setMemory(MemoryModuleType.HOME,GlobalPos.of(level.dimension(),plaza.offset(2,0,15)));
        var tag=new CompoundTag();vanilla.save(tag);migratedId=vanilla.getUUID();
        var loaded=EntityType.loadEntityRecursive(tag,level,e->e);require(loaded instanceof Keeper,"Saved vanilla NBT not adapted by native loader");auditResident((Keeper)loaded);
        require(loaded.getUUID().equals(migratedId),"Saved UUID changed");
        require(((Keeper)loaded).getBrain().getMemory(MemoryModuleType.HOME).equals(vanilla.getBrain().getMemory(MemoryModuleType.HOME)),"Immediate native load lost HOME");
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event)->{
            if(event.getEntity() instanceof Keeper k&&k.getUUID().equals(migratedId)&&rejectedReplacement==0){
                require(k.getBrain().getMemory(MemoryModuleType.HOME).equals(vanilla.getBrain().getMemory(MemoryModuleType.HOME)), "Replacement changed current HOME memory");
                rejectedReplacement++;event.setCanceled(true);
            }
        });
        require(level.addFreshEntity(vanilla),"Direct constructor fixture failed to enter world");
        var baby=((Keeper)loaded).getBreedOffspring(level,(Keeper)loaded);require(baby instanceof Keeper&&baby.getType()!=EntityType.VILLAGER,"Breeding produced vanilla entity");
        var zombie=EntityType.ZOMBIE_VILLAGER.create(level);zombie.moveTo(Vec3.atBottomCenterOf(plaza.offset(4,0,15)));zombie.setPersistenceRequired();var zn=new CompoundTag();zombie.saveWithoutId(zn);zn.putInt("ConversionTime",1);zombie.load(zn);zombie.getPersistentData().putBoolean("cure_probe",true);level.addFreshEntity(zombie);
        // Conversion does not copy arbitrary persistent flags, so identify the cure by its custom name.
        zombie.setCustomName(Component.literal("Cure Probe"));
        var duskZombie=EntityType.ZOMBIE_VILLAGER.create(level);duskZombie.moveTo(Vec3.atBottomCenterOf(plaza.offset(7,0,15)));duskZombie.setPersistenceRequired();
        var origin=new CompoundTag();origin.putBoolean("WasDusk",true);origin.putBoolean("Settled",true);origin.putUUID("Companion",UUID.randomUUID());origin.putInt("OfferedJobLevel",3);
        duskZombie.getPersistentData().put(RealmResidents.ORIGIN,origin);duskZombie.setVillagerData(vanilla.getVillagerData());duskZombie.setTradeOffers(offers.copy());
        var dz=new CompoundTag();duskZombie.saveWithoutId(dz);dz.putInt("ConversionTime",1);duskZombie.load(dz);duskZombie.setCustomName(Component.literal("Dusk Cure Probe"));level.addFreshEntity(duskZombie);
        System.out.println("RESIDENT FACTORY PASS three realms, vanilla NBT UUID/name/age/trades/inventory, Overworld unchanged and custom offspring");
    }
    static void auditResident(Keeper k){require(k.getUUID().equals(migratedId),"Resident UUID mismatch");require(k.getCustomName()!=null&&k.getCustomName().getString().equals("Saved Mae"),"Name lost");require(k.isBaby(),"Age lost");require(k.getVillagerData().getProfession()==VillagerProfession.FARMER&&k.getVillagerData().getLevel()==3,"Profession lost");require(k.getInventory().getItem(0).is(Items.BREAD)&&k.getInventory().getItem(0).getCount()==17,"Inventory lost");require(k.getOffers().size()==1&&k.getOffers().getFirst().getResult().is(Items.APPLE),"Trades changed or duplicated");}
}
