package dev.uwu.dimension;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /uwuguardian creeperaggro [true|false]: whether creepers may pick the Blossom Warden as a target. */
public final class GuardianCommands {
    private GuardianCommands() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GuardianCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("uwuguardian")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> {
                    var source = ctx.getSource();
                    var pos = source.getPosition();
                    var near = new net.minecraft.world.phys.AABB(pos, pos).inflate(64);
                    var guard = source.getLevel().getEntitiesOfClass(BlossomWarden.class, near).stream()
                            .min(java.util.Comparator.comparingDouble(g -> g.distanceToSqr(pos)));
                    source.sendSuccess(() -> Component.literal(guard.map(BlossomWarden::combatStatus)
                            .orElse("No loaded Guardian within 64 blocks.")), false);
                    return guard.isPresent() ? 1 : 0;
                }))
                .then(Commands.literal("creeperaggro")
                        .executes(ctx -> {
                            boolean on = GuardianTargeting.creeperAggroOn(ctx.getSource().getLevel());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Creeper aggro to the Blossom Warden is " + (on ? "on." : "off.")), false);
                            return on ? 1 : 0;
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean on = BoolArgumentType.getBool(ctx, "enabled");
                                    GuardianTargeting.setCreeperAggro(ctx.getSource().getLevel(), on, ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Creeper aggro to the Blossom Warden is now " + (on ? "on." : "off.")), true);
                                    return on ? 1 : 0;
                                }))));
    }
}
