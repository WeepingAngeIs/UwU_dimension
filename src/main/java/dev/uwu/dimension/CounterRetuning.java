package dev.uwu.dimension;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Reuses the exact registered form so its target-specific ward and saved stacks remain intact. */
public final class CounterRetuning {
    private static final int PAGE_SIZE = 10;
    private CounterRetuning() {}

    static void register() { NeoForge.EVENT_BUS.addListener(CounterRetuning::commands); }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sakura")
            .then(Commands.literal("counter")
                .executes(ctx -> list(ctx.getSource().getPlayerOrException(), 1))
                .then(Commands.literal("page")
                    .then(Commands.argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> list(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "number")))))
                .then(Commands.argument("form", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CounterArsenal.ROSTER.stream().filter(CounterArsenal::selectable).map(CounterArsenal.Entry::id), builder))
                    .executes(ctx -> retune(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "form")) ? 1 : 0))));
    }

    private static InteractionHand counterHand(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof CounterArsenal.CounterItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof CounterArsenal.CounterItem) return InteractionHand.OFF_HAND;
        return null;
    }

    private static int list(ServerPlayer player, int page) {
        if (counterHand(player) == null) {
            player.sendSystemMessage(Component.literal("Hold a counter weapon to choose its target.").withStyle(ChatFormatting.RED));
            return 0;
        }
        var choices = CounterArsenal.ROSTER.stream().filter(CounterArsenal::selectable).toList();
        int pages = (choices.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page > pages) {
            player.sendSystemMessage(Component.literal("Choose a page from 1 to " + pages + ".").withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Counter targets (" + page + "/" + pages + ") — click a name to retune:").withStyle(ChatFormatting.LIGHT_PURPLE));
        int start = (page - 1) * PAGE_SIZE;
        for (int i = start; i < Math.min(start + PAGE_SIZE, choices.size()); i++) {
            var entry = choices.get(i);
            player.sendSystemMessage(Component.literal("• " + entry.name() + " — answers " + entry.sourceName())
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sakura counter " + entry.id()))));
        }
        var navigation = Component.empty();
        if (page > 1) navigation.append(Component.literal("[Previous] ").withStyle(style -> style.withColor(ChatFormatting.YELLOW)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sakura counter page " + (page - 1)))));
        if (page < pages) navigation.append(Component.literal("[Next]").withStyle(style -> style.withColor(ChatFormatting.YELLOW)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sakura counter page " + (page + 1)))));
        player.sendSystemMessage(navigation);
        return 1;
    }

    /** Replaces only the held stack and carries its complete component patch to the exact target item. */
    public static boolean retune(ServerPlayer player, String id) {
        var hand = counterHand(player);
        var entry = CounterArsenal.entry(id);
        if (hand == null || entry == null) {
            player.sendSystemMessage(Component.literal(hand == null ? "Hold a counter weapon first." : "Unknown counter form.").withStyle(ChatFormatting.RED));
            return false;
        }
        ItemStack current = player.getItemInHand(hand);
        var target = CounterArsenal.ITEMS.get(id).get();
        if (current.getItem() == target) {
            player.sendSystemMessage(Component.literal("Already tuned to " + entry.sourceName() + "."));
            return true;
        }
        if (!CounterArsenal.selectable(entry)) {
            player.sendSystemMessage(Component.literal("This legacy bow form is retired from new acquisition."));
            return false;
        }
        // A different exact item ID is a new acquisition even though it replaces an owned stack.
        // Match the server policy's documented operator/creative exception.
        if (!AcquisitionPolicy.weaponAcquisitionEnabled() && !player.isCreative() && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("This server has disabled acquiring new weapon forms.").withStyle(ChatFormatting.RED));
            return false;
        }
        // Refuse both cooling-down forms: otherwise swapping item IDs would bypass a cast cooldown.
        if (player.getCooldowns().isOnCooldown(current.getItem()) || player.getCooldowns().isOnCooldown(target)) {
            player.sendSystemMessage(Component.literal("Wait for the counter to finish cooling down before retuning.").withStyle(ChatFormatting.RED));
            return false;
        }
        player.setItemInHand(hand, current.transmuteCopy(target));
        player.sendSystemMessage(Component.literal("Counter tuned to " + entry.sourceName() + ".").withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }
}
