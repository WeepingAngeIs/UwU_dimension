package dev.uwu.dimension;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Durable bounded receipts: unresolved or unloaded effects never count as completed. */
final class GlitchCompletions extends SavedData {
    private static final int HISTORY_PER_STACK = 64;
    private static final String LINKS = "uwu.glitch_effect_links";
    private static final String TRACKED = "uwu.glitch_effect_tracked";
    private static final Factory<GlitchCompletions> FACTORY = new Factory<>(GlitchCompletions::new, GlitchCompletions::read, null);
    private CompoundTag completed = new CompoundTag();
    private static GlitchCompletions get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, "uwudimension_glitch_completions");
    }
    private ListTag history(String key) {
        ListTag entries = completed.getList(key, Tag.TAG_COMPOUND);
        if (!entries.isEmpty()) return entries;
        ListTag legacy = completed.getList(key, Tag.TAG_STRING);
        if (legacy.isEmpty()) return entries;
        ListTag converted = new ListTag();
        for (Tag token : legacy) {
            try { CompoundTag entry = new CompoundTag();entry.putUUID("token",UUID.fromString(token.getAsString()));entry.putLong("until",0);converted.add(entry); }
            catch (IllegalArgumentException ignored) { }
        }
        return converted;
    }
    private CompoundTag receipt(UUID stackId, UUID token, boolean create) {
        String key=stackId.toString();ListTag history=history(key);
        for(Tag value:history){CompoundTag entry=(CompoundTag)value;if(entry.hasUUID("token")&&entry.getUUID("token").equals(token)){completed.put(key,history);return entry;}}
        if(!create)return null;
        CompoundTag entry=new CompoundTag();entry.putUUID("token",token);entry.putBoolean("open",true);
        history.add(entry);while(history.size()>HISTORY_PER_STACK)history.remove(0);
        completed.put(key,history);setDirty();return entry;
    }
    static void complete(ServerLevel level, UUID stackId, UUID token) { complete(level, stackId, token, 0); }
    static void complete(ServerLevel level, UUID stackId, UUID token, int effectTicks) {
        var data=get(level);var entry=data.receipt(stackId,token,true);
        long until=level.getServer().overworld().getGameTime()+Math.max(0,effectTicks);
        entry.putLong("until",Math.max(entry.getLong("until"),until));entry.remove("open");data.setDirty();
    }
    static boolean applyEffect(LivingEntity target,MobEffectInstance incoming){
        var prior=target.getEffect(incoming.getEffect());
        boolean covered=prior!=null && prior.getAmplifier()>=incoming.getAmplifier()
                && (prior.isInfiniteDuration() || prior.getDuration()>=incoming.getDuration());
        target.addEffect(incoming);
        // A weaker, longer effect can be queued invisibly even when addEffect
        // returns false. A stronger/longer preexisting effect fully covers it.
        return !covered && target.hasEffect(incoming.getEffect());
    }
    static void track(Entity source,LivingEntity target,String kind){track(source,target,kind,0);}
    static void track(Entity source,LivingEntity target,String kind,int ticks){
        if(!(source.level() instanceof ServerLevel level))return;var data=source.getPersistentData();
        if(!data.hasUUID("uwu.glitch_id")||!data.hasUUID("uwu.glitch_token"))return;
        track(level,data.getUUID("uwu.glitch_id"),data.getUUID("uwu.glitch_token"),target,kind,ticks);
    }
    static void track(ServerLevel level,ItemStack stack,LivingEntity target,String kind){track(level,stack,target,kind,0);}
    static void track(ServerLevel level,ItemStack stack,LivingEntity target,String kind,int ticks){
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!data.hasUUID("uwu_glitch_id")||!data.hasUUID("uwu_glitch_token"))return;
        track(level,data.getUUID("uwu_glitch_id"),data.getUUID("uwu_glitch_token"),target,kind,ticks);
    }
    private static void track(ServerLevel level,UUID stackId,UUID token,LivingEntity target,String kind,int ticks){
        var data=get(level);var entry=data.receipt(stackId,token,true);ListTag effects=entry.getList("effects",Tag.TAG_COMPOUND);
        for(Tag value:effects){CompoundTag effect=(CompoundTag)value;if(effect.hasUUID("target")&&effect.getUUID("target").equals(target.getUUID())&&effect.getString("kind").equals(kind))return;}
        CompoundTag effect=new CompoundTag();effect.putUUID("target",target.getUUID());effect.putString("kind",kind);
        if(ticks>0)effect.putInt("remaining",ticks);
        if(kind.equals("rift_mark")){
            long remaining=Math.max(0,target.getPersistentData().getLong("uwu.rift_until")-target.level().getGameTime());
            effect.putLong("absolute_until",level.getServer().overworld().getGameTime()+remaining);
        }
        effects.add(effect);entry.put("effects",effects);data.setDirty();
        target.addTag(TRACKED);
        ListTag links=target.getPersistentData().getList(LINKS,Tag.TAG_COMPOUND);
        // Do not evict live actions. Prune a link only when every effect for
        // this target has an explicit resolved receipt; unknown history stays.
        for(int i=links.size()-1;i>=0;i--){
            CompoundTag link=links.getCompound(i);
            if(!link.hasUUID("stack")||!link.hasUUID("token"))continue;
            var prior=data.receipt(link.getUUID("stack"),link.getUUID("token"),false);
            if(prior==null)continue;
            boolean seen=false,allGone=true;
            for(Tag value:prior.getList("effects",Tag.TAG_COMPOUND)){
                CompoundTag old=(CompoundTag)value;
                if(old.hasUUID("target")&&old.getUUID("target").equals(target.getUUID())){seen=true;if(!old.getBoolean("gone"))allGone=false;}
            }
            if(seen&&allGone)links.remove(i);
        }
        boolean found=false;
        for(Tag value:links){CompoundTag link=(CompoundTag)value;if(link.hasUUID("stack")&&link.getUUID("stack").equals(stackId)&&link.hasUUID("token")&&link.getUUID("token").equals(token)){found=true;break;}}
        if(!found){CompoundTag link=new CompoundTag();link.putUUID("stack",stackId);link.putUUID("token",token);links.add(link);}
        target.getPersistentData().put(LINKS,links);
    }
    static boolean completed(ServerLevel level, UUID stackId, UUID token) {
        var data=get(level);var entry=data.receipt(stackId,token,false);
        if(entry==null||entry.getBoolean("open")||level.getServer().overworld().getGameTime()<entry.getLong("until"))return false;
        for(Tag value:entry.getList("effects",Tag.TAG_COMPOUND)){
            CompoundTag effect=(CompoundTag)value;if(effect.getBoolean("gone"))continue;if(!effect.hasUUID("target"))return false;
            // The Rift mark is explicitly based on absolute game time, not an
            // entity-ticked potion; it expires even if its owner logged out.
            if(effect.getString("kind").equals("rift_mark") && effect.contains("absolute_until")
                    && level.getServer().overworld().getGameTime()>=effect.getLong("absolute_until")){effect.putBoolean("gone",true);data.setDirty();continue;}
            Entity found=null;
            for(ServerLevel world:level.getServer().getAllLevels()){
                Entity candidate=world.getEntity(effect.getUUID("target"));
                if(candidate instanceof LivingEntity living && linked(living,stackId,token)){found=candidate;break;}
            }
            // An absent UUID might merely be in an unloaded chunk or an offline player.
            if(found==null)return false;
            if(!(found instanceof LivingEntity living))return false;
            if(!living.isAlive()){effect.putBoolean("gone",true);data.setDirty();continue;}
            boolean active=active(living,effect.getString("kind"));
            if(effect.contains("remaining") && effect.getInt("remaining")<=0)active=false;
            if(active)return false;
            effect.putBoolean("gone",true);data.setDirty();
        }
        return true;
    }
    private static boolean active(LivingEntity living,String kind){
            return switch(kind){
                case "exists" -> true;
                case "glow" -> living.hasEffect(MobEffects.GLOWING);
                case "slow" -> living.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
                case "fire_resistance" -> living.hasEffect(MobEffects.FIRE_RESISTANCE);
                case "freeze" -> living.getTicksFrozen()>0;
                case "fire" -> living.isOnFire();
                case "charm" -> living.getPersistentData().hasUUID(HeartMagic.OWNER) && living.level().getGameTime()<living.getPersistentData().getLong(HeartMagic.END);
                // A counter mark can become active again if its target switches
                // back to the matched weapon before the stored expiry.
                case "counter_mark" -> living.level().getGameTime()<living.getPersistentData().getLong(CounterEffects.MARK_END)
                        && CounterArsenal.entry(living.getPersistentData().getString(CounterEffects.MARK))!=null;
                case "counter_ward" -> CounterEffects.active(living)!=null;
                case "rift_mark" -> living.getPersistentData().hasUUID("uwu.rift_target") && living.level().getGameTime()<living.getPersistentData().getLong("uwu.rift_until");
                // The delayed echo is executed on the recipient's tick, so an
                // unloaded target must not be treated as having fired it.
                case "echo" -> living.getPersistentData().hasUUID("uwu.echo_owner");
                default -> true;
            };
    }
    /** Count only ticks actually run for the recipient, including hidden-effect duration. */
    static void onEntityTick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof LivingEntity target)||!(target.level() instanceof ServerLevel level)||!target.getTags().contains(TRACKED))return;
        var data=get(level);
        for(Tag value:target.getPersistentData().getList(LINKS,Tag.TAG_COMPOUND)){
            CompoundTag link=(CompoundTag)value;if(!link.hasUUID("stack")||!link.hasUUID("token"))continue;
            var entry=data.receipt(link.getUUID("stack"),link.getUUID("token"),false);if(entry==null)continue;
            for(Tag item:entry.getList("effects",Tag.TAG_COMPOUND)){
                CompoundTag effect=(CompoundTag)item;
                if(effect.getBoolean("gone")||!effect.contains("remaining")||!effect.hasUUID("target")||!effect.getUUID("target").equals(target.getUUID()))continue;
                int remaining=effect.getInt("remaining");
                if(remaining<=0||!target.isAlive()||!active(target,effect.getString("kind"))){effect.putBoolean("gone",true);data.setDirty();continue;}
                effect.putInt("remaining",remaining-1);if(remaining<=1)effect.putBoolean("gone",true);data.setDirty();
            }
        }
    }
    private static boolean linked(LivingEntity target,UUID stackId,UUID token){
        for(Tag value:target.getPersistentData().getList(LINKS,Tag.TAG_COMPOUND)){
            CompoundTag link=(CompoundTag)value;
            if(link.hasUUID("stack")&&link.getUUID("stack").equals(stackId)&&link.hasUUID("token")&&link.getUUID("token").equals(token))return true;
        }
        return false;
    }
    /** Explicit release is authoritative even when an affected player subsequently logs out. */
    static void resolved(ServerLevel level,LivingEntity target,String kind){
        if(!target.getTags().contains(TRACKED))return;
        var data=get(level);
        for(Tag value:target.getPersistentData().getList(LINKS,Tag.TAG_COMPOUND)){
            CompoundTag link=(CompoundTag)value;if(!link.hasUUID("stack")||!link.hasUUID("token"))continue;
            var entry=data.receipt(link.getUUID("stack"),link.getUUID("token"),false);if(entry==null)continue;
            for(Tag item:entry.getList("effects",Tag.TAG_COMPOUND)){
                CompoundTag effect=(CompoundTag)item;
                if(effect.hasUUID("target")&&effect.getUUID("target").equals(target.getUUID())&&effect.getString("kind").equals(kind))effect.putBoolean("gone",true);
            }
            data.setDirty();
        }
    }
    /** Only confirmed destructive removal resolves absent recipients; chunk unload never does. */
    static void onEntityLeave(EntityLeaveLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof LivingEntity target))return;
        if(!target.getTags().contains(TRACKED))return;
        var reason=target.getRemovalReason();if(reason==null||!reason.shouldDestroy())return;
        ListTag links=target.getPersistentData().getList(LINKS,Tag.TAG_COMPOUND);if(links.isEmpty())return;
        var data=get(level);
        for(Tag value:links){CompoundTag link=(CompoundTag)value;if(!link.hasUUID("stack")||!link.hasUUID("token"))continue;
            var entry=data.receipt(link.getUUID("stack"),link.getUUID("token"),false);if(entry==null)continue;
            for(Tag item:entry.getList("effects",Tag.TAG_COMPOUND)){CompoundTag effect=(CompoundTag)item;if(effect.hasUUID("target")&&effect.getUUID("target").equals(target.getUUID()))effect.putBoolean("gone",true);}
            data.setDirty();
        }
    }
    private static GlitchCompletions read(CompoundTag tag, HolderLookup.Provider registries) {
        var data=new GlitchCompletions();data.completed=tag.getCompound("completed").copy();return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {tag.put("completed",completed.copy());return tag;}
}
