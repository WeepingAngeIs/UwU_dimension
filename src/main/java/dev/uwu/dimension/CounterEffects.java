package dev.uwu.dimension;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Wards act on the actual matching weapon owner, including attributed projectile hits. */
public final class CounterEffects {
    static final String WARD="uwu.counter_ward",UNTIL="uwu.counter_until",MARK="uwu.counter_mark",MARK_END="uwu.counter_mark_end";
    static void register(IEventBus bus){
        NeoForge.EVENT_BUS.addListener(CounterEffects::impact);
        NeoForge.EVENT_BUS.addListener(CounterEffects::tick);
        NeoForge.EVENT_BUS.addListener(CounterEffects::damage);
        NeoForge.EVENT_BUS.addListener(CounterEffects::heal);
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e)->e.enqueueWork(SimplyCounterHooks::register));
    }
    public static String description(String school){return switch(school){
        case "QUENCH"->"Quench flames and suppress the matching wielder's fire bursts.";
        case "CLEANSE"->"Wash away ailments and interrupt matching plague and mark buildup.";
        case "ANCHOR"->"A landing tether pins the matching wielder's dash and lift effects.";
        case "SEVER"->"Sever pull pressure and weaken the matching field's reach.";
        case "SHATTER"->"Break absorption with spaced prism pulses before the main impact.";
        case "SILENCE"->"Intercept incoming matching projectiles and mute their ranged signature.";
        case "REAP"->"Forbid matching healing and punish siphoning from a distance.";
        case "BANISH"->"Repel matching summoned attackers and suppress fresh summon damage.";
        case "FEINT"->"A delayed echo strikes after the first projectile; brace for the counter.";
        case "GUARD"->"Briefly brace against rapid matching melee chains and push the attacker back.";
        case "STAGGER"->"Slow the matching heavy weapon's recovery and step outside its reach.";
        default->"Create ranged separation with a strong push and a protective matching ward.";
    };}
    public static void ward(ServerLevel level,Player p,CounterArsenal.Entry e){
        var n=p.getPersistentData();n.putString(WARD,e.id());n.putLong(UNTIL,level.getGameTime()+160);
        if(e.school().equals("QUENCH")){net.sweenus.simplyswords.world.Phase5FlamewindManager.remove(level,p.getUUID());net.sweenus.simplyswords.world.FlamewindVisualManager.removeSeed(level,p);p.clearFire();p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,160));}
        if(e.school().equals("CLEANSE")||e.runic()||e.school().equals("SEVER"))cleanse(p);
    }
    public static CounterArsenal.Entry active(LivingEntity p){
        var n=p.getPersistentData();return n.getLong(UNTIL)>p.level().getGameTime()?CounterArsenal.entry(n.getString(WARD)):null;
    }
    public static boolean holds(LivingEntity target,String id){return BuiltInRegistries.ITEM.getKey(target.getMainHandItem().getItem()).toString().equals(id)||BuiltInRegistries.ITEM.getKey(target.getOffhandItem().getItem()).toString().equals(id);}
    public static CounterArsenal.Entry marked(LivingEntity target){
        var n=target.getPersistentData();if(n.getLong(MARK_END)<=target.level().getGameTime())return null;
        var e=CounterArsenal.entry(n.getString(MARK));return e!=null&&holds(target,e.target())?e:null;
    }
    public static void hit(ServerLevel level,Player owner,LivingEntity target,CounterArsenal.Entry e){
        if(e==null||!RangedRelics.enemy(owner,target))return;
        if(holds(target,e.target())){
            SimplyCounterHooks.interrupt(level,target,e);
            var n=target.getPersistentData();n.putString(MARK,e.id());n.putLong(MARK_END,level.getGameTime()+160);
            n.putDouble("uwu.anchor_x",target.getX());n.putDouble("uwu.anchor_y",target.getY());n.putDouble("uwu.anchor_z",target.getZ());
            if(e.school().equals("SHATTER"))target.setAbsorptionAmount(0);
            if(e.school().equals("REAP"))for(var fx:List.copyOf(target.getActiveEffects()))if(fx.getEffect().value().isBeneficial())target.removeEffect(fx.getEffect());
            if(e.school().equals("STAGGER")||e.school().equals("GUARD"))target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,80,2));
            if(e.school().equals("SPACE")||e.school().equals("STAGGER")){
                Vec3 push=target.position().subtract(owner.position()).multiply(1,0,1).normalize().scale(1.4);target.push(push.x,.2,push.z);target.hurtMarked=true;
            }
        }
        if(e.school().equals("FEINT")||e.school().equals("SHATTER")){
            var n=target.getPersistentData();n.putUUID("uwu.echo_owner",owner.getUUID());n.putString("uwu.echo_counter",e.id());n.putLong("uwu.echo_at",level.getGameTime()+14);
            if(e.school().equals("SHATTER"))target.hurt(owner.damageSources().playerAttack(owner),2);
        }else target.hurt(owner.damageSources().playerAttack(owner),e.damage());
    }
    private static void impact(ProjectileImpactEvent event){
        if(!(event.getProjectile() instanceof Snowball p)||!(p.level() instanceof ServerLevel level)||!(p.getOwner() instanceof Player owner)||!p.getPersistentData().contains("uwu.counter"))return;
        if(event.getRayTraceResult() instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity target)hit(level,owner,target,CounterArsenal.entry(p.getPersistentData().getString("uwu.counter")));
        p.discard();event.setCanceled(true);
    }
    static LivingEntity sourceOwner(Entity entity){
        if(entity instanceof OwnableEntity o&&o.getOwner() instanceof LivingEntity owner)return owner;
        if(entity instanceof LivingEntity l)return l;
        if(entity instanceof Projectile p&&p.getOwner() instanceof LivingEntity l)return l;
        return null;
    }
    private static void damage(LivingIncomingDamageEvent event){
        CounterArsenal.Entry ward=active(event.getEntity());if(ward==null)return;
        LivingEntity attacker=sourceOwner(event.getSource().getEntity());
        if(attacker==null)attacker=sourceOwner(event.getSource().getDirectEntity());
        if(attacker!=null&&holds(attacker,ward.target())){
            // Defensive baseline also covers previously cast attacks whose manager outlives a status cleanse.
            event.setAmount(event.getAmount()*.35F);
            if(ward.school().equals("GUARD")&&event.getEntity().getPersistentData().getLong("uwu.guard_hit")+10>event.getEntity().level().getGameTime())event.setCanceled(true);
            event.getEntity().getPersistentData().putLong("uwu.guard_hit",event.getEntity().level().getGameTime());
        }
    }
    private static void heal(LivingHealEvent event){var mark=marked(event.getEntity());if(mark!=null&&mark.school().equals("REAP"))event.setCanceled(true);}
    private static void cleanse(LivingEntity target){
        for(var fx:List.copyOf(target.getActiveEffects()))if(!fx.getEffect().value().isBeneficial())target.removeEffect(fx.getEffect());
        target.setTicksFrozen(0);
    }
    private static void tick(EntityTickEvent.Post event){
        if(!(event.getEntity().level() instanceof ServerLevel level))return;
        Entity entity=event.getEntity();
        if(entity instanceof Snowball ball&&ball.getPersistentData().contains("uwu.counter")&&ball.tickCount>60){ball.discard();return;}
        if(!(entity instanceof LivingEntity l))return;
        var n=l.getPersistentData();
        if(n.hasUUID("uwu.echo_owner")&&level.getGameTime()>=n.getLong("uwu.echo_at")){
            var e=CounterArsenal.entry(n.getString("uwu.echo_counter"));
            if(e!=null&&level.getEntity(n.getUUID("uwu.echo_owner")) instanceof Player p&&RangedRelics.enemy(p,l)&&p.distanceToSqr(l)<1024)l.hurt(p.damageSources().playerAttack(p),e.damage());
            n.remove("uwu.echo_owner");n.remove("uwu.echo_counter");n.remove("uwu.echo_at");
        }
        var mark=marked(l);
        if(mark!=null&&mark.school().equals("ANCHOR")){
            Vec3 anchor=new Vec3(n.getDouble("uwu.anchor_x"),n.getDouble("uwu.anchor_y"),n.getDouble("uwu.anchor_z"));
            if(l.position().distanceToSqr(anchor)>9 && level.noCollision(l,l.getBoundingBox().move(anchor.subtract(l.position()))))l.teleportTo(anchor.x,anchor.y,anchor.z);
            l.fallDistance=0;
        }
        var ward=active(l);if(ward==null||l.tickCount%5!=0)return;
        if(ward.school().equals("CLEANSE")||ward.school().equals("SEVER")||ward.school().equals("QUENCH")||ward.runic())cleanse(l);
        if(ward.school().equals("QUENCH"))l.clearFire();
        if(ward.school().equals("SILENCE")||ward.school().equals("BANISH")){
            if(ward.school().equals("BANISH"))for(Entity summon:level.getEntities(l,l.getBoundingBox().inflate(12),a->BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()).getNamespace().equals("simplyswords") && a instanceof OwnableEntity owned && owned.getOwner() instanceof LivingEntity own && holds(own,ward.target())))summon.discard();
            for(Projectile p:level.getEntitiesOfClass(Projectile.class,l.getBoundingBox().inflate(5),p->p.getOwner() instanceof LivingEntity own&&holds(own,ward.target())))p.discard();
        }
    }
}
