package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

/** Server-authoritative ranged casts. Visual copies never become collectible items. */
public final class RangedRelics {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "uwudimension");
    public static final Supplier<EntityType<RibbonLash>> LASH = TYPES.register("ribbon_lash", () ->
        EntityType.Builder.<RibbonLash>of(RibbonLash::new, MobCategory.MISC).sized(.15F,.15F).clientTrackingRange(12).updateInterval(1).build("uwudimension:ribbon_lash"));
    public static final Supplier<EntityType<RelicFlight>> FLIGHT = TYPES.register("relic_flight", () ->
        EntityType.Builder.<RelicFlight>of(RelicFlight::new, MobCategory.MISC).sized(.4F,.4F).clientTrackingRange(12).updateInterval(1).build("uwudimension:relic_flight"));
    public static final Supplier<EntityType<RelicProjectile>> PROJECTILE = TYPES.register("relic_projectile", () ->
        EntityType.Builder.<RelicProjectile>of(RelicProjectile::new, MobCategory.MISC).sized(.25F,.25F).clientTrackingRange(12).updateInterval(1).build("uwudimension:relic_projectile"));
    static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(ScytheInput::register);
        NeoForge.EVENT_BUS.addListener(RangedRelics::companionTick);
    }
    static boolean isWhip(Arsenal.Relic r) {
        return r==Arsenal.Relic.SATIN_WHIP || r==Arsenal.Relic.FROST_WHIP || r==Arsenal.Relic.RIFT_WHIP || r==Arsenal.Relic.AXOLOTL_WHIP;
    }
    static boolean activate(ServerLevel level, Player player, Arsenal.Relic relic, InteractionHand hand) {
        if (isWhip(relic)) {
            if (relic==Arsenal.Relic.RIFT_WHIP && player.isShiftKeyDown()) return teleport(level,player);
            RibbonLash.cast(level,player,relic);
            return true;
        }
        if (relic==Arsenal.Relic.BLUE_HOUR_WAND)
            return RelicAbilities.activate(level,player,relic);
        return RelicFlight.castWeapon(level, player, relic, hand);
    }
    static boolean enemy(Player owner, LivingEntity target) {
        return target.isAlive() && !HeartMagic.friendly(owner,target)
            && (!(target instanceof Player other) || owner.canHarmPlayer(other));
    }
    static LivingEntity aim(ServerLevel level,Player player,double reach) {
        Vec3 start=player.getEyePosition(),end=player.pick(reach,1,false).getLocation();
        EntityHitResult hit=ProjectileUtil.getEntityHitResult(level,player,start,end,new AABB(start,end).inflate(1),e->e instanceof LivingEntity l && enemy(player,l));
        return hit!=null && hit.getEntity() instanceof LivingEntity l ? l:null;
    }
    static void strike(ServerLevel level, Player player, LivingEntity target, Arsenal.Relic relic) {
        if (!enemy(player,target)) return;
        if (!target.hurt(player.damageSources().playerAttack(player),relic.damage)) return;
        switch(relic) {
            case FROST_WHIP -> {
                target.setTicksFrozen(160);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,10));
            }
            case RIFT_WHIP -> {
                CompoundTag n=player.getPersistentData();n.putUUID("uwu.rift_target",target.getUUID());
                n.putLong("uwu.rift_until",level.getGameTime()+160);
                n.putString("uwu.rift_world",level.dimension().location().toString());
            }
            case AXOLOTL_WHIP -> summonPair(level,player,target);
            case SATIN_WHIP, BELLHEART_AXE, TIDELACE_TRIDENT -> {
                Vec3 pull=player.position().subtract(target.position()).multiply(1,0,1).normalize().scale(.9);
                target.push(pull.x,.18,pull.z);target.hurtMarked=true;
            }
            case LOCKKEEPER_AXE -> { Vec3 push=player.getLookAngle().scale(1.5);target.push(push.x,.3,push.z);target.hurtMarked=true; }
            case VESPER_SCYTHE -> player.heal(2);
            default -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING,100));
        }
    }
    private static boolean teleport(ServerLevel level,Player player) {
        CompoundTag n=player.getPersistentData();
        if (!n.hasUUID("uwu.rift_target") || n.getLong("uwu.rift_until")<level.getGameTime()
                || !n.getString("uwu.rift_world").equals(level.dimension().location().toString())) return false;
        if (!(level.getEntity(n.getUUID("uwu.rift_target")) instanceof LivingEntity foe) || !enemy(player,foe) || player.distanceToSqr(foe)>576) return false;
        Vec3 behind=foe.position().subtract(foe.getLookAngle().multiply(1,0,1).normalize().scale(2));
        for(int dy=0;dy<=2;dy++) {
            BlockPos pos=BlockPos.containing(behind).above(dy);
            if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.getBlockState(pos.below()).isCollisionShapeFullBlock(level,pos.below()))continue;
            Vec3 at=Vec3.atBottomCenterOf(pos);
            if(!level.getFluidState(pos).isEmpty()||!level.noCollision(player,player.getBoundingBox().move(at.subtract(player.position()))))continue;
            player.teleportTo(at.x,at.y,at.z);player.fallDistance=0;n.remove("uwu.rift_target");
            level.sendParticles(ParticleTypes.PORTAL,at.x,at.y+1,at.z,30,.5,.7,.5,.1);return true;
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("The marked foe has no safe landing nearby."),true);
        return false;
    }
    private static void summonPair(ServerLevel level,Player player,LivingEntity target) {
        // Replace only this player's prior pair; repeated casts cannot accumulate an army.
        for(Axolotl old:level.getEntitiesOfClass(Axolotl.class,player.getBoundingBox().inflate(64),a->a.getPersistentData().hasUUID("uwu.twin_owner") && a.getPersistentData().getUUID("uwu.twin_owner").equals(player.getUUID())))old.discard();
        for(int side:new int[]{-1,1}) {
            Axolotl a=EntityType.AXOLOTL.create(level);if(a==null)continue;
            a.setVariant(side<0?Axolotl.Variant.LUCY:Axolotl.Variant.BLUE);
            a.moveTo(player.getX()+side*.6,player.getY(),player.getZ(),0,0);
            CompoundTag n=a.getPersistentData();n.putUUID("uwu.twin_owner",player.getUUID());n.putUUID("uwu.twin_target",target.getUUID());n.putLong("uwu.twin_end",level.getGameTime()+600);
            a.addTag("uwu.twin");a.setPersistenceRequired();level.addFreshEntity(a);
        }
    }
    private static void companionTick(EntityTickEvent.Post e) {
        if(!(e.getEntity() instanceof Axolotl a)||!a.getTags().contains("uwu.twin")||!(a.level() instanceof ServerLevel level))return;
        CompoundTag n=a.getPersistentData();
        if(level.getGameTime()>=n.getLong("uwu.twin_end")){a.discard();return;}
        a.setAirSupply(a.getMaxAirSupply());
        if(!(level.getEntity(n.getUUID("uwu.twin_owner")) instanceof Player owner)){a.discard();return;}
        if(level.getEntity(n.getUUID("uwu.twin_target")) instanceof LivingEntity target && enemy(owner,target)) {
            a.getNavigation().moveTo(target,1.3);
            if(a.tickCount%20==0&&a.distanceToSqr(target)<6&&a.hasLineOfSight(target))target.hurt(a.damageSources().mobAttack(a),4);
        } else a.getNavigation().moveTo(owner,1.2);
    }
}
