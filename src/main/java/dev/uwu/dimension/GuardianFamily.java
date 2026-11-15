package dev.uwu.dimension;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/** Entity-owned readiness, ancestry and growth. No pending spawn survives an unload. */
final class GuardianFamily {
    static final TagKey<Item> FOOD=TagKey.create(Registries.ITEM,UwUDimension.id("owl_guardian_food"));
    static final double COMBAT_FRACTION=.30D;
    private static final ResourceLocation YOUNG=UwUDimension.id("owl_juvenile");
    private static final AttributeModifier JUVENILE_STATS=new AttributeModifier(YOUNG,COMBAT_FRACTION-1,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final int[][] OFFSETS={{0,0},{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1},
            {2,0},{-2,0},{0,2},{0,-2}};
    int growth;
    long restUntil,readyUntil;
    boolean offspring;
    @Nullable UUID fedBy,feedToken,mother,father;
    private boolean busy;

    InteractionResult feed(BlossomWarden self,ServerPlayer player,InteractionHand hand) {
        ServerLevel level=player.serverLevel();
        ItemStack held=player.getItemInHand(hand);
        if(!authorized(player,self)||!player.canInteractWithEntity(self,1.0)||!held.is(FOOD))return notice(player,"denied");
        long now=level.getGameTime();
        if(self.isBaby())return notice(player,"young");
        if(busy || now<restUntil)return notice(player,"rest");
        if(readyUntil>now)return notice(player,"ready");
        var candidates=partners(self,level,now);
        if(!candidates.isEmpty()){
            for(var mate:candidates){
                var other=feeder(level,mate);
                if(other==null||!canPair(self,player,mate,other,now,held))continue;
                if(!birth(self,player,mate,other,held))return notice(player,"blocked");
                held.consume(1,player);
                return notice(player,"born");
            }
            return notice(player,"unavailable");
        }
        if(!capacity(level,self.position()))return notice(player,"space");
        fedBy=player.getUUID();
        feedToken=UUID.randomUUID();
        readyUntil=now+AcquisitionPolicy.GUARDIAN_READY.get();
        held.consume(1,player);
        hearts(level,self,4);
        return notice(player,"fed");
    }

    private static InteractionResult notice(ServerPlayer p,String kind) {
        p.displayClientMessage(Component.translatable("guardian.uwudimension.family."+kind),true);
        return InteractionResult.CONSUME;
    }
    private static boolean authorized(ServerPlayer p,BlossomWarden guardian) {
        if(!AcquisitionPolicy.GUARDIAN_FAMILY.get() || !p.isAlive() || p.isSpectator()
                || !p.hasPermissions(AcquisitionPolicy.GUARDIAN_PERMISSION.get()) || !guardian.isAlive()
                || guardian.isRemoved() || !guardian.isAddedToLevel() || p.level()!=guardian.level())return false;
        if(guardian.getLeashHolder() instanceof Player holder && !holder.getUUID().equals(p.getUUID()))return false;
        var data=guardian.getPersistentData();
        if(data.hasUUID(HeartMagic.OWNER) && data.getLong(HeartMagic.END)>guardian.level().getGameTime()
                && !data.getUUID(HeartMagic.OWNER).equals(p.getUUID()))return false;
        return SakuraAccess.mayInteract(p,guardian.blockPosition());
    }
    @Nullable private static ServerPlayer feeder(ServerLevel level,BlossomWarden guard) {
        var id=guard.family().fedBy;
        return id!=null && level.getPlayerByUUID(id) instanceof ServerPlayer p?p:null;
    }
    private static List<BlossomWarden> partners(BlossomWarden self,ServerLevel level,long now) {
        int r=AcquisitionPolicy.GUARDIAN_PAIR_RADIUS.get();
        return level.getEntitiesOfClass(BlossomWarden.class,self.getBoundingBox().inflate(r),
                g->g!=self && g.isAlive() && !g.isBaby() && g.female()!=self.female()
                        && !g.family().busy && g.family().readyUntil>now && g.family().restUntil<=now
                        && self.distanceToSqr(g)<=r*r)
                .stream().sorted(Comparator.comparingDouble((BlossomWarden g)->self.distanceToSqr(g))
                        .thenComparing(BlossomWarden::getUUID)).toList();
    }

    private static boolean capacity(ServerLevel level,Vec3 center) {
        int r=AcquisitionPolicy.GUARDIAN_POPULATION_RADIUS.get();
        AABB bounds=new AABB(center,center).inflate(r);
        int x1=net.minecraft.util.Mth.floor(bounds.minX)>>4,x2=net.minecraft.util.Mth.floor(bounds.maxX)>>4;
        int z1=net.minecraft.util.Mth.floor(bounds.minZ)>>4,z2=net.minecraft.util.Mth.floor(bounds.maxZ)>>4;
        for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++){
            double dx=center.x-Math.clamp(center.x,x*16.0,(x+1)*16.0);
            double dz=center.z-Math.clamp(center.z,z*16.0,(z+1)*16.0);
            if(dx*dx+dz*dz<=r*r && !level.areEntitiesLoaded(ChunkPos.asLong(x,z)))return false;
        }
        return level.getEntitiesOfClass(BlossomWarden.class,bounds,g->g.isAlive() && g.distanceToSqr(center)<=r*r).size()
                <AcquisitionPolicy.GUARDIAN_POPULATION_CAP.get();
    }

    private String pairingKey(UUID parent,boolean receptive) {
        // Older saved receptive guardians only recorded their readiness deadline.
        String token=receptive?(feedToken==null?Long.toString(readyUntil):feedToken.toString()):"pending";
        return parent+"/"+token;
    }

    /** Both the immediate second interaction and two separately fed adults approaching
     * one another converge here, serially on the server thread. */
    private static boolean birth(BlossomWarden a,ServerPlayer pa,BlossomWarden b,ServerPlayer pb,@Nullable ItemStack pending) {
        ServerLevel level=pa.serverLevel();
        long now=level.getGameTime();
        var first=a.family();var second=b.family();
        if(first.busy||second.busy||!canPair(a,pa,b,pb,now,pending))return false;
        first.busy=true;second.busy=true;
        try {
            var child=People.BLOSSOM_WARDEN.get().create(level);
            if(child==null)return false;
            // A replay of the same two feedings addresses the same child. Neither a second
            // packet nor a partially repeated save cycle can materialize another UUID.
            String ta=first.pairingKey(a.getUUID(),pending==null),tb=second.pairingKey(b.getUUID(),true);
            String pair=ta.compareTo(tb)<0?ta+"|"+tb:tb+"|"+ta;
            child.setUUID(UUID.nameUUIDFromBytes(("uwudimension/owl/"+pair).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            child.setFemale(a.getRandom().nextBoolean());
            var f=child.family();
            f.offspring=true;
            f.mother=(a.female()?a:b).getUUID();
            f.father=(a.female()?b:a).getUUID();
            f.growth=AcquisitionPolicy.GUARDIAN_GROWTH.get();
            f.applyAge(child);
            child.setHealth(child.getMaxHealth());
            Vec3 mid=a.position().add(b.position()).scale(.5);
            BlockPos base=BlockPos.containing(mid);
            for(int dy:new int[]{0,1,-1})for(int[] d:OFFSETS){
                BlockPos at=base.offset(d[0],dy,d[1]);
                child.moveTo(Vec3.atBottomCenterOf(at));
                if(!safe(level,child,pa,pb))continue;
                if(!capacity(level,child.position()))continue;
                if(NeoForge.EVENT_BUS.post(new OwlGuardianBirthEvent(a,b,child,pa,pb,at)).isCanceled())return false;
                if(!canPair(a,pa,b,pb,now,pending) || child.isRemoved() || child.isAddedToLevel() ||
                        !child.isBaby() || !child.blockPosition().equals(at) || !safe(level,child,pa,pb)
                        || !capacity(level,child.position()) || !level.addFreshEntity(child))return false;
                long rest=now+AcquisitionPolicy.GUARDIAN_COOLDOWN.get();
                first.finish(rest);second.finish(rest);
                hearts(level,a,5);hearts(level,b,5);hearts(level,child,8);
                return true;
            }
            return false;
        } finally {first.busy=false;second.busy=false;}
    }
    private static boolean canPair(BlossomWarden a,ServerPlayer pa,BlossomWarden b,ServerPlayer pb,long now,@Nullable ItemStack food) {
        int r=AcquisitionPolicy.GUARDIAN_PAIR_RADIUS.get();
        return a!=b && a.female()!=b.female() && !a.isBaby() && !b.isBaby() && a.distanceToSqr(b)<=r*r
                && a.family().restUntil<=now && b.family().restUntil<=now && b.family().readyUntil>now && pb.getUUID().equals(b.family().fedBy)
                && (food==null?a.family().readyUntil>now && pa.getUUID().equals(a.family().fedBy):food.is(FOOD) && !food.isEmpty() && (pa.getMainHandItem()==food||pa.getOffhandItem()==food))
                && authorized(pa,a) && authorized(pb,b) && SakuraAccess.mayInteract(pa,b.blockPosition())
                && SakuraAccess.mayInteract(pb,a.blockPosition());
    }
    private static boolean safe(ServerLevel level,BlossomWarden child,ServerPlayer a,ServerPlayer b) {
        AABB box=child.getBoundingBox();
        var low=BlockPos.containing(box.minX,box.minY-.125,box.minZ);
        var high=BlockPos.containing(box.maxX,box.maxY,box.maxZ);
        if(level.isOutsideBuildHeight(low)||level.isOutsideBuildHeight(high)||!level.getWorldBorder().isWithinBounds(box)
                || !level.hasChunksAt(low,high))return false;
        for(var part:BlockPos.betweenClosed(low,high)) {
            if(!SakuraAccess.mayInteract(a,part) || (a!=b&&!SakuraAccess.mayInteract(b,part))
                    || !level.getFluidState(part).isEmpty() || LocketLanding.harmfulBlock(level.getBlockState(part)))return false;
        }
        return level.getBlockState(child.blockPosition().below()).isFaceSturdy(level,child.blockPosition().below(),Direction.UP)
                && level.noCollision(child,box) && level.getEntities(child,box,e->e.isAlive()&&!e.isSpectator()&&e.isPickable()).isEmpty();
    }
    private void finish(long rest){readyUntil=0;fedBy=null;feedToken=null;restUntil=rest;}
    private static void hearts(ServerLevel level,BlossomWarden target,int count) {
        HeartCombatGlow.charmHearts(level,target.getX(),target.getY()+target.getBbHeight()+.12,
                target.getZ(),count,.25,.12,.25,.014);
    }
    void tick(BlossomWarden guardian,ServerLevel level) {
        if(growth>0 && --growth==0){
            float ratio=guardian.getHealth()/guardian.getMaxHealth();
            applyAge(guardian);
            guardian.setHealth(ratio*guardian.getMaxHealth());
            hearts(level,guardian,5);
        }
        if(readyUntil==0)return;
        long now=level.getGameTime();
        if(now>=readyUntil){finish(restUntil);return;}
        if(!guardian.female() || guardian.tickCount%20!=0 || !AcquisitionPolicy.GUARDIAN_FAMILY.get())return;
        var p=feeder(level,guardian);
        if(p==null || !authorized(p,guardian))return;
        for(var mate:partners(guardian,level,now)){
            var other=feeder(level,mate);
            if(other!=null && canPair(guardian,p,mate,other,now,null)){
                birth(guardian,p,mate,other,null);
                return;
            }
        }
    }

    private void applyAge(BlossomWarden g) {
        boolean young=growth>0;
        g.juvenile(young);
        var max=g.getAttribute(Attributes.MAX_HEALTH);
        ((GuardianVitality)max).uwu$guardianHealth(young);
        max.setBaseValue(young?6666*COMBAT_FRACTION:6666);
        for(var attr:List.of(Attributes.ATTACK_DAMAGE,Attributes.ARMOR,Attributes.ARMOR_TOUGHNESS,Attributes.KNOCKBACK_RESISTANCE)){
            var inst=g.getAttribute(attr);
            if(inst==null)continue;
            if(young)inst.addOrUpdateTransientModifier(JUVENILE_STATS);
            else inst.removeModifier(YOUNG);
        }
    }
    void save(CompoundTag root) {
        var tag=new CompoundTag();
        tag.putInt("GrowthTicks",growth);tag.putLong("ReadyUntil",readyUntil);tag.putLong("RestUntil",restUntil);
        tag.putBoolean("Offspring",offspring);
        if(fedBy!=null)tag.putUUID("FedBy",fedBy);
        if(feedToken!=null)tag.putUUID("FeedToken",feedToken);
        if(mother!=null)tag.putUUID("Mother",mother);
        if(father!=null)tag.putUUID("Father",father);
        root.put("OwlFamily",tag);
    }
    void read(BlossomWarden guardian,CompoundTag root) {
        var tag=root.getCompound("OwlFamily");
        growth=Math.max(0,tag.getInt("GrowthTicks"));offspring=tag.getBoolean("Offspring")||growth>0;
        if(offspring)guardian.setPersistenceRequired();
        fedBy=tag.hasUUID("FedBy")?tag.getUUID("FedBy"):null;
        feedToken=fedBy!=null && tag.hasUUID("FeedToken")?tag.getUUID("FeedToken"):null;
        readyUntil=fedBy==null?0:tag.getLong("ReadyUntil");restUntil=tag.getLong("RestUntil");
        mother=tag.hasUUID("Mother")?tag.getUUID("Mother"):null;
        father=tag.hasUUID("Father")?tag.getUUID("Father"):null;
        busy=false;
        applyAge(guardian);
        if((growth>0 || root.contains("GuardianVitalityVersion")) && root.contains("Health",Tag.TAG_ANY_NUMERIC))
            guardian.setHealth(root.getFloat("Health"));
    }
}

