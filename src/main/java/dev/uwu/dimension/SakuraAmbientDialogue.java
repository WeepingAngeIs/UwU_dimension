package dev.uwu.dimension;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Private, opt-in chat conversations with eligible Sakura Reach residents. */
@EventBusSubscriber(modid = "uwudimension")
public final class SakuraAmbientDialogue {
    private static final int SESSION_TICKS = 600;
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new IdentityHashMap<>();

    private record Session(UUID ticket, UUID target, ResourceKey<Level> dimension,
                           String scene, int openedAt, Set<String> choices) {}

    private SakuraAmbientDialogue() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var content = AmbientDialogueText.load();
        event.getDispatcher().register(Commands.literal("sakuraambient")
                .requires(SakuraAmbientDialogue::directPlayer)
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("talk").executes(ctx -> open(ctx.getSource(), content)))
                .then(Commands.literal("choose")
                        .then(Commands.argument("ticket", UuidArgument.uuid())
                                .then(Commands.argument("topic", StringArgumentType.word())
                                        .executes(ctx -> choose(ctx.getSource(), content,
                                                UuidArgument.getUuid(ctx, "ticket"),
                                                StringArgumentType.getString(ctx, "topic"))))))
                .then(Commands.literal("leave")
                        .then(Commands.argument("ticket", UuidArgument.uuid())
                                .executes(ctx -> leave(ctx.getSource(), UuidArgument.getUuid(ctx, "ticket"))))));
    }

    private static boolean directPlayer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player && source.source == player
                && source.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Look at a resident and use /sakuraambient talk."), false);
        return 1;
    }

    private static Map<UUID, Session> sessions(MinecraftServer server) {
        return SESSIONS.computeIfAbsent(server, unused -> new HashMap<>());
    }

    private static Entity target(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved() || player.isSleeping() || player.isSpectator()) return null;
        Vec3 eye = player.getEyePosition();
        double reach = player.entityInteractionRange();
        var block = player.pick(reach, 1.0F, false);
        Vec3 end = block.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(player, eye, end, new AABB(eye, end).inflate(1),
                entity -> entity != player && entity.isPickable() && !entity.isSpectator(), eye.distanceToSqr(end));
        if (hit == null) return null;
        return hit.getEntity();
    }

    private static int open(CommandSourceStack source, AmbientDialogueText content) {
        if (!directPlayer(source)) return 0;
        ServerPlayer player = source.getPlayer();
        sessions(source.getServer()).remove(player.getUUID());
        Entity resident = target(player);
        String sceneId = resident == null ? null : AmbientResidents.sceneFor(player, resident);
        if (sceneId == null) {
            player.sendSystemMessage(Component.literal("Look directly at an available Sakura Reach resident within reach."));
            return 0;
        }
        var scene = content.scene(sceneId);
        boolean crow = sceneId.equals("dusk_village") && AmbientResidents.hasCrow(player, resident);
        Set<String> choices = new HashSet<>();
        for (var choice : scene.choices()) {
            if (!choice.needsCrow() || crow) choices.add(choice.id());
        }
        UUID ticket = UUID.randomUUID();
        var session = new Session(ticket, resident.getUUID(), player.serverLevel().dimension(), sceneId,
                source.getServer().getTickCount(), Set.copyOf(choices));
        sessions(source.getServer()).put(player.getUUID(), session);
        player.sendSystemMessage(scene.opening().component());
        player.sendSystemMessage(Component.literal("Choose a response, or leave the conversation."));
        for (var choice : scene.choices()) {
            if (!choices.contains(choice.id())) continue;
            String command = "/sakuraambient choose " + ticket + " " + choice.id();
            player.sendSystemMessage(Component.literal(choice.label() + " [" + choice.id() + "]")
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))));
        }
        String leave = "/sakuraambient leave " + ticket;
        player.sendSystemMessage(Component.literal("[Leave]").withStyle(style ->
                style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, leave))));
        return 1;
    }

    private static int choose(CommandSourceStack source, AmbientDialogueText content, UUID ticket, String topic) {
        if (!directPlayer(source)) return 0;
        ServerPlayer player = source.getPlayer();
        Map<UUID, Session> sessions = sessions(source.getServer());
        Session session = sessions.get(player.getUUID());
        if (session == null || !session.ticket().equals(ticket)) return stale(player);
        sessions.remove(player.getUUID());
        if (Integer.compareUnsigned(source.getServer().getTickCount() - session.openedAt(), SESSION_TICKS) > 0
                || !player.serverLevel().dimension().equals(session.dimension())
                || !session.choices().contains(topic)) return stale(player);
        Entity resident = target(player);
        if (resident == null || !resident.getUUID().equals(session.target())
                || !session.scene().equals(AmbientResidents.sceneFor(player, resident))) return stale(player);
        var scene = content.scene(session.scene());
        for (var choice : scene.choices()) {
            if (!choice.id().equals(topic)) continue;
            if (choice.needsCrow() && !AmbientResidents.hasCrow(player, resident)) return stale(player);
            player.sendSystemMessage(choice.reply().component());
            return 1;
        }
        return stale(player);
    }

    private static int leave(CommandSourceStack source, UUID ticket) {
        if (!directPlayer(source)) return 0;
        ServerPlayer player = source.getPlayer();
        Map<UUID, Session> sessions = sessions(source.getServer());
        Session session = sessions.get(player.getUUID());
        if (session == null || !session.ticket().equals(ticket)) return stale(player);
        sessions.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("Conversation closed."));
        return 1;
    }

    private static int stale(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("That conversation has ended. Look at the resident and use /sakuraambient talk."));
        return 0;
    }

    private static void invalidate(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var active = SESSIONS.get(serverPlayer.serverLevel().getServer());
        if (active != null) active.remove(player.getUUID());
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { invalidate(event.getEntity()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { invalidate(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { invalidate(event.getEntity()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); }
}
