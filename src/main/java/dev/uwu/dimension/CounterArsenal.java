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
    // One representative of each existing ability, shape, theme and runic combination.
    // Other exact IDs remain registered and obtainable by retuning a held counter.
    private static final Set<String> DIRECT_FORMS = Set.of(
        "counter_iron_spear", "counter_iron_glaive", "counter_iron_twinblade", "counter_iron_rapier",
        "counter_iron_greataxe", "counter_gold_greataxe", "counter_iron_greathammer", "counter_gold_greathammer",
        "counter_iron_chakram", "counter_gold_chakram", "counter_runic_spear", "counter_runic_glaive",
        "counter_runic_twinblade", "counter_runic_rapier", "counter_runic_greataxe", "counter_runic_greathammer",
        "counter_runic_chakram", "counter_hearthflame", "counter_emberblade", "counter_watching_warglaive",
        "counter_tainted_relic", "counter_toxic_longsword", "counter_the_devourer", "counter_frostfall",
        "counter_storms_edge", "counter_thunderbrand", "counter_ionbound_stormscale", "counter_stormbringer",
        "counter_soulstealer", "counter_soulrender", "counter_harbinger", "counter_hiveheart");
    private static List<Entry> load(){
        try(var stream=CounterArsenal.class.getResourceAsStream("/assets/uwudimension/counter_roster.json")){
            if(stream==null)throw new IllegalStateException("Missing counter roster");
            return List.of(new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Entry[].class));
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public static Entry entry(String id){return ROSTER.stream().filter(e->e.id().equals(id)).findFirst().orElse(null);}
    /** Legacy counters remain registered so saved stacks and their exact matching wards keep working. */
    public static boolean active(Entry entry) {
        return selectable(entry) && DIRECT_FORMS.contains(entry.id());
    }
    /** Retired bow-like counters remain registered for saved stacks only. */
    public static boolean selectable(Entry entry) { return entry != null && !entry.shape().equals("bow"); }
    static void register(IEventBus bus){
        for(Entry e:ROSTER)ITEMS.put(e.id(),REGISTRY.register(e.id(),()->new CounterItem(e)));
        REGISTRY.register(bus);CounterEffects.register(bus);CounterRetuning.register();
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{
            if(e.getTabKey().equals(CreativeModeTabs.COMBAT))
                ROSTER.stream().filter(CounterArsenal::active).forEach(entry -> e.accept(ITEMS.get(entry.id()).get()));
        });
    }
    public static final class CounterItem extends Item {
        public final Entry entry;
        CounterItem(Entry entry){super(properties(entry));this.entry=entry;}
        private static Properties properties(Entry entry) {
            String id = entry.id();
            boolean basicMaterial = id.startsWith("counter_iron_") || id.startsWith("counter_gold_")
                    || id.startsWith("counter_diamond_") || id.startsWith("counter_netherite_");
            Properties properties = new Properties().durability(basicMaterial ? 1100 : 1666).rarity(Rarity.EPIC).attributes(
                net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                    .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_DAMAGE_ID,entry.damage()-1,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND)
                    .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_SPEED_ID,-2.7,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND).build());
            if (entry.shape().equals("axe"))
                properties.component(net.minecraft.core.component.DataComponents.TOOL, Tiers.DIAMOND.createToolProperties(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE));
            if (entry.shape().equals("scythe"))
                properties.component(net.minecraft.core.component.DataComponents.TOOL, Tiers.DIAMOND.createToolProperties(net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE));
            if (entry.shape().equals("hammer"))
                properties.component(net.minecraft.core.component.DataComponents.TOOL, Tiers.DIAMOND.createToolProperties(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE));
            return properties;
        }
        @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            if (entry.shape().equals("axe")) return Items.DIAMOND_AXE.useOn(context);
            if (entry.shape().equals("scythe")) return Items.DIAMOND_HOE.useOn(context);
            return super.useOn(context);
        }
        @Override public boolean canPerformAction(ItemStack stack, net.neoforged.neoforge.common.ItemAbility ability) {
            return (entry.shape().equals("axe") && net.neoforged.neoforge.common.ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability))
                || (entry.shape().equals("scythe") && net.neoforged.neoforge.common.ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability))
                || (entry.shape().equals("hammer") && net.neoforged.neoforge.common.ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(ability))
                || (entry.shape().equals("spear") && net.neoforged.neoforge.common.ItemAbilities.DEFAULT_FISHING_ROD_ACTIONS.contains(ability));
        }
        @Override public boolean mineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.core.BlockPos pos, LivingEntity user) {
            if ((entry.shape().equals("axe") || entry.shape().equals("scythe") || entry.shape().equals("hammer")) && !level.isClientSide
                    && state.getDestroySpeed(level, pos) != 0)
                stack.hurtAndBreak(1, user, EquipmentSlot.MAINHAND);
            return true;
        }
        @Override public boolean shouldCauseReequipAnimation(ItemStack before,ItemStack after,boolean slotChanged){return slotChanged||before.getItem()!=after.getItem();}
        @Override public Component getName(ItemStack stack){return Component.literal(entry.name());}
        @Override public void appendHoverText(ItemStack stack,TooltipContext ctx,List<Component> tips,TooltipFlag flag){
            tips.add(Component.literal("Answers "+entry.sourceName()).withStyle(ChatFormatting.GOLD));
            tips.add(Component.literal(CounterEffects.description(entry.school())).withStyle(entry.theme().equals("dusk")?ChatFormatting.DARK_PURPLE:ChatFormatting.LIGHT_PURPLE));
            if(!entry.shape().equals("whip"))tips.add(Component.literal("Innate Swift Loyalty III • returns to your hand").withStyle(ChatFormatting.AQUA));
            tips.add(Component.literal("Use to cast • "+entry.damage()+" damage • 8-second matching ward").withStyle(ChatFormatting.GRAY));
            tips.add(Component.literal("Retune to another target: /sakura counter").withStyle(ChatFormatting.GRAY));
            if(entry.runic())tips.add(Component.literal("Also cleanses rolled runic ailments during the ward.").withStyle(ChatFormatting.AQUA));
            if(entry.shape().equals("hammer"))tips.add(Component.literal("Crouch and mine for a 9 × 9 plane when server area mining is enabled.").withStyle(ChatFormatting.GRAY));
        }
        @Override public InteractionResultHolder<ItemStack> use(Level world,Player p,InteractionHand hand){
            ItemStack stack=p.getItemInHand(hand);
            if(entry.shape().equals("spear") && p.isShiftKeyDown())return SpearFishing.use(world,p,hand);
            if(p.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
            if(entry.shape().equals("scythe")){p.startUsingItem(hand);return InteractionResultHolder.consume(stack);}
            if(world instanceof ServerLevel level){
                if(entry.shape().equals("whip")){
                    fire(level,p,entry);
                    p.getCooldowns().addCooldown(this,60);
                    stack.hurtAndBreak(1,p,LivingEntity.getSlotForHand(hand));
                }else{
                    CounterEffects.ward(level,p,entry);
                    if(!RelicFlight.castCounterWeapon(level,p,entry,hand))return InteractionResultHolder.fail(stack);
                    p.getCooldowns().addCooldown(this,60);
                    return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),false);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack,world.isClientSide);
        }
        @Override public int getUseDuration(ItemStack s,LivingEntity e){return entry.shape().equals("scythe")?72000:0;}
        @Override public UseAnim getUseAnimation(ItemStack s){return UseAnim.NONE;}
        @Override public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remaining) {
            if(!entry.shape().equals("scythe")||!(world instanceof ServerLevel level)||!(user instanceof Player p)||72000-remaining<12)return;
            if(p.getPersistentData().getLong("uwu.scythe_flight")>level.getGameTime())return;
            CounterEffects.ward(level,p,entry);
            if(RelicFlight.castCounterWeapon(level,p,entry,p.getUsedItemHand())){
                p.getCooldowns().addCooldown(this,60);
            }
        }
    }
    public static void fire(ServerLevel level,Player p,Entry e){
        CounterEffects.ward(level,p,e);
        if(e.shape().equals("scythe")){RelicFlight.castCounter(level,p,e,false);return;}
        if(e.shape().equals("whip")){RibbonLash.castCounter(level,p,e);return;}
        RelicProjectile projectile=RangedRelics.PROJECTILE.get().create(level);if(projectile==null)return;
        projectile.setItem(new ItemStack(ITEMS.get(e.id()).get()));
        projectile.getPersistentData().putString("uwu.counter",e.id());
        projectile.launchFrom(p,1.65F,0);level.addFreshEntity(projectile);
    }
}
