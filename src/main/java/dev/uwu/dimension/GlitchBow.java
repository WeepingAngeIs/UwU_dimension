package dev.uwu.dimension;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** One durable owned item. Forms are dispatch keys and model overrides, never inventory grants. */
public final class GlitchBow extends Item {
    public static final List<String> FORMS=forms();
    private static List<String> forms(){
        var list=new ArrayList<String>();
        for(Arsenal.Relic r:Arsenal.Relic.values())list.add(r.id());
        CounterArsenal.ROSTER.forEach(e->list.add(e.id()));return List.copyOf(list);
    }
    public GlitchBow(){super(new Properties().durability(1536).rarity(Rarity.EPIC));}
    @Override public boolean shouldCauseReequipAnimation(ItemStack before,ItemStack after,boolean slotChanged){return slotChanged||before.getItem()!=after.getItem();}
    @Override public Component getName(ItemStack s){return Component.literal("ERROR // Glitch Bow");}
    @Override public void appendHoverText(ItemStack s,TooltipContext c,List<Component> text,TooltipFlag flag){
        text.add(Component.literal("Draw and fire: a different UwU weapon emerges from the static."));
        text.add(Component.literal("The same bow keeps its enchantments and wear. Its form lasts until the next shot."));
        var data=s.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(data.contains("uwu_form"))text.add(Component.literal("Current form: "+FORMS.get(Math.floorMod(data.getInt("uwu_form"),FORMS.size())).replace('_',' ')));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player p,InteractionHand hand){
        if(p.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(p.getItemInHand(hand));
        p.startUsingItem(hand);return InteractionResultHolder.consume(p.getItemInHand(hand));
    }
    @Override public int getUseDuration(ItemStack s,LivingEntity e){return 72000;}
    @Override public UseAnim getUseAnimation(ItemStack s){return UseAnim.BOW;}
    @Override public void releaseUsing(ItemStack stack,Level world,LivingEntity entity,int left){
        if(!(world instanceof ServerLevel level)||!(entity instanceof Player p)||p.getCooldowns().isOnCooldown(this))return;
        if(BowItem.getPowerForTime(72000-left)<.4F)return;
        CompoundTag prior=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        int current=prior.contains("uwu_form")?Math.floorMod(prior.getInt("uwu_form"),FORMS.size()):-1;
        int next=level.random.nextInt(FORMS.size()-(current>=0?1:0));if(current>=0&&next>=current)next++;
        String form=FORMS.get(next);
        CounterArsenal.Entry counter=CounterArsenal.entry(form);
        if(counter!=null)CounterArsenal.fire(level,p,counter);
        else{
            Arsenal.Relic r=Arsenal.Relic.valueOf(form.toUpperCase(Locale.ROOT));
            if(r.bow())HeartArrow.fire(level,p,r,1);
            else if(RangedRelics.isWhip(r))RibbonLash.cast(level,p,r);
            else if(r==Arsenal.Relic.BLUE_HOUR_WAND)RelicAbilities.activate(level,p,r);
            else RelicFlight.cast(level,p,r,false);
        }
        final int selected=next;
        CustomData.update(DataComponents.CUSTOM_DATA,stack,n->{n.putInt("uwu_form",selected);n.putLong("uwu_static_until",level.getGameTime()+8);});
        p.getCooldowns().addCooldown(this,60);
        stack.hurtAndBreak(1,p,LivingEntity.getSlotForHand(p.getUsedItemHand()));
    }
}
