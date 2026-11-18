package dev.uwu.dimension;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HeartShields {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    static final Supplier<ShieldItem> HEARTBRIAR_SHIELD = shield("heartbriar_shield");
    static final Supplier<ShieldItem> HEARTWATER_WINDOW_SHIELD = shield("heartwater_window_shield");
    static final Supplier<ShieldItem> PETALFOLD_SHIELD = shield("petalfold_shield");
    static final List<Supplier<ShieldItem>> SHIELDS =
        List.of(HEARTBRIAR_SHIELD, HEARTWATER_WINDOW_SHIELD, PETALFOLD_SHIELD);

    private HeartShields() {}

    private static Supplier<ShieldItem> shield(String id) {
        return ITEMS.register(id, () -> new GoddessShield());
    }

    private static final class GoddessShield extends ShieldItem {
        GoddessShield() {
            super(new Item.Properties().durability(336));
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> lines, TooltipFlag flag) {
            super.appendHoverText(stack, context, lines, flag);
            lines.add(Component.translatable("tooltip.uwudimension.goddess_shield")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        }
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        HeartShieldEffects.register(bus);
        bus.addListener(HeartShields::addToCombatTab);
    }

    private static void addToCombatTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            SHIELDS.forEach(shield -> event.accept(shield.get()));
        }
    }
}
