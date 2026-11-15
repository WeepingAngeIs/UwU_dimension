package dev.uwu.test;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Actual server-tick custody, form behavior and completion checks in a disposable world. */
final class GlitchLifecycleProof {
    private static ServerPlayer player;
    private static Husk frozenTarget;
    private static GlitchBow item;
    private static RelicFlight flight;
    private static HeartArrow arrow;
    private static ItemStack current;
    private static long deadline;
    private static int stage, oldForm;
    private static int initialWear;
    private static Object enchantments;
    private static boolean caught, offhandWardWaited, frozenObserved;
    private static long whipStarted;

    private static void check(boolean ok,String text){if(!ok)throw new AssertionError("Glitch lifecycle: "+text);}
    private static CompoundTag data(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static ItemStack form(String id){
        int index=GlitchBow.FORMS.indexOf(id);check(index>=0,"unknown form "+id);
        var stack=new ItemStack(item);
        CustomData.update(DataComponents.CUSTOM_DATA,stack,n->{n.putInt("uwu_form",index);n.putString("fixture","preserve-me");});
        return stack;
    }
    private static ItemStack findOwned(){
        ItemStack found=ItemStack.EMPTY;int count=0;
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            var s=player.getInventory().getItem(i);
            if(s.is(item)){found=s;count+=s.getCount();}
        }
        check(count<=1,"duplicate owned Glitch stacks: "+count);
        return found;
    }
    private static void clearInventory(){player.getInventory().clearContent();player.getCooldowns().removeCooldown(item);}
    private static void startForm(String id,InteractionHand hand){
        clearInventory();current=form(id);oldForm=GlitchBow.currentIndex(current);
        player.setItemInHand(hand,current);
    }
    private static boolean stillForm(ItemStack stack){return GlitchBow.currentIndex(stack)==oldForm;}
    private static void melee(ServerLevel level,BlockPos origin)throws Exception{
        Husk mob=EntityType.HUSK.create(level);check(mob!=null,"no melee target");
        mob.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()-2.5,0,0);
        mob.setNoAi(true);mob.setNoGravity(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        mob.getAttribute(Attributes.ARMOR).setBaseValue(0);
        mob.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        mob.setHealth(200);level.addFreshEntity(mob);
        var entry=CounterArsenal.entry("counter_runic_twinblade");
        var targetItem=CounterArsenal.ITEMS.get(entry.id()).get();
        var ticker=LivingEntity.class.getDeclaredField("attackStrengthTicker");ticker.setAccessible(true);
        var key=net.minecraft.resources.ResourceKey.create(Registries.ATTRIBUTE,net.minecraft.resources.ResourceLocation.parse("apothic_attributes:crit_chance"));
        var chance=net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(key).map(player::getAttribute).orElse(null);
        double oldChance=chance==null?0:chance.getBaseValue();if(chance!=null)chance.setBaseValue(0);
        try{
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(targetItem));player.doTick();
            double expected=player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            ticker.setInt(player,100);player.setOnGround(true);player.attack(mob);
            float actualDirect=200-mob.getHealth();
            mob.setHealth(200);mob.invulnerableTime=0;mob.setDeltaMovement(Vec3.ZERO);
            var mimic=form(entry.id());player.setItemInHand(InteractionHand.MAIN_HAND,mimic);player.doTick();
            double mirrored=player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            check(Math.abs(expected-mirrored)<.001,"displayed form attack attribute differs "+expected+"/"+mirrored);
            ticker.setInt(player,100);player.setOnGround(true);player.attack(mob);
            float actualMimic=200-mob.getHealth();
            check(actualDirect>0&&Math.abs(actualDirect-actualMimic)<.1F,"actual Twin Chakram melee damage differs "+actualDirect+"/"+actualMimic);
            check(stillForm(mimic)||GlitchBow.form(mimic).equals(entry.id()),"melee changed displayed form early");
            System.out.println("GLITCH TWIN CHAKRAM MELEE PASS direct="+actualDirect+" mimic="+actualMimic+" attackAttribute="+mirrored);
        }finally{if(chance!=null)chance.setBaseValue(oldChance);mob.discard();}
    }
    private static void recovery(ServerLevel level)throws Exception{
        startForm("counter_runic_twinblade",InteractionHand.MAIN_HAND);
        current.set(DataComponents.CUSTOM_NAME,Component.literal("Saved Glitch"));
        int selected=oldForm;
        item.use(level,player,InteractionHand.MAIN_HAND);
        var launched=level.getEntitiesOfClass(RelicFlight.class,player.getBoundingBox().inflate(5),f->f.getOwner()==player&&!f.wave());
        check(launched.size()==1&&player.getMainHandItem().isEmpty(),"recovery fixture did not throw");
        var physical=launched.getFirst();
        CompoundTag ownerData=player.saveWithoutId(new CompoundTag());
        CompoundTag flightData=physical.saveWithoutId(new CompoundTag());
        UUID id=player.getUUID();
        physical.discard();player.discard();
        ServerLevel elsewhere=level.getServer().overworld();
        var recovered=CounterProof.player(elsewhere,new GameProfile(id,"GlitchLifecycle"));
        recovered.load(ownerData);recovered.setPos(0,elsewhere.getMaxBuildHeight()-20,0);
        recovered.setNoGravity(true);elsewhere.addNewPlayer(recovered);recovered.tickCount=20;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(recovered));
        check(recovered.getInventory().countItem(item)==1,"offline/dimension recovery failed or duplicated stack");
        var result=recovered.getInventory().items.stream().filter(s->s.is(item)).findFirst().orElseThrow();
        check(GlitchBow.currentIndex(result)==selected&&data(result).getBoolean("uwu_glitch_pending")
                &&result.getHoverName().getString().equals("Saved Glitch"),"recovery lost pending form or custom data");
        var stale=RangedRelics.FLIGHT.get().create(level);check(stale!=null,"no stale-flight factory");
        stale.load(flightData);stale.setOwner(recovered);stale.tick();
        check(stale.isRemoved()&&recovered.getInventory().countItem(item)==1,"late projectile duplicated a recovered stack");
        recovered.discard();
        System.out.println("GLITCH RECOVERY PASS saved owner and flight, dimension change, pending form and stale-flight deduplication");
    }
    static boolean tick(ServerLevel level)throws Exception {
        // Probe visitors are not in the server player list, whose network loop
        // normally calls doTick; drive that real inventory/cooldown path once per tick.
        if(player!=null&&player.isAlive())player.doTick();
        if(stage==0){
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setChunkForced(x,z,true);
            BlockPos base=new BlockPos(0,level.getMaxBuildHeight()-24,0);
            for(int x=-5;x<=5;x++)for(int z=-6;z<=9;z++){
                level.setBlockAndUpdate(base.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                for(int y=0;y<6;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            for(int x=-5;x<=5;x++)for(int y=0;y<6;y++)level.setBlockAndUpdate(base.offset(x,y,7),Blocks.STONE.defaultBlockState());
            player=CounterProof.player(level,new GameProfile(UUID.fromString("c719e828-f310-44fa-95d5-41fef4a9da43"),"GlitchLifecycle"));
            player.moveTo(.5,base.getY(),.5,180,0);player.setNoGravity(true);level.addNewPlayer(player);
            item=(GlitchBow)UwUDimension.GLITCH.get();deadline=level.getGameTime()+40;stage=1;return false;
        }
        if(stage==1){
            if(level.getGameTime()<deadline)return false;
            BlockPos base=player.blockPosition();melee(level,base);
            // The documented reproduction case is a physical chakram, not a bow.
            startForm("counter_runic_twinblade",InteractionHand.MAIN_HAND);
            current.setDamageValue(37);current.set(DataComponents.CUSTOM_NAME,Component.literal("Beloved Glitch"));
            current.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING),3);
            enchantments=current.get(DataComponents.ENCHANTMENTS);initialWear=current.getDamageValue();
            player.moveTo(.5,base.getY(),.5,0,0);
            item.use(level,player,InteractionHand.MAIN_HAND);
            check(CounterEffects.active(player)!=null&&CounterEffects.active(player).target().equals(CounterArsenal.entry("counter_runic_twinblade").target()),"Twin Chakram did not apply its own counter ward");
            var launched=level.getEntitiesOfClass(RelicFlight.class,player.getBoundingBox().inflate(4),f->f.getOwner()==player&&!f.wave());
            check(launched.size()==1,"Twin Chakram did not create one physical flight");flight=launched.getFirst();
            check(player.getMainHandItem().isEmpty(),"physical form remained in hand");
            check(flight.getItem().is(item)&&stillForm(flight.getItem()),"flight lost original item or displayed form");
            check(flight.saveWithoutId(new CompoundTag()).getString("counter").equals("counter_runic_twinblade"),"projectile is not the selected Twin Chakram");
            // Longer test cooldown ensures the returned item waits even if its flight is short.
            player.getCooldowns().addCooldown(item,200);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT));
            deadline=level.getGameTime()+350;stage=2;return false;
        }
        if(stage==2){
            var owned=findOwned();
            if(!flight.isRemoved()){
                check(owned.isEmpty(),"weapon duplicated before return");
                check(stillForm(flight.getItem()),"projectile changed form in flight");
            }else if(!owned.isEmpty()){
                if(!caught){
                    check(stillForm(owned)&&data(owned).getBoolean("uwu_glitch_pending"),"form advanced before cooldown after physical return");
                    check(owned.getHoverName().getString().equals("Beloved Glitch")&&enchantments.equals(owned.get(DataComponents.ENCHANTMENTS)),"returned item lost name or enchantments");
                    check(data(owned).getString("fixture").equals("preserve-me")&&owned.getDamageValue()>=initialWear,"returned item lost data or wear");
                    ItemStack saved=ItemStack.parseOptional(level.registryAccess(),(CompoundTag)owned.save(level.registryAccess()));
                    check(stillForm(saved)&&data(saved).getBoolean("uwu_glitch_pending")&&enchantments.equals(saved.get(DataComponents.ENCHANTMENTS)),"pending form did not survive item save/load");
                    caught=true;
                }
                if(player.getCooldowns().isOnCooldown(item))check(stillForm(owned),"form advanced during cooldown");
                else if(!stillForm(owned)){
                    System.out.println("GLITCH CUSTODY PASS physical Twin Chakram, full component preservation, occupied hand and cooldown-gated switch");
                    startForm("cupid_bow",InteractionHand.MAIN_HAND);
                    item.use(level,player,InteractionHand.MAIN_HAND);
                    item.releaseUsing(current,level,player,71980);
                    player.stopUsingItem();
                    var arrows=level.getEntitiesOfClass(HeartArrow.class,player.getBoundingBox().inflate(5),a->a.getOwner()==player);
                    check(arrows.size()==1,"Cupid form failed to shoot its actual heart arrow");
                    arrow=arrows.getFirst();arrow.setNoGravity(true);arrow.setDeltaMovement(Vec3.ZERO);
                    check(stillForm(current)&&data(current).getBoolean("uwu_glitch_pending"),"bow switched before arrow completion");
                    deadline=level.getGameTime()+30;stage=3;return false;
                }
            }
            check(level.getGameTime()<deadline,"Twin Chakram did not return and advance");return false;
        }
        if(stage==3){
            check(stillForm(current),"Cupid form changed while arrow remained active");
            if(level.getGameTime()<deadline)return false;
            arrow.setDeltaMovement(0,0,3);deadline=level.getGameTime()+80;stage=4;return false;
        }
        if(stage==4){
            if(arrow.isAlive()){check(stillForm(current),"Cupid form changed before impact");check(level.getGameTime()<deadline,"heart arrow did not reach wall");return false;}
            if(stillForm(current)){check(level.getGameTime()<deadline,"Cupid form never advanced after impact");return false;}
            System.out.println("GLITCH BOW PASS form held beyond zero cooldown until actual arrow impact");
            frozenTarget=EntityType.HUSK.create(level);check(frozenTarget!=null,"missing Frost test target");
            frozenTarget.moveTo(player.getX(),player.getY(),player.getZ()+4,180,0);
            frozenTarget.setNoAi(true);frozenTarget.setNoGravity(true);
            frozenTarget.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);frozenTarget.setHealth(200);
            level.addFreshEntity(frozenTarget);player.setYRot(0);player.setYHeadRot(0);player.yHeadRotO=0;player.setXRot(0);
            startForm("frost_whip",InteractionHand.MAIN_HAND);
            item.use(level,player,InteractionHand.MAIN_HAND);
            var lashes=level.getEntitiesOfClass(RibbonLash.class,player.getBoundingBox().inflate(4),l->l.owner()==player);
            check(lashes.size()==1&&lashes.getFirst().relic()==Arsenal.Relic.FROST_WHIP,"Frost form did not cast its own lash");
            check(stillForm(current)&&data(current).getBoolean("uwu_glitch_pending"),"whip advanced before ability/cooldown");
            whipStarted=level.getGameTime();deadline=level.getGameTime()+350;stage=5;return false;
        }
        if(stage==5){
            if(frozenTarget.getTicksFrozen()>0)frozenObserved=true;
            if(player.getCooldowns().isOnCooldown(item))check(stillForm(current),"whip advanced during cooldown");
            if(frozenObserved&&level.getGameTime()-whipStarted<160)check(stillForm(current),"whip advanced before its freezing effect ended");
            if(stillForm(current)){check(level.getGameTime()<deadline,"whip did not advance after cooldown");return false;}
            check(frozenObserved&&level.getGameTime()-whipStarted>=160,"Frost hit/effect lifetime was not observed");
            frozenTarget.discard();
            System.out.println("GLITCH WHIP PASS selected Snowkiss lash, actual hit and full freeze-effect lifetime");
            startForm("counter_iron_greataxe",InteractionHand.OFF_HAND);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT));
            item.use(level,player,InteractionHand.OFF_HAND);
            var launched=level.getEntitiesOfClass(RelicFlight.class,player.getBoundingBox().inflate(4),f->f.getOwner()==player&&!f.wave());
            check(launched.size()==1&&player.getOffhandItem().isEmpty(),"offhand throw did not leave its hand");flight=launched.getFirst();
            check(flight.getItem().is(item)&&stillForm(flight.getItem()),"offhand flight wrong stack or form");
            deadline=level.getGameTime()+250;stage=6;return false;
        }
        if(stage==6){
            if(!flight.isRemoved()){check(findOwned().isEmpty(),"offhand Glitch duplicated while airborne");check(stillForm(flight.getItem()),"offhand flight changed form");}
            else if(player.getOffhandItem().is(item)){
                check(player.getMainHandItem().is(Items.DIRT),"offhand return changed the main hand");
                ItemStack returned=player.getOffhandItem();
                if(!player.getCooldowns().isOnCooldown(item)&&level.getServer().overworld().getGameTime()<data(returned).getLong("uwu_glitch_ready")){
                    check(stillForm(returned),"counter changed before its matching ward ended");offhandWardWaited=true;
                }
                if(stillForm(returned)){check(level.getGameTime()<deadline,"offhand counter never advanced");return false;}
                check(offhandWardWaited,"counter ward gate was not observed after item cooldown");
                System.out.println("GLITCH OFFHAND PASS physical custody and return to offhand");
                startForm("counter_iron_spear",InteractionHand.MAIN_HAND);
                player.setShiftKeyDown(true);item.use(level,player,InteractionHand.MAIN_HAND);player.setShiftKeyDown(false);
                check(player.fishing!=null&&data(current).getBoolean("uwu_glitch_fishing"),"spear fishing hook not created");
                deadline=level.getGameTime()+15;stage=7;return false;
            }
            check(level.getGameTime()<deadline,"offhand Glitch did not return");return false;
        }
        if(stage==7){
            check(stillForm(current)&&player.fishing!=null,"fishing form advanced or hook vanished before reel");
            if(level.getGameTime()<deadline)return false;
            player.setShiftKeyDown(true);item.use(level,player,InteractionHand.MAIN_HAND);player.setShiftKeyDown(false);
            check(player.fishing==null,"spear fishing did not reel");
            deadline=level.getGameTime()+20;stage=8;return false;
        }
        if(stage==8){
            if(stillForm(current)){check(level.getGameTime()<deadline,"fishing form did not advance after reel");return false;}
            System.out.println("GLITCH FISHING PASS retained form while cast, reel then advance");
            recovery(level);
            System.out.println("UWU GLITCH LIFECYCLE PASS real melee, Twin Chakram, projectile custody, saved data, cooldown, bow, whip, offhand, fishing and offline recovery");
            return true;
        }
        throw new AssertionError("Unknown Glitch lifecycle stage "+stage);
    }
}
