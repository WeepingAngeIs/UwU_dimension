package dev.uwu.dimension;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Optional, animation-only view of an already-resolved Better Combat profile. */
public final class WeaponAnimationAdapter {
    private WeaponAnimationAdapter(){}
    private static final Map<Object,Map<String,Object>> CACHE=new WeakHashMap<>();
    private static final Object NO_CHANGE=new Object();
    private static volatile boolean reported;

    public static Object adapt(ItemStack stack,Object original){
        if(stack==null||original==null)return original;
        var component=BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("bettercombat","preset_id")).orElse(null);
        if(component!=null&&stack.has(component))return original;
        String shape=shape(stack);
        if(shape==null)return original;
        boolean simply=ModList.get().isLoaded("simplyswords");
        String key=shape+":"+simply;
        synchronized(CACHE){
            var existing=CACHE.get(original);
            if(existing!=null&&existing.containsKey(key)){
                Object value=existing.get(key);return value==NO_CHANGE?original:value;
            }
            Object next=copy(original,shape,simply);
            CACHE.computeIfAbsent(original,unused->new java.util.HashMap<>()).put(key,next==original?NO_CHANGE:next);
            return next;
        }
    }
    private static String shape(ItemStack stack){
        if(stack.getItem() instanceof CounterArsenal.CounterItem item){
            String shape=item.entry.shape();
            return switch(shape){case "axe","hammer","scythe","spear","chakram","whip","wand"->shape;default->null;};
        }
        if(!(stack.getItem() instanceof Arsenal.RelicItem item))return null;
        var r=item.relic;
        if(r==Arsenal.Relic.VESPER_SCYTHE)return "scythe";
        if(r.axe())return "axe";
        if(r==Arsenal.Relic.LANTERN_SPEAR)return "spear";
        if(RangedRelics.isWhip(r))return "whip";
        if(r==Arsenal.Relic.RIBBONHARE_SCEPTER||r==Arsenal.Relic.BLUE_HOUR_WAND)return "wand";
        return null; // Cupid/Ripple bows, daggers, and Tidelace trident retain their verified profiles.
    }
    private static Object get(Object target,String method)throws ReflectiveOperationException{
        return target.getClass().getMethod(method).invoke(target);
    }
    private static Constructor<?> constructor(Class<?> type,int size)throws NoSuchMethodException{
        for(var c:type.getConstructors())if(c.getParameterCount()==size)return c;
        throw new NoSuchMethodException(type.getName()+" constructor "+size);
    }
    private static Object copy(Object original,String shape,boolean simply){
        try{
            Object attacks=get(original,"attacks");if(attacks==null)return original;
            int count=Array.getLength(attacks);if(count==0)return original;
            Class<?> attackType=attacks.getClass().getComponentType();
            var attackConstructor=constructor(attackType,11);
            Object replacements=Array.newInstance(attackType,count);
            boolean two=Boolean.TRUE.equals(get(original,"two_handed"));
            String oldPose=(String)get(original,"pose");
            String newPose=shape.equals("scythe")&&simply?"simplyswords:pose_soulrender":two?switch(shape){
                case "axe"->"bettercombat:pose_two_handed_heavy";
                case "spear"->"bettercombat:pose_two_handed_polearm";
                case "scythe"->"bettercombat:pose_two_handed_scythe";
                default->oldPose;
            }:oldPose;
            boolean changed=!java.util.Objects.equals(oldPose,newPose);
            for(int i=0;i<count;i++){
                Object a=Array.get(attacks,i);if(a==null)return original;
                Object conditions=get(a,"conditions");
                // Existing conditional/dual-wield sequences can require a particular paired animation.
                boolean conditional=conditions!=null&&Array.getLength(conditions)>0;
                String before=(String)get(a,"animation");
                String after=conditional?before:animation(shape,two,simply,i);
                if(after==null)after=before;
                changed|=!java.util.Objects.equals(before,after);
                Object clone=attackConstructor.newInstance(conditions,get(a,"hitbox"),get(a,"damageMultiplier"),
                        get(a,"movementSpeedMultiplier"),get(a,"rangeMultiplier"),get(a,"angle"),get(a,"upswing"),
                        after,get(a,"swingSound"),get(a,"impactSound"),get(a,"trailParticles"));
                Array.set(replacements,i,clone);
            }
            if(!changed)return original;
            return constructor(original.getClass(),8).newInstance(get(original,"attackRange"),get(original,"rangeBonus"),
                    newPose,get(original,"offHandPose"),get(original,"two_handed"),get(original,"category"),
                    replacements,get(original,"trailAppearance"));
        }catch(ReflectiveOperationException|RuntimeException e){
            if(!reported){reported=true;com.mojang.logging.LogUtils.getLogger().warn("Could not adapt optional Better Combat animation; original profile retained",e);}
            return original;
        }
    }
    private static String animation(String shape,boolean two,boolean simply,int index){
        boolean right=(index&1)==0;
        if(shape.equals("chakram")&&!two&&simply)return right?"simplyswords:one_handed_chakram_throw_left":"simplyswords:one_handed_throw_chakram_right";
        if(shape.equals("hammer"))return two?"bettercombat:two_handed_slam":"bettercombat:one_handed_slam";
        if(shape.equals("spear")||shape.equals("wand"))return two?"bettercombat:two_handed_stab_right":"bettercombat:one_handed_stab";
        return "bettercombat:"+(two?"two_handed":"one_handed")+"_slash_horizontal_"+(right?"right":"left");
    }
}
