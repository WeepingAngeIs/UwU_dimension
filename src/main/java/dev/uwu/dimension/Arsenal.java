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
        CUPID_BOW("Cupid Bow", 14, 1.4, 0, "A pink heart befriends any eligible target for 40 seconds. Left-click while holding the bow to send your charmed allies after the target in your sights. Crouch and left-click two charmed targets to make a wish."),
        RIPPLE_BOW("Rippleglass Bow", 17, 1.25, 24, "Draw and release. On impact, a ripple marks and slows nearby creatures."),
        RIBBONHARE_SCEPTER("Ribbonhare Scepter", 14, 1.6, 200, "Send a glowing pink orb to solid ground to call a pink hare. Its thumps draw monsters."),
        BLOSSOM_GREATSWORD("Blossom Great Axe", 30, 0.85, 44, "Throw the blossom axe for a ranged strike. Fully charged melee hits still scatter petals."),
        BELLHEART_AXE("Bellheart Axe", 22, 0.95, 105, "Throw the bell axe. Its impact draws the struck foe toward you."),
        LOCKKEEPER_AXE("Lockkeeper's Axe", 21, 1.05, 85, "Throw the turquoise axe to drive the struck foe backward."),
        VESPER_SCYTHE("Vesper Scythe", 20, 1.15, 105, "Attack to launch an elemental crescent. Hold use for 0.6 seconds and release to throw. Till soil like a hoe."),
        AXOLOTL_WHIP("Twinfin Whip", 16, 1.6, 160, "Lash a foe to call exactly two axolotl companions for 30 seconds."),
        FROST_WHIP("Snowkiss Whip", 17, 1.6, 70, "Extend a frosted ribbon to freeze a target for three seconds."),
        RIFT_WHIP("Ribbonstep Whip", 18, 1.6, 80, "Lash to mark a foe. Sneak-use to teleport beside that marked foe within 24 blocks, when there is safe footing."),
        ROSE_DAGGER("Roseflight Dagger", 15, 2.2, 25, "Throw a rose blade. Its hit briefly reveals the target."),
        SATIN_WHIP("Satin Switch", 15, 2.1, 40, "Use to catch a creature up to eleven blocks away and pull it toward you."),
        LANTERN_SPEAR("Lanternfall Spear", 19, 1.25, 45, "Use to throw a light-forged copy. Its strike exposes the target in gold."),
        TIDELACE_TRIDENT("Tidelace Trident", 23, 1.0, 75, "Throw a spectral trident. Its impact draws the struck foe toward you."),
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
        boolean axe() { return this == BELLHEART_AXE || this == LOCKKEEPER_AXE || this == BLOSSOM_GREATSWORD; }
        boolean bow() { return this == CUPID_BOW || this == RIPPLE_BOW; }
        boolean throwable() {
            return this == RIBBONHARE_SCEPTER || this == BLOSSOM_GREATSWORD || this == BELLHEART_AXE
                || this == LOCKKEEPER_AXE || this == VESPER_SCYTHE || this == ROSE_DAGGER
                || this == LANTERN_SPEAR || this == TIDELACE_TRIDENT;
        }
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
        CupidMinions.register(bus);
        bus.addListener(CupidRarity::apply);
        NeoForge.EVENT_BUS.addListener(RelicReturns::recover);
        NeoForge.EVENT_BUS.addListener(RelicAbilities::onThrowImpact);
        NeoForge.EVENT_BUS.addListener(RelicAbilities::beforeAttack);
        NeoForge.EVENT_BUS.addListener(GlitchBow::attributes);
        NeoForge.EVENT_BUS.addListener(GlitchCompletions::onEntityLeave);
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
            Item.Properties properties = new Item.Properties().durability(1024).rarity(relic == Relic.CUPID_BOW ? Rarity.EPIC : Rarity.RARE).attributes(attributes.build());
            if (relic.axe()) properties.component(net.minecraft.core.component.DataComponents.TOOL,
                Tiers.DIAMOND.createToolProperties(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE));
            if (relic == Relic.VESPER_SCYTHE) properties.component(net.minecraft.core.component.DataComponents.TOOL,
                Tiers.DIAMOND.createToolProperties(net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE));
            return properties;
        }
        @Override public boolean onEntityItemUpdate(ItemStack stack, net.minecraft.world.entity.item.ItemEntity item) {
            if (relic == Relic.CUPID_BOW && item.level() instanceof ServerLevel level && item.tickCount % 4 == 0) {
                double phase = item.tickCount * .17;
                var at = item.position().add(Math.cos(phase)*.27,.16,Math.sin(phase)*.27);
                for (var viewer : level.players()) {
                    if (!CupidEffects.inRange(viewer, viewer.getEyePosition().distanceToSqr(at))) continue;
                    int mode = ReachLife.preference(viewer,"sakura_effects",2);
                    int interval = mode == 0 ? 16 : mode == 1 ? 8 : 4;
                    if (item.tickCount % interval == 0)
                        level.sendParticles(viewer,GOLD_SPARK,false,at.x,at.y,at.z,0,0,.04,0,1);
                }
            }
            return false;
        }
        private static final net.minecraft.core.particles.DustParticleOptions GOLD_SPARK =
                new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f,.73f,.16f), .72f);
        @Override public boolean shouldCauseReequipAnimation(ItemStack before,ItemStack after,boolean slotChanged){return slotChanged||before.getItem()!=after.getItem();}
        @Override public Component getName(ItemStack stack) { var name = Component.translatableWithFallback(getDescriptionId(), relic.title);
            return relic == Relic.CUPID_BOW ? name.withStyle(ChatFormatting.GOLD) : name; }
        @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tips, TooltipFlag flag) {
            if (relic == Relic.CUPID_BOW && CupidRarity.hasMythicPresentation(stack))
                tips.add(Component.translatableWithFallback("rarity.apotheosis:mythic", "Mythic").withStyle(ChatFormatting.GOLD));
            tips.add(Component.literal(relic.hint).withStyle(ChatFormatting.LIGHT_PURPLE));
            if (relic.throwable()) tips.add(Component.literal("Innate Swift Loyalty III Ã¢â‚¬Â¢ returns to your hand")
                    .withStyle(ChatFormatting.AQUA));
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if ((relic == Relic.LANTERN_SPEAR || relic == Relic.TIDELACE_TRIDENT) && player.isShiftKeyDown())
                return SpearFishing.use(level, player, hand);
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
            if (relic.bow() || relic == Relic.VESPER_SCYTHE) {
                if (relic == Relic.CUPID_BOW && !level.isClientSide) CupidEffects.resetDraw(player);
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
            if (level instanceof ServerLevel server && RangedRelics.activate(server, player, relic, hand)) {
                player.getCooldowns().addCooldown(this, relic.cooldown);
                if (!relic.throwable()) stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), false);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            // Let the native axe handle stripping, scraping, and removing wax with this stack.
            if (relic.axe()) return Items.DIAMOND_AXE.useOn(context);
            if (relic == Relic.VESPER_SCYTHE) return Items.DIAMOND_HOE.useOn(context);
            return super.useOn(context);
        }
        @Override public boolean canPerformAction(ItemStack stack, net.neoforged.neoforge.common.ItemAbility ability) {
            return (relic.axe() && net.neoforged.neoforge.common.ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability))
                || (relic == Relic.VESPER_SCYTHE && net.neoforged.neoforge.common.ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability))
                || ((relic == Relic.LANTERN_SPEAR || relic == Relic.TIDELACE_TRIDENT)
                    && net.neoforged.neoforge.common.ItemAbilities.DEFAULT_FISHING_ROD_ACTIONS.contains(ability));
        }
        @Override public boolean mineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.core.BlockPos pos, LivingEntity user) {
            if ((relic.axe() || relic == Relic.VESPER_SCYTHE) && !level.isClientSide
                    && state.getDestroySpeed(level, pos) != 0)
                stack.hurtAndBreak(1, user, EquipmentSlot.MAINHAND);
            return true;
        }
        @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return relic.bow() || relic == Relic.VESPER_SCYTHE ? 72000 : 0; }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return relic.bow() ? UseAnim.BOW : UseAnim.NONE; }
        @Override public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
            if (!(level instanceof ServerLevel server) || !(user instanceof Player p)) return;
            if (relic == Relic.CUPID_BOW && BowItem.getPowerForTime(getUseDuration(stack,user)-remaining) >= 1)
                CupidEffects.fullDraw(server,p);
        }
        @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int left) {
            if (relic == Relic.VESPER_SCYTHE && level instanceof ServerLevel server && user instanceof Player player) {
                if (getUseDuration(stack, user) - left >= 12
                        && player.getPersistentData().getLong("uwu.scythe_flight") <= level.getGameTime()
                        && RelicFlight.castWeapon(server, player, relic, player.getUsedItemHand()))
                    player.getCooldowns().addCooldown(this, 60);
                return;
            }
            if (!relic.bow() || !(level instanceof ServerLevel server) || !(user instanceof Player player)) return;
            float draw = BowItem.getPowerForTime(getUseDuration(stack, user) - left);
            if (relic == Relic.CUPID_BOW) {
                if (draw >= 1) CupidEffects.fullDraw(server,player);
                CupidEffects.resetDraw(player);
            }
            if (draw < 0.15F) return;
            HeartArrow.fire(server, player, relic, draw);
            if (relic.cooldown > 0) player.getCooldowns().addCooldown(this, relic.cooldown);
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
