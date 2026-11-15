package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.LevelReader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.registries.*;

public final class SakuraCraft {
    private static final String MOD = "uwudimension";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, MOD);
    private static final List<Supplier<? extends Item>> NATURAL = new ArrayList<>();
    private static final List<Supplier<? extends Item>> GEAR = new ArrayList<>();

    static final DeferredBlock<Block> TUFF = stone("sazanami_tuff", MapColor.TERRACOTTA_WHITE, 0);
    static final DeferredBlock<Block> SHALE = stone("nightshale", MapColor.COLOR_PURPLE, 0);
    static final DeferredBlock<Block> ROSE_ORE = stone("roseglass_ore", MapColor.COLOR_PINK, 6);
    static final DeferredBlock<Block> OPAL_ORE = stone("vesper_opal_ore", MapColor.COLOR_PURPLE, 3);
    static final DeferredBlock<Block> SHELF = stone("blush_shellstone", MapColor.COLOR_PINK, 2);
    static final DeferredBlock<Block> ABYSS = stone("moonwake_shellstone", MapColor.COLOR_CYAN, 5);
    static final DeferredBlock<Block> LAMP = stone("confluence_lamp", MapColor.COLOR_MAGENTA, 15);
    static final DeferredBlock<SeagrassBlock> PLUME = fern("silkfin_plume", 5);
    static final DeferredBlock<SeagrassBlock> KELP = fern("veilribbon_fern", 8);
    static final DeferredBlock<SeagrassBlock> CLAM = fern("starcap_clam", 10);
    static final DeferredItem<Item> RAW_ROSE = ITEMS.registerSimpleItem("rough_roseglass", new Item.Properties());
    static final DeferredItem<Item> RAW_OPAL = ITEMS.registerSimpleItem("rough_vesper_opal", new Item.Properties());
    static final DeferredItem<Item> ROSE = ITEMS.registerSimpleItem("roseglass_prism", new Item.Properties());
    static final DeferredItem<Item> OPAL = ITEMS.registerSimpleItem("vesper_opal", new Item.Properties());
    private static final String BLOOM = "petalweave", THORN = "nightbriar";
    private static final Holder<ArmorMaterial> PETAL = material(BLOOM, ROSE);
    private static final Holder<ArmorMaterial> BRIAR = material(THORN, OPAL);
    private static final List<ArmorItem.Type> TYPES = List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
            ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS);

    static {
        for (ArmorItem.Type type : TYPES) {
            addGear(BLOOM, PETAL, type);
            addGear(THORN, BRIAR, type);
        }
    }
    private SakuraCraft() {}

    public static void register(IEventBus bus) {
        MATERIALS.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(SakuraCraft::creative);
        NeoForge.EVENT_BUS.addListener(SakuraCraft::attributes);
        NeoForge.EVENT_BUS.addListener(SakuraCraft::guard);
    }
    private static DeferredBlock<Block> stone(String name, MapColor color, int light) {
        var block = BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().mapColor(color)
                .strength(3F, 6F).requiresCorrectToolForDrops().sound(SoundType.CALCITE).lightLevel(s -> light));
        NATURAL.add(ITEMS.registerSimpleBlockItem(name, block));
        return block;
    }
    private static DeferredBlock<SeagrassBlock> fern(String name, int light) {
        var block = BLOCKS.<SeagrassBlock>register(name, () -> new SeagrassBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WATER).noCollission().instabreak().sound(SoundType.WET_GRASS)
                .lightLevel(s -> light)) {
            @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
                return false;
            }
        });
        NATURAL.add(ITEMS.registerSimpleBlockItem(name, block));
        return block;
    }
    private static Holder<ArmorMaterial> material(String name, Supplier<Item> mineral) {
        return MATERIALS.register(name, () -> new ArmorMaterial(Map.of(ArmorItem.Type.HELMET, 2,
                ArmorItem.Type.CHESTPLATE, 7, ArmorItem.Type.LEGGINGS, 5, ArmorItem.Type.BOOTS, 2),
                16, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(mineral.get()),
                List.of(new ArmorMaterial.Layer(UwUDimension.id(name))), 1F, 0F));
    }
    private static void addGear(String name, Holder<ArmorMaterial> material, ArmorItem.Type type) {
        GEAR.add(ITEMS.register(name + "_" + type.getName(), () -> new ArmorItem(material, type,
                new Item.Properties().durability(type.getDurability(23)).rarity(Rarity.UNCOMMON)) {
            @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flag) {
                super.appendHoverText(stack, context, text, flag);
                text.add(Component.translatable("tip.uwudimension." + name));
            }
        }));
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) NATURAL.forEach(i -> event.accept(i.get()));
        if (event.getTabKey() == CreativeModeTabs.COMBAT) GEAR.forEach(i -> event.accept(i.get()));
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS)
            List.of(RAW_ROSE, RAW_OPAL, ROSE, OPAL).forEach(i -> event.accept(i.get()));
    }
    private static void attributes(ItemAttributeModifierEvent event) {
        if (!(event.getItemStack().getItem() instanceof ArmorItem armor)) return;
        var slot = EquipmentSlotGroup.bySlot(armor.getType().getSlot());
        var id = UwUDimension.id("set_piece/" + armor.getType().getName());
        if (armor.getMaterial().equals(PETAL)) {
            event.addModifier(Attributes.MAX_HEALTH, new AttributeModifier(id, .5,
                    AttributeModifier.Operation.ADD_VALUE), slot);
        } else if (armor.getMaterial().equals(BRIAR)) {
            event.addModifier(Attributes.MOVEMENT_SPEED, new AttributeModifier(id, .025,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE), slot);
        }
    }
    private static void guard(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || event.getNewDamage() <= 0
                || !(event.getSource().getEntity() instanceof LivingEntity)) return;
        if (!(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem chest)) return;
        var material = chest.getMaterial();
        boolean bloom = material.equals(PETAL);
        if (!bloom && !material.equals(BRIAR)) return;
        for (var type : TYPES) {
            if (!(player.getItemBySlot(type.getSlot()).getItem() instanceof ArmorItem piece)
                    || piece.getType() != type || !piece.getMaterial().equals(material)) return;
        }
        var level = player.serverLevel();
        long time = level.getServer().overworld().getGameTime();
        String key = "uwudimension.ward_ready";
        if (time < player.getPersistentData().getLong(key)) return;
        player.getPersistentData().putLong(key, time + 400);
        if (bloom) {
            player.heal(2);
            for (var ally : level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(6),
                    p -> p != player && p.isAlive() && !p.isSpectator() && player.isAlliedTo(p)
                            && p.distanceToSqr(player) <= 36 && player.hasLineOfSight(p))) ally.heal(2);
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 0));
        }
        level.sendParticles(bloom ? ParticleTypes.CHERRY_LEAVES : ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1, player.getZ(), 8, .4, .4, .4, .02);
    }
}
