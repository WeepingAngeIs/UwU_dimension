package dev.uwu.test;

import dev.uwu.dimension.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.bundle.PacketAndPayloadAcceptor;

/** Drives normal guardian interaction and production server AI. Everything here runs in a disposable world. */
final class GuardianFamilyProof {
    record Family(BlossomWarden male,BlossomWarden girl,BlossomWarden son,BlossomWarden daughter){
        List<BlossomWarden> all(){return List.of(male,girl,son,daughter);}
    }
    static void check(boolean ok,String what){if(!ok)throw new AssertionError("Guardian family: "+what);}
    static void close(double actual,double wanted,String what){check(Math.abs(actual-wanted)<.012,what+" expected "+wanted+" got "+actual);}
    private static Item uwu(String key){return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("uwudimension",key)).orElseThrow();}

    static void floor(ServerLevel l,BlockPos at){
        for(int dx=-5;dx<=5;dx++)for(int dz=-4;dz<=5;dz++){
            l.setBlockAndUpdate(at.offset(dx,-1,dz),Blocks.CALCITE.defaultBlockState());
            for(int h=0;h<5;h++)l.setBlock(at.offset(dx,h,dz),Blocks.AIR.defaultBlockState(),2);
        }
    }
    private static BlossomWarden adult(ServerLevel l,BlockPos pos,boolean female){
        var g=People.BLOSSOM_WARDEN.get().create(l);
        var tag=g.saveWithoutId(new CompoundTag());tag.putBoolean("FemaleGuardian",female);
        tag.putBoolean("KeeperSummonedGuardian",true);g.load(tag);
        g.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        check(l.addFreshEntity(g),"adult addition");
        check(!g.isBaby()&&g.female()==female,"old adult save compatibility");
        return g;
    }
    private static int feed(ServerPlayer p,BlossomWarden g,Item food){
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(food,3));
        p.setPos(g.getX(),g.getY(),g.getZ()+2.3);
        p.interactOn(g,InteractionHand.MAIN_HAND);
        return 3-p.getMainHandItem().getCount();
    }
    private static List<BlossomWarden> near(ServerLevel l,BlockPos pos){
        return l.getEntitiesOfClass(BlossomWarden.class,new AABB(pos).inflate(8),Entity::isAlive);
    }
    private static List<BlossomWarden> young(ServerLevel l,BlockPos pos){
        return near(l,pos).stream().filter(BlossomWarden::isBaby).toList();
    }
    private static void restFinished(BlossomWarden g){
        // Move only the adult's saved clock deadlines to the past between independent pair transactions.
        var tag=g.saveWithoutId(new CompoundTag());
        var state=tag.getCompound("OwlFamily");state.putLong("ReadyUntil",0);state.putLong("RestUntil",0);state.remove("FedBy");
        tag.put("OwlFamily",state);g.load(tag);
    }

    static Family family(ServerLevel l,BlockPos at,ServerPlayer a,ServerPlayer b){
        var male=adult(l,at.west(2),false);
        var girl=adult(l,at.east(1),true);
        BlossomWarden son=null,daughter=null;
        for(int i=0;i<32 && (son==null||daughter==null||i<2);i++){
            var first=i%2==0?male:girl;var next=i%2==0?girl:male;
            restFinished(first);restFinished(next);
            var existing=young(l,at);
            // Vary both real parental random streams repeatably; sex is selected inside the production transaction.
            first.getRandom().setSeed(0x56F8B1E923L*(i+1));
            next.getRandom().setSeed(0x6A3B75DE15L*(i+2));
            check(feed(a,first,uwu("heartberry"))==(a.getAbilities().instabuild?0:1),"first crop, order "+i);
            check(first.familyReadyUntil()>l.getGameTime()&&young(l,at).size()==existing.size(),"single feed produced a child");
            int used=feed(b,next,uwu("sakura_rice"));
            var born=young(l,at).stream().filter(v->!existing.contains(v)).toList();
            check(used==(b.getAbilities().instabuild?0:1)&&born.size()==1,"second feed order "+i+" consumed="+used+" newborns="+born.size());
            check(male.familyRestUntil()>l.getGameTime()&&girl.familyRestUntil()>l.getGameTime(),"missing parental rest");
            var child=born.getFirst();
            if(child.female() && daughter==null)daughter=child;
            else if(!child.female() && son==null)son=child;
            else child.discard();
        }
        check(son!=null&&daughter!=null,"native births did not produce both sexes");
        return new Family(male,girl,son,daughter);
    }

    static void run(ServerLevel l)throws Exception{
        var pos=new BlockPos(8,l.getMaxBuildHeight()-93,8);
        floor(l,pos);
        try(var va=new ChestProof.Visitor(l,pos,"OwlGardenerA",UUID.randomUUID());
            var vb=new ChestProof.Visitor(l,pos,"OwlGardenerB",UUID.randomUUID())){
            var a=va.player;var b=vb.player;
            l.addNewPlayer(a);l.addNewPlayer(b);
            try{
                conditions(l,pos,a,b);
                var group=family(l,pos,a,b);
                int count=young(l,pos).size();
                check(feed(a,group.girl,uwu("heartberry"))==0&&feed(b,group.male,uwu("starfruit"))==0
                                && young(l,pos).size()==count,"same-tick competing clicks duplicated a birth or charged a resting parent");
                check(feed(a,group.daughter,uwu("starfruit"))==0,"offspring accepted a breeding crop");
                for(var child:List.of(group.son,group.daughter)){
                    var parent=child.female()?group.girl:group.male;
                    compare(child,parent);
                    pairing(l,child,a);pairing(l,child,b);
                    protect(l,child);
                    grow(l,child,parent);
                }
                for(var g:List.of(group.male,group.girl)){
                    var saved=g.saveWithoutId(new CompoundTag());var restored=People.BLOSSOM_WARDEN.get().create(l);restored.load(saved);
                    check(restored.familyRestUntil()==g.familyRestUntil()&&g.female()==restored.female(),"parent cooldown/sex lost on reload");
                }
                GuardianFamilyDisk.seed(l,group);
                System.out.println("OWL GUARDIAN FAMILY PASS male/girl feed orders; both baby sexes; two-player tracking; reload, growth, claims, cap, cooldown and retained hostile grab/throw");
            }finally{
                near(l,pos).forEach(Entity::discard);a.discard();b.discard();
            }
        }
    }
    private static void conditions(ServerLevel l,BlockPos at,ServerPlayer a,ServerPlayer b)throws Exception{
        var male=adult(l,at.west(),false);var second=adult(l,at.east(),false);
        for(var invalid:List.of(Items.WHEAT,uwu("sakura_rice_seeds"),uwu("heartberry_burger")))
            check(feed(a,male,invalid)==0&&male.familyReadyUntil()==0,"accepted "+invalid);
        for(String crop:List.of("heartberry","sakura_rice","starfruit","frostpetal","sweetbean_pods","glowginger","vanilla_bean")){
            check(feed(a,male,uwu(crop))==1&&male.familyReadyUntil()>l.getGameTime(),"cultivated crop "+crop);
            restFinished(male);
        }
        feed(a,male,uwu("heartberry"));feed(b,second,uwu("starfruit"));check(young(l,at).isEmpty(),"same-sex pairing produced child");
        second.discard();restFinished(male);var girl=adult(l,at.east(),true);
        var permission=AcquisitionPolicy.GUARDIAN_PERMISSION;int oldPermission=permission.get();
        try{permission.set(4);check(feed(a,male,uwu("heartberry"))==0&&male.familyReadyUntil()<=l.getGameTime(),"permission gate charged crop");}
        finally{permission.set(oldPermission);}
        if(net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims")){
            var type=Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            var api=type.getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,l.getServer());
            var getter=type.getMethod("getServerClaimsManager");var claims=getter.invoke(api);var methods=getter.getReturnType();
            var loc=l.dimension().location();int x=at.getX()>>4,z=at.getZ()>>4;
            methods.getMethod("claim",loc.getClass(),UUID.class,int.class,int.class,int.class,boolean.class).invoke(claims,loc,b.getUUID(),-1,x,z,false);
            try{check(feed(a,male,uwu("starfruit"))==0&&male.familyReadyUntil()<=l.getGameTime(),"foreign claim charged unauthorized feeder");}
            finally{methods.getMethod("unclaim",loc.getClass(),int.class,int.class).invoke(claims,loc,x,z);}
        }
        boolean creative=a.getAbilities().instabuild;a.getAbilities().instabuild=true;
        try{check(feed(a,male,uwu("heartberry"))==0&&male.familyReadyUntil()>l.getGameTime(),"creative crop/ready state");}
        finally{a.getAbilities().instabuild=creative;}
        check(feed(a,male,uwu("sakura_rice"))==0,"already-ready adult charged twice");
        var cap=new ArrayList<BlossomWarden>();
        while(near(l,at).size()<AcquisitionPolicy.GUARDIAN_POPULATION_CAP.get())cap.add(adult(l,at.offset(3,0,2),false));
        check(feed(b,girl,uwu("starfruit"))==0&&young(l,at).isEmpty(),"local guardian population cap");
        cap.forEach(Entity::discard);
        // A second feeder arrives while a birth is in flight, followed by a mod veto before insertion.
        java.util.function.Consumer<OwlGuardianBirthEvent> veto=e->{
            check(feed(a,girl,uwu("sakura_rice"))==0,"reentrant feed accepted while parents locked");
            e.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(OwlGuardianBirthEvent.class,veto);
        try{check(feed(b,girl,uwu("starfruit"))==0&&young(l,at).isEmpty(),"vetoed spawn charged or duplicated");}
        finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(veto);}
        male.discard();girl.discard();
    }
    private static double hit(ServerLevel level,BlossomWarden guardian){
        // A fresh unarmored target for each real melee hit avoids hurt immunity and lethal overkill.
        var target=EntityType.COW.create(level);target.moveTo(guardian.position().add(0,0,2));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);target.setNoAi(true);
        check(level.addFreshEntity(target),"damage sample entity");
        var critKey=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ATTRIBUTE,
                ResourceLocation.fromNamespaceAndPath("apothic_attributes","crit_chance"));
        var crit=BuiltInRegistries.ATTRIBUTE.getHolder(critKey).map(guardian::getAttribute).orElse(null);
        double previous=crit==null?0:crit.getBaseValue();
        try{
            // Compare two ordinary hits by fixing the optional pack's independent per-hit crit roll.
            if(crit!=null){crit.setBaseValue(0);close(crit.getValue(),0,"damage fixture critical chance");}
            check(guardian.doHurtTarget(target),"guardian melee rejected");return 100-target.getHealth();
        }finally{
            if(crit!=null)crit.setBaseValue(previous);
            target.discard();
        }
    }
    private static void compare(BlossomWarden child,BlossomWarden parent){
        close(child.getBbWidth(),parent.getBbWidth()*.5,"width");
        close(child.getBbHeight(),parent.getBbHeight()*.5,"height");
        close(child.getEyeHeight(),parent.getEyeHeight()*.5,"eye");
        close(child.getMaxHealth(),parent.getMaxHealth()*.30,"max health");
        close(child.getHealth(),child.getMaxHealth(),"newborn starting health");
        var strength=child.getAttributeValue(Attributes.ATTACK_DAMAGE);
        check(parent.getAttributeValue(Attributes.ATTACK_DAMAGE)>0,"adult attack attribute missing");
        close(strength,parent.getAttributeValue(Attributes.ATTACK_DAMAGE)*.30,"attack damage");
        for(var attr:List.of(Attributes.ARMOR,Attributes.ARMOR_TOUGHNESS,Attributes.KNOCKBACK_RESISTANCE))
            close(child.getAttributeValue(attr),parent.getAttributeValue(attr)*.30,"combat attribute "+attr);
        ServerLevel level=(ServerLevel)parent.level();
        close(hit(level,child),hit(level,parent)*.30,"actual unarmored melee hit");
        System.out.printf(Locale.ROOT,"OWL GUARDIAN JUVENILE MEASURE sex=%s width=%.3f height=%.3f eye=%.3f hp=%.1f damage=%.2f adultWidth=%.3f adultHeight=%.3f adultEye=%.3f adultHp=%.1f adultDamage=%.2f%n",
                child.female()?"girl":"male",child.getBbWidth(),child.getBbHeight(),child.getEyeHeight(),child.getMaxHealth(),strength,
                parent.getBbWidth(),parent.getBbHeight(),parent.getEyeHeight(),parent.getMaxHealth(),parent.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }
    private static void pairing(ServerLevel l,BlossomWarden child,ServerPlayer watcher){
        var tracker=new ServerEntity(l,child,3,true,packet->{});
        var mirror=People.BLOSSOM_WARDEN.get().create(l);
        var neutral=mirror.saveWithoutId(new CompoundTag());neutral.putBoolean("FemaleGuardian",false);mirror.load(neutral);
        var sent=new boolean[1];
        tracker.sendPairingData(watcher,new PacketAndPayloadAcceptor<>(packet->{
            if(packet instanceof ClientboundSetEntityDataPacket data){
                mirror.getEntityData().assignValues(data.packedItems());sent[0]=true;
            }
        }));
        check(sent[0]&&mirror.isBaby()&&mirror.female()==child.female(),"new observer received wrong age/sex through tracking packet");
    }
    private static void protect(ServerLevel l,BlossomWarden guard){
        var foe=EntityType.ZOMBIE.create(l);foe.setNoAi(true);foe.setNoGravity(true);
        foe.moveTo(guard.getX()+1,guard.getY(),guard.getZ(),0,0);l.addFreshEntity(foe);
        try{
            double y=foe.getY();
            // Place the threat within sight and let native target acquisition, grip and release run.
            guard.tickCount=0;
            for(int i=0;i<45&&!foe.getPersistentData().contains(GuardianFlight.STATE);i++)guard.tick();
            var arc=foe.getPersistentData().getCompound(GuardianFlight.STATE);
            check(arc.contains("Y")&&Math.abs(arc.getDouble("Y")-y-BlossomWarden.LAUNCH_HEIGHT*.3)<2,
                    "baby failed to protect with scaled native launch: "+arc);
        }finally{foe.discard();}
    }
    private static void grow(ServerLevel l,BlossomWarden child,BlossomWarden adult)throws java.io.IOException{
        var file=java.nio.file.Files.createTempFile("owl-juvenile-", ".nbt");
        CompoundTag saved;
        try{
            net.minecraft.nbt.NbtIo.writeCompressed(child.saveWithoutId(new CompoundTag()),file);
            saved=net.minecraft.nbt.NbtIo.readCompressed(file,net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        }finally{java.nio.file.Files.deleteIfExists(file);}
        var savedChild=People.BLOSSOM_WARDEN.get().create(l);savedChild.load(saved);
        check(savedChild.isBaby()&&savedChild.female()==child.female()&&savedChild.familyGrowthTicks()==child.familyGrowthTicks(),"newborn state lost on reload");
        // Advance the saved countdown to its final two loaded ticks. Production age advancement performs the transition.
        var timing=saved.getCompound("OwlFamily");timing.putInt("GrowthTicks",2);saved.put("OwlFamily",timing);
        // Keep the original family intact for independent chunk-unload and process-restart verification.
        saved.putUUID("UUID",UUID.randomUUID());savedChild.load(saved);savedChild.moveTo(child.position().add(0,0,3));
        check(l.addFreshEntity(savedChild),"load short countdown into the server world");
        try{
            savedChild.tick();check(savedChild.isBaby(),"first loaded growth tick skipped");savedChild.tick();
            check(!savedChild.isBaby()&&savedChild.female()==child.female()&&savedChild.familyGrowthTicks()==0,"wrong adult after normal growth");
            close(savedChild.getBbWidth(),adult.getBbWidth(),"grown width");close(savedChild.getBbHeight(),adult.getBbHeight(),"grown height");
            close(savedChild.getMaxHealth(),adult.getMaxHealth(),"grown health");close(savedChild.getAttributeValue(Attributes.ATTACK_DAMAGE),adult.getAttributeValue(Attributes.ATTACK_DAMAGE),"grown attack");
        }finally{savedChild.discard();}
    }
}
