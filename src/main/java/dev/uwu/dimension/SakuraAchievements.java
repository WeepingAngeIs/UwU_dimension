package dev.uwu.dimension;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

/** Event-only milestones. Minecraft owns persistence and one-time completion; no new tick scans. */
public final class SakuraAchievements {
    private SakuraAchievements(){}
    private static final Set<String> EVENTS=Set.of("garden/harvest","garden/fruit","garden/giant","garden/chakram",
        "community/contribute","community/keepsake","community/court",
        "guardians/feed","guardians/family","guardians/together","utility/hammer","utility/return",
        "cupid/charm","cupid/player","cupid/wish","cupid/player_wish","cupid/witness","cupid/command");
    private static final Set<String> REPORTED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static void register(){
        NeoForge.EVENT_BUS.addListener(SakuraAchievements::damage);
    }
    static void award(Player actor,String id){
        if(!(actor instanceof ServerPlayer player)||!player.isAlive()||player.isSpectator()||!EVENTS.contains(id))return;
        try{
            var advancement=player.server.getAdvancements().get(UwUDimension.id("activities/"+id));
            if(advancement!=null&&!player.getAdvancements().getOrStartProgress(advancement).isDone())
                player.getAdvancements().award(advancement,"done");
        }catch(RuntimeException error){
            // A datapack or another mod's award callback must not unwind a completed birth/return.
            if(REPORTED.add(id))com.mojang.logging.LogUtils.getLogger().warn("Activity advancement {} could not be awarded",id,error);
        }
    }
    static void at(ServerPlayer actor,BlockPos pos,String id){
        if(actor!=null&&actor.serverLevel().hasChunkAt(pos)&&SakuraAccess.mayInteract(actor,pos))award(actor,id);
    }
    public static void harvested(ServerPlayer player,BlockState before){
        if(before.getBlock() instanceof DimensionCropBlock crop&&crop.isMaxAge(before))award(player,"garden/harvest");
    }
    private static void damage(LivingDamageEvent.Post event){
        if(event.getEntity().level().isClientSide||event.getNewDamage()<=0||!(event.getSource().getEntity() instanceof LivingEntity minion))return;
        if(!minion.getTags().contains(HeartMagic.OWNER))return;
        var data=minion.getPersistentData();
        if(!data.hasUUID(HeartMagic.OWNER)||!data.hasUUID(CupidMinions.COMMAND_TARGET)
            ||!data.getUUID(CupidMinions.COMMAND_TARGET).equals(event.getEntity().getUUID())
            ||data.getLong(HeartMagic.END)<=minion.level().getGameTime())return;
        var owner=minion.level().getServer().getPlayerList().getPlayer(data.getUUID(HeartMagic.OWNER));
        if(owner!=null&&owner.level()==minion.level()&&event.getEntity()!=owner&&!HeartMagic.friendly(owner,event.getEntity())
            &&(!(event.getEntity() instanceof Player target)||owner.canHarmPlayer(target)))award(owner,"cupid/command");
    }
}
