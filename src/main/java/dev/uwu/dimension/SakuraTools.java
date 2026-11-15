package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Material-specific everyday tools use the existing native ingots and gems. */
public final class SakuraTools {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    private static final List<Supplier<Item>> TOOLS = new ArrayList<>();

    private enum Material {
        SILVER("silver", Tiers.IRON, () -> SakuraMaterials.INGOT.get()),
        ROSEGLASS("roseglass", Tiers.DIAMOND, () -> SakuraCraft.ROSE.get()),
        VESPER_OPAL("vesper_opal", Tiers.NETHERITE, () -> SakuraCraft.OPAL.get());
        final String id;
        final Tier tier;
        final Supplier<Item> repair;
        Material(String id, Tier tier, Supplier<Item> repair) {
            this.id = id;
            this.tier = tier;
            this.repair = repair;
        }
    }

    private enum Kind {
        PICKAXE("pickaxe", BlockTags.MINEABLE_WITH_PICKAXE, 4, -2.8),
        AXE("axe", BlockTags.MINEABLE_WITH_AXE, 9, -3.1),
        SHOVEL("shovel", BlockTags.MINEABLE_WITH_SHOVEL, 4.5, -3),
        HOE("hoe", BlockTags.MINEABLE_WITH_HOE, 1, -1);
        final String id;
        final TagKey<Block> mineable;
        final double damage, speed;
        Kind(String id, TagKey<Block> mineable, double damage, double speed) {
            this.id = id;
            this.mineable = mineable;
            this.damage = damage;
            this.speed = speed;
        }
    }

    static {
        for (Material material : Material.values())
            for (Kind kind : Kind.values())
                TOOLS.add(ITEMS.register(material.id + "_" + kind.id, () -> new NativeTool(material, kind)));
    }
    private SakuraTools() {}
    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
                TOOLS.forEach(tool -> event.accept(tool.get()));
        });
    }

    private static final class NativeTool extends Item {
        private final Material material;
        private final Kind kind;
        NativeTool(Material material, Kind kind) {
            super(properties(material, kind));
            this.material = material;
            this.kind = kind;
        }
        private static Properties properties(Material material, Kind kind) {
            var attributes = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                    kind.damage - 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID,
                    kind.speed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build();
            return new Properties().durability(material.tier.getUses()).attributes(attributes)
                .component(DataComponents.TOOL, material.tier.createToolProperties(kind.mineable));
        }
        @Override public InteractionResult useOn(UseOnContext context) {
            return switch (kind) {
                case AXE -> Items.DIAMOND_AXE.useOn(context);
                case SHOVEL -> Items.DIAMOND_SHOVEL.useOn(context);
                case HOE -> Items.DIAMOND_HOE.useOn(context);
                case PICKAXE -> super.useOn(context);
            };
        }
        @Override public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
            return switch (kind) {
                case AXE -> ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability);
                case SHOVEL -> ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability);
                case HOE -> ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability);
                case PICKAXE -> ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(ability);
            };
        }
        @Override public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity user) {
            if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0)
                stack.hurtAndBreak(1, user, EquipmentSlot.MAINHAND);
            return true;
        }
        @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) { return true; }
        @Override public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            stack.hurtAndBreak(2, attacker, EquipmentSlot.MAINHAND);
        }
        @Override public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) {
            return ingredient.is(material.repair.get()) || super.isValidRepairItem(stack, ingredient);
        }
        @Override public int getEnchantmentValue() {
            return material.tier.getEnchantmentValue();
        }
    }
}

