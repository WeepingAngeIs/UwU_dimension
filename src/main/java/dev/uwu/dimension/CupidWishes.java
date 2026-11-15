package dev.uwu.dimension;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** A wish has two selected witnesses, one archer, and at most one independently created creature. */
final class CupidWishes {
    private record Wish(ResourceKey<Level> world, UUID first, UUID second, long due) {}
    private static final Map<UUID,Wish> wishes = new HashMap<>();
    private static final String REST = "uwudimension.wish_rest_until";
    private static final TagKey<EntityType<?>> EXCLUDED = TagKey.create(Registries.ENTITY_TYPE, UwUDimension.id("wish_excluded"));

    static void register() {
        NeoForge.EVENT_BUS.addListener(CupidWishes::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> wishes.clear());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) -> {
            UUID id=e.getEntity().getUUID();
            wishes.entrySet().removeIf(w -> w.getKey().equals(id) || w.getValue().first.equals(id) || id.equals(w.getValue().second));
        });
    }
    private static void tell(ServerPlayer p,String message) { p.displayClientMessage(Component.literal(message),true); }
    static boolean enabled(ServerPlayer p) {
        return AcquisitionPolicy.CUPID_WISHES.get() && !p.isSpectator()
            && p.hasPermissions(AcquisitionPolicy.CUPID_WISH_PERMISSION.get());
    }
    static void aim(ServerPlayer p) {
        Vec3 eye=p.getEyePosition(), end=eye.add(p.getLookAngle().scale(24));
        Vec3 wall=p.serverLevel().clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getLocation();
        EntityHitResult hit=ProjectileUtil.getEntityHitResult(p,eye,wall,new AABB(eye,wall).inflate(1),
            e->e instanceof LivingEntity && e!=p && e.isPickable(),eye.distanceToSqr(wall));
        select(p,hit==null?null:(LivingEntity)hit.getEntity());
    }
    static void select(ServerPlayer p, LivingEntity target) {
        UUID id=p.getUUID();
        if(target==null) { wishes.remove(id); tell(p,"Cupid wish cleared."); return; }
        if(!enabled(p)) { wishes.remove(id); tell(p,"Cupid wishes are unavailable to you on this server."); return; }
        if(p.getPersistentData().getLong("uwudimension.wish_archer_until")>p.level().getGameTime()) {
            tell(p,"Let the last wish settle first."); return;
        }
        if(!eligible(p,target)) { tell(p,"Choose an available adult you charmed, or a charmed player who accepts wishes."); return; }
        ServerLevel level=p.serverLevel();
        long now=level.getGameTime();
        Wish w=wishes.get(id);
        if(w!=null && w.first.equals(target.getUUID())) { wishes.remove(id); tell(p,"Cupid wish cleared."); return; }
        if(w!=null && w.second!=null) { tell(p,"Your wish is forming. Crouch-click empty air to cancel."); return; }
        if(w==null || w.due<=now || w.world!=level.dimension()) {
            wishes.put(id,new Wish(level.dimension(),target.getUUID(),null,now+200));
            tell(p,"First wish target: "+target.getName().getString()+". Crouch-click a second charmed target.");
            return;
        }
        Entity first=level.getEntity(w.first);
        if(!(first instanceof LivingEntity a) || !pairAllowed(p,a,target)) {
            tell(p,"Choose two nearby eligible targets, including at least one creature."); return;
        }
        if(wishes.values().stream().anyMatch(other->other.second!=null && (includes(other,a) || includes(other,target)))) {
            tell(p,"One of these targets is already making a wish."); return;
        }
        if(NeoForge.EVENT_BUS.post(new CupidPairEvent(p,a,target)).isCanceled()) { tell(p,"This wish is protected.");return; }
        wishes.put(id,new Wish(level.dimension(),a.getUUID(),target.getUUID(),now+20));
        hearts(level,a); hearts(level,target);
        tell(p,"Two hearts answered. A little companion may appear.");
    }
    private static boolean includes(Wish w,LivingEntity e) { return w.first.equals(e.getUUID()) || e.getUUID().equals(w.second); }
    private static void hearts(ServerLevel level,LivingEntity e) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,e.getX(),e.getY()+e.getBbHeight()+.12,e.getZ(),4,.23,.10,.23,.01);
    }
    private static boolean eligible(ServerPlayer p,LivingEntity e) {
        if(e==p || !(e instanceof Mob || e instanceof ServerPlayer) || !e.isAlive() || e.isRemoved()
            || e.level()!=p.level() || e.isBaby() || e.getType().is(EXCLUDED) || p.distanceToSqr(e)>24*24) return false;
        if(e instanceof AgeableMob age && age.getAge()!=0) return false;
        if(e instanceof Animal animal && animal.isInLove()) return false;
        if(e instanceof OwnableEntity pet && pet.getOwnerUUID()!=null && !p.getUUID().equals(pet.getOwnerUUID()))return false;
        if(e instanceof ServerPlayer witness && (!AcquisitionPolicy.CUPID_PLAYER_WITNESSES.get()
            || !p.canHarmPlayer(witness) || witness.isSpectator() || witness.isCreative()
            || ReachLife.preference(witness,"cupid_witness",0)==0))return false;
        var data=e.getPersistentData();
        long now=e.level().getGameTime();
        return data.getBoolean("uwu.pastel_cupid") && data.hasUUID(HeartMagic.OWNER) && p.getUUID().equals(data.getUUID(HeartMagic.OWNER))
            && data.getLong(HeartMagic.END)>now && data.getLong(REST)<=now && protectedAccess(p,e.blockPosition());
    }
    private static boolean pairAllowed(ServerPlayer p,LivingEntity a,LivingEntity b) {
        int radius=AcquisitionPolicy.CUPID_WISH_RADIUS.get();
        return a!=b && !(a instanceof ServerPlayer && b instanceof ServerPlayer)
            && eligible(p,a) && eligible(p,b) && a.distanceToSqr(b)<=radius*radius;
    }
    private static void tick(ServerTickEvent.Post evt) {
        if(wishes.isEmpty()) return;
        var server=evt.getServer();
        for(var entry:java.util.List.copyOf(wishes.entrySet())) {
            Wish w=entry.getValue();
            ServerPlayer p=server.getPlayerList().getPlayer(entry.getKey());
            if(p==null || !p.isAlive() || p.level().dimension()!=w.world || !enabled(p)) { wishes.remove(entry.getKey());continue; }
            if(p.serverLevel().getGameTime()<w.due)continue;
            if(wishes.remove(entry.getKey(),w)) {
                if(w.second==null) tell(p,"Your Cupid wish selection expired.");
                else finish(p,w);
            }
        }
    }
    private static void finish(ServerPlayer p,Wish w) {
        ServerLevel level=p.serverLevel();
        Entity first=level.getEntity(w.first), second=level.getEntity(w.second);
        if(!p.getMainHandItem().is(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get()) ||
           !(first instanceof LivingEntity a) || !(second instanceof LivingEntity b) || !pairAllowed(p,a,b) ||
           NeoForge.EVENT_BUS.post(new CupidPairEvent(p,a,b)).isCanceled()) {
            tell(p,"The wish faded before both hearts were ready."); return;
        }
        boolean swap=level.random.nextBoolean();
        LivingEntity primary=swap?b:a, other=swap?a:b;
        AgeableMob child=offspring(level,primary,other);
        if(child==null){LivingEntity t=primary;primary=other;other=t;child=offspring(level,primary,other);}
        if(child==null){tell(p,"Neither creature can summon a baby of its kind.");return;}
        if(a instanceof Mob m1 && b instanceof Mob m2) {
            var birth=NeoForge.EVENT_BUS.post(new BabyEntitySpawnEvent(m1,m2,child));
            if(birth.isCanceled() || !validChild(birth.getChild(),primary,other)) {tell(p,"This birth was declined.");return;}
            child=birth.getChild();
        }
        child.setBaby(true);
        if(!child.isBaby() || !place(level,p,child,primary)){tell(p,"There is no safe space for this companion.");return;}
        // This runs on the server thread. The ordinary entity-join event can still veto insertion.
        if(!level.addFreshEntity(child)){tell(p,"This area did not allow the companion to appear.");return;}
        long until=level.getGameTime()+6000;
        for(LivingEntity e:new LivingEntity[]{a,b}) {
            e.getPersistentData().putLong(REST,until);
            if(e instanceof AgeableMob parent) parent.setAge(6000);
        }
        p.getPersistentData().putLong("uwudimension.wish_archer_until",level.getGameTime()+60);
        tell(p,"Your wish welcomed "+child.getName().getString()+".");
    }
    private static boolean validChild(AgeableMob child,LivingEntity parent,LivingEntity other) {
        return child!=null && child!=parent && child!=other && !child.isAddedToLevel() && !child.isRemoved()
            && child.level()==parent.level() && child.getType()==parent.getType();
    }
    private static AgeableMob offspring(ServerLevel level,LivingEntity source,LivingEntity second) {
        if(!(source instanceof AgeableMob parent))return null;
        AgeableMob partner=second instanceof AgeableMob b && b.getType()==parent.getType()?b:parent;
        try {
            AgeableMob child=parent.getBreedOffspring(level,partner);
            return validChild(child,parent,second)?child:null;
        } catch(RuntimeException ex) {
            LogUtils.getLogger().warn("Cupid offspring factory for {} declined a wish: {}",parent.getType(),ex.toString());
            return null;
        }
    }
    private static boolean place(ServerLevel level,ServerPlayer p,AgeableMob child, LivingEntity parent) {
        for(var d:new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1}}) {
            Vec3 at=parent.position().add(d[0],0,d[1]); BlockPos pos=BlockPos.containing(at);
            if(!level.hasChunkAt(pos) || !protectedAccess(p,pos) || !level.getWorldBorder().isWithinBounds(pos))continue;
            child.moveTo(at.x,at.y,at.z,parent.getYRot(),0);
            if(!level.getBlockCollisions(child,child.getBoundingBox()).iterator().hasNext() &&
                !level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE) && !level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA))return true;
        }
        return false;
    }
    private static boolean protectedAccess(ServerPlayer p, BlockPos pos) {
        var level=p.serverLevel();
        if(!level.mayInteract(p,pos))return false;
        if(!ModList.get().isLoaded("openpartiesandclaims"))return true;
        return OpenPac.allowed(p,pos);
    }
    private static final class OpenPac {
        private static Method api,protection,access;
        private static boolean attempted;
        static boolean allowed(ServerPlayer p,BlockPos pos) {
            try {
                if(!attempted){
                    attempted=true;
                    Class<?> server=Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
                    api=server.getMethod("get",MinecraftServer.class); protection=server.getMethod("getChunkProtection");
                    access=protection.getReturnType().getMethod("hasChunkAccess",UUID.class,ResourceLocation.class,int.class,int.class);
                }
                if(access==null)return false;
                Object rights=protection.invoke(api.invoke(null,p.server));
                return Boolean.TRUE.equals(access.invoke(rights,p.getUUID(),p.level().dimension().location(),pos.getX()>>4,pos.getZ()>>4));
            } catch(ReflectiveOperationException | LinkageError ex) { return false; }
        }
    }
}

