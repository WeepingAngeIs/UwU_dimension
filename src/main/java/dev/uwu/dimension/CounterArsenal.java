package dev.uwu.dimension;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Explicit installed-registry coverage, with shared behavior for shared weapon mechanics. */
public final class CounterArsenal {
    public record Entry(String id,String target,String name,String school,String shape,String theme,int damage,String sourceName,boolean runic){}
    public static final List<Entry> ROSTER=load();
    public static final Map<String,Supplier<CounterItem>> ITEMS=new LinkedHashMap<>();
    private static final DeferredRegister.Items REGISTRY=DeferredRegister.createItems("uwudimension");
    private static List<Entry> load(){
        try(var stream=CounterArsenal.class.getResourceAsStream("/assets/uwudimension/counter_roster.json")){
            if(stream==null)throw new IllegalStateException("Missing counter roster");
            return List.of(new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Entry[].class));
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public static Entry entry(String id){return ROSTER.stream().filter(e->e.id().equals(id)).findFirst().orElse(null);}
    static void register(IEventBus bus){
        for(Entry e:ROSTER)ITEMS.put(e.id(),REGISTRY.register(e.id(),()->new CounterItem(e)));
        REGISTRY.register(bus);CounterEffects.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey().equals(CreativeModeTabs.COMBAT))ITEMS.values().forEach(i->e.accept(i.get()));});
    }
    public static final class CounterItem extends Item {
        public final Entry entry;
        CounterItem(Entry entry){super(new Properties().durability(1100).rarity(Rarity.EPIC));this.entry=entry;}
        @Override public Component getName(ItemStack stack){return Component.literal(entry.name());}
        @Override public void appendHoverText(ItemStack stack,TooltipContext ctx,List<Component> tips,TooltipFlag flag){
            tips.add(Component.literal("Answers "+entry.sourceName()).withStyle(ChatFormatting.GOLD));
            tips.add(Component.literal(CounterEffects.description(entry.school())).withStyle(entry.theme().equals("dusk")?ChatFormatting.DARK_PURPLE:ChatFormatting.LIGHT_PURPLE));
            tips.add(Component.literal("Use to cast • "+entry.damage()+" damage • 8-second matching ward").withStyle(ChatFormatting.GRAY));
            if(entry.runic())tips.add(Component.literal("Also cleanses rolled runic ailments during the ward.").withStyle(ChatFormatting.AQUA));
        }
        @Override public InteractionResultHolder<ItemStack> use(Level world,Player p,InteractionHand hand){
            ItemStack stack=p.getItemInHand(hand);
            if(p.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
            if(world instanceof ServerLevel level){
                fire(level,p,entry);p.getCooldowns().addCooldown(this,60);
                stack.hurtAndBreak(1,p,LivingEntity.getSlotForHand(hand));
            }
            return InteractionResultHolder.sidedSuccess(stack,world.isClientSide);
        }
    }
    public static void fire(ServerLevel level,Player p,Entry e){
        CounterEffects.ward(level,p,e);
        if(e.shape().equals("whip")){RibbonLash.castCounter(level,p,e);return;}
        Snowball projectile=new Snowball(level,p);projectile.setItem(new ItemStack(ITEMS.get(e.id()).get()));
        projectile.getPersistentData().putString("uwu.counter",e.id());projectile.setNoGravity(true);
        projectile.shootFromRotation(p,p.getXRot(),p.getYRot(),0,1.65F,0);level.addFreshEntity(projectile);
    }
}
