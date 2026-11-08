package dev.uwu.dimension;

import net.sweenus.simplyswords.api.ability.*;

/** Uses the installed mod's published tuning hook; never rewrites its static config. */
public final class SimplyCounterHooks {
    public static void register(){
        UniqueAbilityApi.registerModifier(Arsenal.id("counter_weaves"),1000,(context,definition,tuning)->{
            CounterArsenal.Entry counter=CounterEffects.marked(context.actor());
            if(counter==null && context.target()!=null){
                var ward=CounterEffects.active(context.target());
                if(ward!=null&&CounterEffects.holds(context.actor(),ward.target()))counter=ward;
            }
            if(counter==null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(context.stack().getItem()).toString().equals(counter.target()))return UniqueAbilityObserver.NONE;
            for(var key:definition.keys())if(affected(counter.school(),key.id().getPath()))soften(tuning,key);
            return UniqueAbilityObserver.NONE;
        });
    }
    public static boolean affected(String school,String key){
        if(key.contains("cooldown")||key.contains("interval")||key.contains("delay")||key.contains("cost")||key.contains("threshold")||key.contains("lockout")||key.contains("penalty"))return false;
        return switch(school){
            case "QUENCH"->key.matches(".*(fire|heat|ignite|burn|damage|chain).*");
            case "CLEANSE"->key.matches(".*(stack|mark|fever|wound|dread|bleed|damage).*");
            case "ANCHOR"->key.matches(".*(dash|speed|distance|leap|lift|knock|height|damage).*");
            case "SEVER"->key.matches(".*(radius|range|pull|bind|orbit|damage).*");
            case "SHATTER"->key.matches(".*(barrier|guard|shield|absorption|negat|cube|damage).*");
            case "SILENCE"->key.matches(".*(projectile|count|wave|ray|damage|radius|scaling).*");
            case "REAP"->key.matches(".*(heal|siphon|regen|resur|reviv|lifesteal|absorption).*");
            case "BANISH"->key.matches(".*(summon|count|damage|lifespan|aura|buff).*");
            case "FEINT"->key.matches(".*(parry|deflect|counter|damage).*");
            default->key.contains("damage");
        };
    }
    static void interrupt(net.minecraft.server.level.ServerLevel level,net.minecraft.world.entity.LivingEntity target,CounterArsenal.Entry entry){
        if(!CounterEffects.holds(target,entry.target()))return;
        var stack=target.getMainHandItem();
        if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(entry.target()))stack=target.getOffhandItem();
        if(entry.target().equals("simplyswords:thunderbrand"))net.sweenus.simplyswords.world.ThunderbrandAbilityManager.cancelCharging(target);
        if(entry.target().equals("simplyswords:stormbringer") && target instanceof net.minecraft.server.level.ServerPlayer player){
            stack.set(net.sweenus.simplyswords.registry.ComponentTypeRegistry.PARRY.get(),net.sweenus.simplyswords.item.component.ParryComponent.DEFAULT);
            net.sweenus.simplyswords.world.StormbringerParryManager.finishUse(player,stack);
        }
        if(entry.target().equals("simplyswords:molten_edge"))net.sweenus.simplyswords.world.MoltenEdgeAbilityManager.cancelVent(target);
        if(entry.target().equals("simplyswords:shadowsting")&&target instanceof net.minecraft.server.level.ServerPlayer p)net.sweenus.simplyswords.world.ShadowstingShadowDanceManager.end(p);
        if(entry.target().equals("simplyswords:magiblade"))net.sweenus.simplyswords.world.MagibladeAbilityManager.cancelCharging(target,false);
        if(entry.target().equals("simplyswords:dawnquiver"))net.sweenus.simplyswords.world.DawnquiverAbilityManager.cancel(level,target.getUUID());
        if(entry.target().equals("simplyswords:waxweaver")||entry.target().equals("simplyswords:wickpiercer")){
            if(!net.sweenus.simplyswords.world.RevivalCooldownManager.isCoolingDown(level,target,stack))
                net.sweenus.simplyswords.world.RevivalCooldownManager.setCooldown(level,target,stack,160);
        }
        if(entry.target().equals("simplyswords:ionbound_stormscale")){
            net.sweenus.simplyswords.world.IonboundStormscaleAbilityManager.flushWielder(target);
            stack.set(net.sweenus.simplyswords.registry.ComponentTypeRegistry.ION_CUBES.get(),new net.sweenus.simplyswords.item.component.IonCubeComponent(0,0));
        }
    }
    private static <T> void soften(UniqueAbilityTuning.Builder tuning,UniqueAbilityKey<T> key){
        Object value=tuning.get(key);
        try{
            if(value instanceof Double d)tuning.set(key,key.type().cast(d*.1));
            else if(value instanceof Integer n)tuning.set(key,key.type().cast(Math.max(0,(int)(n*.1))));
            else if(value instanceof Boolean)tuning.set(key,key.type().cast(false));
        }catch(IllegalArgumentException outsideSupportedRange){
            // Some definitions require positive entity counts. Respect their published minimum.
        }
    }
}
