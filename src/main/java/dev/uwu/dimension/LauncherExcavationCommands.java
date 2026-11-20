package dev.uwu.dimension;

import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

final class LauncherExcavationCommands {
    private LauncherExcavationCommands() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("uwu")
                .then(Commands.literal("launcher").requires(LauncherExcavationControl::hasPermission)
                        .then(Commands.literal("excavation")
                                .then(Commands.literal("on").executes(context -> change(context.getSource(), true)))
                                .then(Commands.literal("off").executes(context -> change(context.getSource(), false)))
                                .then(Commands.literal("status").executes(context -> report(context.getSource(),
                                        LauncherExcavationControl.status(context.getSource()), false))))));
    }

    private static int change(CommandSourceStack source, boolean enabled) {
        return report(source, LauncherExcavationControl.set(source, enabled), true);
    }

    private static int report(CommandSourceStack source, Optional<LauncherExcavationControl.Status> result,
                              boolean broadcast) {
        if (result.isEmpty()) {
            source.sendFailure(Component.literal("Heart Launcher excavation can only be managed by a server operator."));
            return 0;
        }
        var status = result.get();
        if (status.source() == LauncherExcavationControl.Source.UNAVAILABLE) {
            source.sendFailure(Component.literal("Heart Launcher excavation is disabled because its world setting is unreadable."));
            return 0;
        }
        String value = status.enabled() ? "on" : "off";
        String origin = status.source() == LauncherExcavationControl.Source.WORLD_OVERRIDE
                ? "saved world override" : "launcher config";
        source.sendSuccess(() -> Component.literal("Heart Launcher excavation is " + value + " (" + origin + ")."), broadcast);
        return 1;
    }
}
