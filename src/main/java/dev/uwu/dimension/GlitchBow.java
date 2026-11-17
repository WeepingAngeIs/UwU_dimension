package dev.uwu.dimension;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/** A single owned stack which performs the currently displayed form's actions. */
public final class GlitchBow extends Item {
    public static final List<String> FORMS=forms();
    private static List<String> forms(){
        var list=new ArrayList<String>();
        for(Arsenal.Relic r:Arsenal.Relic.values())list.add(r.id());
        CounterArsenal.ROSTER.forEach(e->list.add(e.id()));return List.copyOf(list);
    }
    public GlitchBow(){super(new Properties().durability(1536).rarity(Rarity.EPIC));}
    public static int currentIndex(ItemStack stack){
        CompoundTag n=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return n.contains("uwu_form")?Math.floorMod(n.getInt("uwu_form"),FORMS.size()):0;
    }
    public static String form(ItemStack stack){return FORMS.get(currentIndex(stack));}
    public static CounterArsenal.Entry counter(ItemStack stack){
        return stack.getItem() instanceof GlitchBow?CounterArsenal.entry(form(stack)):null;
    }
    public static Arsenal.Relic relic(ItemStack stack){
        if (!(stack.getItem() instanceof GlitchBow) || counter(stack)!=null) return null;
        return Arsenal.Relic.valueOf(form(stack).toUpperCase(Locale.ROOT));
    }
    public static boolean isRelic(ItemStack stack, Arsenal.Relic expected){
        return stack.is(Arsenal.WEAPONS.get(expected).get()) || relic(stack)==expected;
    }
    public static boolean isScythe(ItemStack stack){
        var c=counter(stack); return relic(stack)==Arsenal.Relic.VESPER_SCYTHE || c!=null && c.shape().equals("scythe");
    }
    /** Temporary copy for optional integrations; it is never added to an inventory. */
    public static ItemStack mimicStack(ItemStack stack){
        if(!(stack.getItem() instanceof GlitchBow))return ItemStack.EMPTY;
        var c=counter(stack);var r=relic(stack);
        return stack.transmuteCopy(c!=null?CounterArsenal.ITEMS.get(c.id()).get():Arsenal.WEAPONS.get(r).get());
    }
    public static net.minecraft.resources.ResourceLocation combatFallback(ItemStack stack){
        var c=counter(stack);var r=relic(stack);
        String shape=c!=null?c.shape():r==null?"":r.bow()?"bow":r==Arsenal.Relic.ROSE_DAGGER?"dagger":r==Arsenal.Relic.VESPER_SCYTHE?"scythe":r.axe()?"axe":r==Arsenal.Relic.LANTERN_SPEAR||r==Arsenal.Relic.TIDELACE_TRIDENT?"spear":RangedRelics.isWhip(r)?"whip":"wand";
        String preset=switch(shape){case "axe"->"axe";case "hammer"->"hammer";case "dagger"->"dagger";case "scythe"->"scythe";case "spear"->"spear";case "chakram"->"sword";case "whip"->"sickle";case "wand"->"wand";default->null;};
        return preset==null?null:net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("bettercombat",preset);
    }
    private static boolean pending(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBoolean("uwu_glitch_pending");}
    static UUID identity(ItemStack stack){
        CompoundTag data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(data.hasUUID("uwu_glitch_id"))return data.getUUID("uwu_glitch_id");
        UUID id=UUID.randomUUID();CustomData.update(DataComponents.CUSTOM_DATA,stack,n->n.putUUID("uwu_glitch_id",id));return id;
    }
    static void begin(ItemStack stack,ServerLevel level,int ticks,UUID token){
        identity(stack);
        CustomData.update(DataComponents.CUSTOM_DATA,stack,n->{n.putBoolean("uwu_glitch_pending",true);n.putLong("uwu_glitch_ready",level.getServer().overworld().getGameTime()+ticks);n.remove("uwu_glitch_fishing");n.remove("uwu_glitch_fish_owner");n.remove("uwu_glitch_fish_hook");if(token!=null)n.putUUID("uwu_glitch_token",token);else n.remove("uwu_glitch_token");});
    }
    static void trackWard(ServerLevel level,ItemStack stack,Player player,boolean resistanceApplied){
        GlitchCompletions.track(level,stack,player,"counter_ward");
        if(resistanceApplied)GlitchCompletions.track(level,stack,player,"fire_resistance",160);
    }
    private static void cancel(ItemStack stack){CustomData.update(DataComponents.CUSTOM_DATA,stack,n->{n.remove("uwu_glitch_pending");n.remove("uwu_glitch_ready");n.remove("uwu_glitch_token");n.remove("uwu_glitch_fishing");n.remove("uwu_glitch_fish_owner");n.remove("uwu_glitch_fish_hook");});}
    @Override public void inventoryTick(ItemStack stack,Level world,Entity entity,int slot,boolean selected){
        if(!(world instanceof ServerLevel level))return;
        CompoundTag n=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!n.contains("uwu_form"))CustomData.update(DataComponents.CUSTOM_DATA,stack,d->d.putInt("uwu_form",0));
        if(!n.getBoolean("uwu_glitch_pending")||level.getServer().overworld().getGameTime()<n.getLong("uwu_glitch_ready"))return;
        if(entity instanceof Player p && (p.getCooldowns().isOnCooldown(this) || BlossomArts.pinFlower(p,stack)))return;
        // Vanilla fishing hooks belong to the player and are not persisted on logout.
        // Keep the form while that hook remains active, including if the stack moves.
        if(n.getBoolean("uwu_glitch_fishing")){
            if(n.hasUUID("uwu_glitch_fish_owner")){
                Player owner=level.getServer().getPlayerList().getPlayer(n.getUUID("uwu_glitch_fish_owner"));
                // Fake players are not in PlayerList, but may still be the holder.
                if(owner==null && entity instanceof Player holder && holder.getUUID().equals(n.getUUID("uwu_glitch_fish_owner")))owner=holder;
                if(owner==null)return;
                if(owner.fishing!=null&&!owner.fishing.isRemoved()&&(!n.hasUUID("uwu_glitch_fish_hook")||owner.fishing.getUUID().equals(n.getUUID("uwu_glitch_fish_hook"))))return;
            }else if(entity instanceof Player holder && holder.fishing!=null)return;
        }
        if(n.hasUUID("uwu_glitch_token")&&(!n.hasUUID("uwu_glitch_id")||!GlitchCompletions.completed(level,n.getUUID("uwu_glitch_id"),n.getUUID("uwu_glitch_token"))))return;
        int current=currentIndex(stack);var choices=new ArrayList<Integer>();
        for(int i=0;i<FORMS.size();i++){var e=CounterArsenal.entry(FORMS.get(i));if(i!=current&&(e==null||CounterArsenal.active(e)))choices.add(i);}
        if(choices.isEmpty())return;
        int next=choices.get(level.random.nextInt(choices.size()));
        CustomData.update(DataComponents.CUSTOM_DATA,stack,d->{d.putInt("uwu_form",next);d.remove("uwu_glitch_pending");d.remove("uwu_glitch_ready");d.remove("uwu_glitch_token");d.remove("uwu_glitch_fishing");d.remove("uwu_glitch_fish_owner");d.remove("uwu_glitch_fish_hook");d.remove("uwu_static_until");});
    }
    /** Preserve all custom attributes except the two native base modifiers, which follow the form. */
    static void attributes(ItemAttributeModifierEvent event){
        ItemStack s=event.getItemStack();if(!(s.getItem() instanceof GlitchBow))return;
        var c=counter(s);var r=relic(s);double damage=c!=null?c.damage():r.damage;double speed=c!=null?1.3:r.speed;
        event.replaceModifier(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_ID,damage-1,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND);
        event.replaceModifier(Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_ID,speed-4,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND);
        if(r==Arsenal.Relic.BLOSSOM_GREATSWORD||r==Arsenal.Relic.VESPER_SCYTHE)
            event.replaceModifier(Attributes.ENTITY_INTERACTION_RANGE,new AttributeModifier(Arsenal.id("bladed_reach"),1,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND);
    }
    @Override public boolean shouldCauseReequipAnimation(ItemStack before,ItemStack after,boolean slotChanged){return slotChanged||before.getItem()!=after.getItem();}
    @Override public Component getName(ItemStack s){return Component.literal("ERROR // Glitch Bow");}
    @Override public void appendHoverText(ItemStack s,TooltipContext c,List<Component> text,TooltipFlag flag){
        var entry=counter(s);var r=relic(s);
        text.add(Component.literal("Current form: "+(entry!=null?entry.name():r.title)).withStyle(ChatFormatting.LIGHT_PURPLE));
        if(entry!=null){text.add(Component.literal("Answers "+entry.sourceName()).withStyle(ChatFormatting.GOLD));text.add(Component.literal(CounterEffects.description(entry.school())).withStyle(ChatFormatting.GRAY));text.add(Component.literal(entry.shape().equals("scythe")?"Hold use to throw; attack for a crescent.":entry.shape().equals("whip")?"Use to lash.":"Use to throw; it returns to your hand.").withStyle(ChatFormatting.GRAY));}
        else text.add(Component.literal(r.hint).withStyle(ChatFormatting.GRAY));
        text.add(Component.literal("Changes form after the ability, return and cooldown finish.").withStyle(ChatFormatting.AQUA));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level world,Player p,InteractionHand hand){
        ItemStack s=p.getItemInHand(hand);var c=counter(s);var r=relic(s);
        // Ribbonstep's marked target can be used for its second stage after the
        // regular item cooldown, even while the first ability is still pending.
        if(pending(s)&&r==Arsenal.Relic.RIFT_WHIP&&p.isShiftKeyDown()&&!p.getCooldowns().isOnCooldown(this)){
            if(!(world instanceof ServerLevel level))return InteractionResultHolder.sidedSuccess(s,true);
            if(!RangedRelics.activate(level,p,r,hand))return InteractionResultHolder.fail(s);
            long ready=level.getServer().overworld().getGameTime()+r.cooldown;
            CustomData.update(DataComponents.CUSTOM_DATA,s,n->n.putLong("uwu_glitch_ready",Math.max(n.getLong("uwu_glitch_ready"),ready)));
            p.getCooldowns().addCooldown(this,r.cooldown);s.hurtAndBreak(1,p,LivingEntity.getSlotForHand(hand));
            return InteractionResultHolder.sidedSuccess(s,false);
        }
        boolean fishingForm=c!=null&&c.shape().equals("spear") || r==Arsenal.Relic.LANTERN_SPEAR || r==Arsenal.Relic.TIDELACE_TRIDENT;
        if(fishingForm && p.isShiftKeyDown()){
            CompoundTag data=s.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            if(data.getBoolean("uwu_glitch_pending")&&!data.getBoolean("uwu_glitch_fishing"))return InteractionResultHolder.fail(s);
            if(data.hasUUID("uwu_glitch_fish_owner")&&!data.getUUID("uwu_glitch_fish_owner").equals(p.getUUID()))return InteractionResultHolder.fail(s);
            if(data.getBoolean("uwu_glitch_fishing")&&p.fishing==null)return InteractionResultHolder.fail(s);
            var priorHook=p.fishing;boolean alreadyFishing=priorHook!=null;
            var result=SpearFishing.use(world,p,hand);
            if(world instanceof ServerLevel level && (!alreadyFishing&&p.fishing!=null || alreadyFishing)){
                begin(s,level,0,null);
                var activeHook=p.fishing!=null?p.fishing:priorHook;
                CustomData.update(DataComponents.CUSTOM_DATA,s,n->{n.putBoolean("uwu_glitch_fishing",true);n.putUUID("uwu_glitch_fish_owner",p.getUUID());if(activeHook!=null)n.putUUID("uwu_glitch_fish_hook",activeHook.getUUID());});
            }
            return result;
        }
        if((pending(s) && !(r==Arsenal.Relic.VESPER_SCYTHE && BlossomArts.flowerCredit(p,s)))||p.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(s);
        boolean draw=r!=null&&(r.bow()||r==Arsenal.Relic.VESPER_SCYTHE)||c!=null&&c.shape().equals("scythe");
        if(draw){if(r==Arsenal.Relic.CUPID_BOW&&!world.isClientSide)CupidEffects.resetDraw(p);p.startUsingItem(hand);return InteractionResultHolder.consume(s);}
        if(!(world instanceof ServerLevel level))return InteractionResultHolder.sidedSuccess(s,true);
        int cooldown=c!=null?60:r.cooldown;
        // Every counter cast grants an eight-second matching ward. Retain the
        // form through that active ability even though its item cooldown is shorter.
        int formDuration=c!=null?Math.max(160,cooldown):cooldown;
        if(c!=null){
            boolean resistance=CounterEffects.wardTracked(level,p,c);
            if(c.shape().equals("whip")){
                UUID token=UUID.randomUUID(),id=identity(s);var lash=RibbonLash.castCounter(level,p,c);
                if(lash==null)return InteractionResultHolder.fail(s);
                lash.getPersistentData().putUUID("uwu.glitch_id",id);lash.getPersistentData().putUUID("uwu.glitch_token",token);
                begin(s,level,Math.max(12,formDuration),token);trackWard(level,s,p,resistance);s.hurtAndBreak(1,p,LivingEntity.getSlotForHand(hand));
            }
            else{begin(s,level,formDuration,UUID.randomUUID());trackWard(level,s,p,resistance);if(!RelicFlight.castGlitchWeapon(level,p,hand,null,c)){cancel(s);return InteractionResultHolder.fail(s);}}
        }else if(RangedRelics.isWhip(r) && !(r==Arsenal.Relic.RIFT_WHIP && p.isShiftKeyDown())){
            UUID token=UUID.randomUUID(),id=identity(s);var lash=RibbonLash.cast(level,p,r);
            if(lash==null)return InteractionResultHolder.fail(s);
            lash.getPersistentData().putUUID("uwu.glitch_id",id);lash.getPersistentData().putUUID("uwu.glitch_token",token);
            begin(s,level,Math.max(12,cooldown),token);s.hurtAndBreak(1,p,LivingEntity.getSlotForHand(hand));
        }else if(r==Arsenal.Relic.BLUE_HOUR_WAND){
            UUID token=UUID.randomUUID(),id=identity(s);begin(s,level,cooldown,token);
            if(!RelicAbilities.activate(level,p,r,s)){cancel(s);return InteractionResultHolder.fail(s);}
            GlitchCompletions.complete(level,id,token);s.hurtAndBreak(1,p,LivingEntity.getSlotForHand(hand));
        }else if(r==Arsenal.Relic.RIFT_WHIP){
            if(!RangedRelics.activate(level,p,r,hand))return InteractionResultHolder.fail(s);
            begin(s,level,cooldown,null);s.hurtAndBreak(1,p,LivingEntity.getSlotForHand(hand));
        }else{
            begin(s,level,cooldown,UUID.randomUUID());if(!RelicFlight.castGlitchWeapon(level,p,hand,r,null)){cancel(s);return InteractionResultHolder.fail(s);}
        }
        if(cooldown>0)p.getCooldowns().addCooldown(this,cooldown);
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),false);
    }
    @Override public int getUseDuration(ItemStack s,LivingEntity e){var r=relic(s);return isScythe(s)||r!=null&&r.bow()?72000:0;}
    @Override public UseAnim getUseAnimation(ItemStack s){var r=relic(s);return r!=null&&r.bow()?UseAnim.BOW:UseAnim.NONE;}
    @Override public void onUseTick(Level world,LivingEntity user,ItemStack s,int remaining){
        if(world instanceof ServerLevel level&&user instanceof Player p&&relic(s)==Arsenal.Relic.CUPID_BOW&&BowItem.getPowerForTime(72000-remaining)>=1)CupidEffects.fullDraw(level,p);
    }
    @Override public void releaseUsing(ItemStack stack,Level world,LivingEntity user,int left){
        if(!(world instanceof ServerLevel level)||!(user instanceof Player p)||(pending(stack) && !(relic(stack)==Arsenal.Relic.VESPER_SCYTHE && BlossomArts.flowerCredit(p,stack)))||p.getCooldowns().isOnCooldown(this))return;
        var c=counter(stack);var r=relic(stack);InteractionHand hand=p.getUsedItemHand();
        if(c!=null&&c.shape().equals("scythe")||r==Arsenal.Relic.VESPER_SCYTHE){
            if(72000-left<12||p.getPersistentData().getLong("uwu.scythe_flight")>level.getGameTime())return;
            boolean resistance=c!=null && CounterEffects.wardTracked(level,p,c);
            // The real scythe item cooldown is 60 ticks; counter scythes also
            // retain their form through the matching 160-tick ward.
            var beforeCast=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            begin(stack,level,c!=null?160:60,UUID.randomUUID());
            if(c!=null)trackWard(level,stack,p,resistance);
            if(RelicFlight.castGlitchWeapon(level,p,hand,r,c))p.getCooldowns().addCooldown(this,60);
            else stack.set(DataComponents.CUSTOM_DATA,CustomData.of(beforeCast));
            return;
        }
        if(r==null||!r.bow())return;
        float draw=BowItem.getPowerForTime(72000-left);
        if(r==Arsenal.Relic.CUPID_BOW){if(draw>=1)CupidEffects.fullDraw(level,p);CupidEffects.resetDraw(p);}
        if(draw<.15F)return;
        UUID token=UUID.randomUUID();UUID stackId=identity(stack);
        if(!HeartArrow.fire(level,p,r,draw,stackId,token))return;
        begin(stack,level,r.cooldown,token);if(r.cooldown>0)p.getCooldowns().addCooldown(this,r.cooldown);
        stack.hurtAndBreak(1,p,LivingEntity.getSlotForHand(hand));
    }
    @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){
        var c=counter(stack);var r=relic(stack);
        return c!=null?CounterArsenal.ITEMS.get(c.id()).get().hurtEnemy(stack,target,attacker):Arsenal.WEAPONS.get(r).get().hurtEnemy(stack,target,attacker);
    }
    @Override public void postHurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){
        var c=counter(stack);var r=relic(stack);
        if(c!=null)CounterArsenal.ITEMS.get(c.id()).get().postHurtEnemy(stack,target,attacker);
        else Arsenal.WEAPONS.get(r).get().postHurtEnemy(stack,target,attacker);
    }
    @Override public boolean canPerformAction(ItemStack stack,net.neoforged.neoforge.common.ItemAbility ability){
        var c=counter(stack);var r=relic(stack);return c!=null?CounterArsenal.ITEMS.get(c.id()).get().canPerformAction(stack,ability):Arsenal.WEAPONS.get(r).get().canPerformAction(stack,ability);
    }
    @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context){
        var s=context.getItemInHand();var c=counter(s);var r=relic(s);return c!=null?CounterArsenal.ITEMS.get(c.id()).get().useOn(context):Arsenal.WEAPONS.get(r).get().useOn(context);
    }
    @Override public boolean mineBlock(ItemStack stack,Level level,net.minecraft.world.level.block.state.BlockState state,net.minecraft.core.BlockPos pos,LivingEntity user){
        var c=counter(stack);var r=relic(stack);return c!=null?CounterArsenal.ITEMS.get(c.id()).get().mineBlock(stack,level,state,pos,user):Arsenal.WEAPONS.get(r).get().mineBlock(stack,level,state,pos,user);
    }
    @Override public float getDestroySpeed(ItemStack stack,net.minecraft.world.level.block.state.BlockState state){
        ItemStack form=mimicStack(stack);return form.getItem().getDestroySpeed(form,state);
    }
    @Override public boolean isCorrectToolForDrops(ItemStack stack,net.minecraft.world.level.block.state.BlockState state){
        ItemStack form=mimicStack(stack);return form.getItem().isCorrectToolForDrops(form,state);
    }
}
