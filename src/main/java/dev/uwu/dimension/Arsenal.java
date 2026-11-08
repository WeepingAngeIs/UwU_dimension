package dev.uwu.dimension;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Arsenal {
    public enum Relic {
        CUPID_BOW("Cupid Bow", 14, 1.4, 65, "Draw and release. A heart arrow befriends an ordinary creature for 40 seconds."),
        RIPPLE_BOW("Rippleglass Bow", 17, 1.25, 24, "Draw and release. On impact, a ripple marks and slows nearby creatures."),
        RIBBONHARE_SCEPTER("Ribbonhare Scepter", 14, 1.6, 200, "Use on a ledge to call a pink hare. Its thumps draw monsters as it bounds back."),
        BLOSSOM_GREATSWORD("Blossom Greatsword", 24, 0.85, 44, "Land a fully charged hit to scatter petals. Use while facing them within three seconds for a longer return sweep."),
        BELLHEART_AXE("Bellheart Axe", 22, 0.95, 105, "Use to ring the blade. Its toll gathers nearby foes and slows them for your next swing."),
        LOCKKEEPER_AXE("Lockkeeper's Axe", 21, 1.05, 85, "Use to send a turquoise wake through foes in front of you, driving them back."),
        VESPER_SCYTHE("Vesper Scythe", 20, 1.15, 105, "Use to reap a wide arc. Each enemy struck restores one heart, up to three."),
        AXOLOTL_WHIP("Twinfin Whip", 16, 1.6, 160, "Lash a foe to call exactly two axolotl companions for 30 seconds."),
        FROST_WHIP("Snowkiss Whip", 17, 1.6, 70, "Extend a frosted ribbon to freeze a target for three seconds."),
        RIFT_WHIP("Ribbonstep Whip", 18, 1.6, 80, "Lash to mark a foe. Sneak-use to teleport beside that marked foe within 24 blocks, when there is safe footing."),
        ROSE_DAGGER("Roseflight Dagger", 15, 2.2, 25, "Throw a rose blade. Its hit briefly reveals the target."),
        SATIN_WHIP("Satin Switch", 15, 2.1, 40, "Use to catch a creature up to eleven blocks away and pull it toward you."),
        LANTERN_SPEAR("Lanternfall Spear", 19, 1.25, 45, "Use to throw a light-forged copy. Its strike exposes the target in gold."),
        TIDELACE_TRIDENT("Tidelace Trident", 23, 1.0, 75, "Use to cast a spectral trident. Its impact drags nearby foes to the splash."),
        BLUE_HOUR_WAND("Blue Hour Wand", 16, 1.55, 65, "Use to trace a ray of cold light. The first creature struck is slowed and revealed.");

        final String title, hint;
        public final int damage;
        final double speed;
        final int cooldown;
        Relic(String title, int damage, double speed, int cooldown, String hint) {
            this.title = title;
            this.damage = damage;
            this.speed = speed;
            this.cooldown = cooldown;
            this.hint = hint;
        }
        String id() { return name().toLowerCase(Locale.ROOT); }
        boolean axe() { return this == BELLHEART_AXE || this == LOCKKEEPER_AXE; }
        boolean bow() { return this == CUPID_BOW || this == RIPPLE_BOW; }
    }

    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("uwudimension", path); }
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "uwudimension");
    public static final Map<Relic, Supplier<RelicItem>> WEAPONS = new EnumMap<>(Relic.class);
    static final Supplier<EntityType<HeartArrow>> ARROW = ENTITIES.register("votive_arrow", () ->
        EntityType.Builder.<HeartArrow>of(HeartArrow::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(1).build("uwudimension:votive_arrow"));
    static final Supplier<EntityType<Rabbit>> HARE = ENTITIES.register("ribbonhare", () ->
        EntityType.Builder.<Rabbit>of(Rabbit::new, MobCategory.CREATURE).sized(0.4F, 0.5F).clientTrackingRange(8).build("uwudimension:ribbonhare"));

    public static void register(IEventBus bus) {
        for (Relic relic : Relic.values()) WEAPONS.put(relic, ITEMS.register(relic.id(), () -> new RelicItem(relic)));
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e) -> e.put(HARE.get(), Rabbit.createAttributes().build()));
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey().equals(CreativeModeTabs.COMBAT)) WEAPONS.values().forEach(w -> e.accept(w.get()));
        });
        NeoForge.EVENT_BUS.addListener(HeartMagic::tick);
        NeoForge.EVENT_BUS.addListener(HeartMagic::filterTarget);
        NeoForge.EVENT_BUS.addListener(HeartMagic::protectKeeper);
        NeoForge.EVENT_BUS.addListener(RelicAbilities::onThrowImpact);
        NeoForge.EVENT_BUS.addListener(RelicAbilities::beforeAttack);
        RangedRelics.register(bus);
    }

    public static final class RelicItem extends Item {
        final Relic relic;
        RelicItem(Relic relic) { super(properties(relic)); this.relic = relic; }
        private static Item.Properties properties(Relic relic) {
            var attributes = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, relic.damage - 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, relic.speed - 4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            if (relic == Relic.BLOSSOM_GREATSWORD || relic == Relic.VESPER_SCYTHE)
                attributes.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(id("bladed_reach"), 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            Item.Properties properties = new Item.Properties().durability(1024).rarity(Rarity.RARE).attributes(attributes.build());
            if (relic.axe()) properties.component(net.minecraft.core.component.DataComponents.TOOL,
                Tiers.DIAMOND.createToolProperties(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE));
            return properties;
        }
        @Override public Component getName(ItemStack stack) { return Component.translatableWithFallback(getDescriptionId(), relic.title); }
        @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tips, TooltipFlag flag) {
            tips.add(Component.literal(relic.hint).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
            if (relic.bow() || relic == Relic.VESPER_SCYTHE) {
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
            if (level instanceof ServerLevel server && RangedRelics.activate(server, player, relic)) {
                player.getCooldowns().addCooldown(this, relic.cooldown);
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            // Let the native axe handle stripping, scraping, and removing wax with this stack.
            return relic.axe() ? Items.DIAMOND_AXE.useOn(context) : super.useOn(context);
        }
        @Override public boolean canPerformAction(ItemStack stack, net.neoforged.neoforge.common.ItemAbility ability) {
            return relic.axe() && net.neoforged.neoforge.common.ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability);
        }
        @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return relic.bow() || relic == Relic.VESPER_SCYTHE ? 72000 : 0; }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return relic.bow() ? UseAnim.BOW : UseAnim.NONE; }
        @Override public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
            if (relic != Relic.VESPER_SCYTHE || !(level instanceof ServerLevel server) || !(user instanceof Player p)) return;
            if (getUseDuration(stack,user)-remaining == 12 && p.getPersistentData().getLong("uwu.scythe_flight") <= level.getGameTime()) {
                RelicFlight.cast(server,p,relic,false);
                net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,stack,n -> n.putInt("uwu_element",p.getPersistentData().getInt("uwu.scythe_element")));
                p.getCooldowns().addCooldown(this,60);
                stack.hurtAndBreak(1,p,LivingEntity.getSlotForHand(p.getUsedItemHand()));
            }
        }
        @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int left) {
            if (!relic.bow() || !(level instanceof ServerLevel server) || !(user instanceof Player player)) return;
            float draw = BowItem.getPowerForTime(getUseDuration(stack, user) - left);
            if (draw < 0.15F) return;
            HeartArrow.fire(server, player, relic, draw);
            player.getCooldowns().addCooldown(this, relic.cooldown);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
        }
        @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            if (relic == Relic.BLOSSOM_GREATSWORD && attacker instanceof Player player && attacker.level() instanceof ServerLevel server)
                RelicAbilities.scatterPetals(server, player, target);
            return true;
        }
        @Override public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        }
    }
}
