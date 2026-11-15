package dev.uwu.dimension;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/** Durable bounded receipts: unresolved or unloaded effects never count as completed. */
final class GlitchCompletions extends SavedData {
    private static final int HISTORY_PER_STACK = 64;
    private static final String LINKS = "uwu.glitch_effect_links";
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
    static void track(Entity source,LivingEntity target,String kind){
        if(!(source.level() instanceof ServerLevel level))return;var data=source.getPersistentData();
        if(!data.hasUUID("uwu.glitch_id")||!data.hasUUID("uwu.glitch_token"))return;
        track(level,data.getUUID("uwu.glitch_id"),data.getUUID("uwu.glitch_token"),target,kind);
    }
    static void track(ServerLevel level,ItemStack stack,LivingEntity target,String kind){
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!data.hasUUID("uwu_glitch_id")||!data.hasUUID("uwu_glitch_token"))return;
        track(level,data.getUUID("uwu_glitch_id"),data.getUUID("uwu_glitch_token"),target,kind);
    }
    private static void track(ServerLevel level,UUID stackId,UUID token,LivingEntity target,String kind){
        var data=get(level);var entry=data.receipt(stackId,token,true);ListTag effects=entry.getList("effects",Tag.TAG_COMPOUND);
        for(Tag value:effects){CompoundTag effect=(CompoundTag)value;if(effect.hasUUID("target")&&effect.getUUID("target").equals(target.getUUID())&&effect.getString("kind").equals(kind))return;}
        CompoundTag effect=new CompoundTag();effect.putUUID("target",target.getUUID());effect.putString("kind",kind);effects.add(effect);entry.put("effects",effects);data.setDirty();
        ListTag links=target.getPersistentData().getList(LINKS,Tag.TAG_COMPOUND);boolean found=false;
        for(Tag value:links){CompoundTag link=(CompoundTag)value;if(link.hasUUID("stack")&&link.getUUID("stack").equals(stackId)&&link.hasUUID("token")&&link.getUUID("token").equals(token)){found=true;break;}}
        if(!found){CompoundTag link=new CompoundTag();link.putUUID("stack",stackId);link.putUUID("token",token);links.add(link);while(links.size()>64)links.remove(0);target.getPersistentData().put(LINKS,links);}
    }
    static boolean completed(ServerLevel level, UUID stackId, UUID token) {
        var entry=get(level).receipt(stackId,token,false);
        if(entry==null||entry.getBoolean("open")||level.getServer().overworld().getGameTime()<entry.getLong("until"))return false;
        for(Tag value:entry.getList("effects",Tag.TAG_COMPOUND)){
            CompoundTag effect=(CompoundTag)value;if(effect.getBoolean("gone")||!effect.hasUUID("target"))continue;
            Entity found=null;for(ServerLevel world:level.getServer().getAllLevels()){found=world.getEntity(effect.getUUID("target"));if(found!=null)break;}
            // An absent UUID might merely be in an unloaded chunk or an offline player.
            if(found==null)return false;
            if(!(found instanceof LivingEntity living))return false;
            if(!living.isAlive())continue;
            boolean active=switch(effect.getString("kind")){
                case "exists" -> true;
                case "glow" -> living.hasEffect(MobEffects.GLOWING);
                case "slow" -> living.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
                case "freeze" -> living.getTicksFrozen()>0;
                case "fire" -> living.isOnFire();
                case "charm" -> living.getPersistentData().hasUUID(HeartMagic.OWNER) && living.level().getGameTime()<living.getPersistentData().getLong(HeartMagic.END);
                case "counter_mark" -> CounterEffects.marked(living)!=null;
                case "rift_mark" -> living.getPersistentData().hasUUID("uwu.rift_target") && living.level().getGameTime()<living.getPersistentData().getLong("uwu.rift_until");
                default -> true;
            };
            if(active)return false;
        }
        return true;
    }
    /** Only confirmed destructive removal resolves absent recipients; chunk unload never does. */
    static void onEntityLeave(EntityLeaveLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof LivingEntity target))return;
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
