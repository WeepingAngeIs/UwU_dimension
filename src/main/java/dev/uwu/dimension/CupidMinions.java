package dev.uwu.dimension;

import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-authoritative charm commands and player minion control for the Cupid Bow. */
final class CupidMinions {
    static final String COMMAND_TARGET = "uwudimension.cupid_target";
    static final String COMMAND_END = "uwudimension.cupid_target_end";
    private static final String CLIENT_CONTROL = "uwudimension.cupid_control";
    private static final double COMMAND_RANGE = 512.0;
    private static final int CHARM_TICKS = 800;

    record Designate() implements CustomPacketPayload {
        static final Type<Designate> TYPE = new Type<>(UwUDimension.id("cupid_designate"));
        static final StreamCodec<RegistryFriendlyByteBuf, Designate> CODEC = StreamCodec.unit(new Designate());
        @Override public Type<Designate> type() { return TYPE; }
    }

    record Control(boolean active, double x, double y, double z, double stopDistance) implements CustomPacketPayload {
        static final Type<Control> TYPE = new Type<>(UwUDimension.id("cupid_minion_control"));
        static final StreamCodec<RegistryFriendlyByteBuf, Control> CODEC = StreamCodec.of(
            (buffer, message) -> {
                buffer.writeBoolean(message.active());
                if (message.active()) {
                    buffer.writeDouble(message.x());
                    buffer.writeDouble(message.y());
                    buffer.writeDouble(message.z());
                    buffer.writeDouble(message.stopDistance());
                }
            },
            buffer -> buffer.readBoolean()
                ? new Control(true, buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
                : new Control(false, 0, 0, 0, 0));
        @Override public Type<Control> type() { return TYPE; }
    }

    static void register(IEventBus modBus) {
        modBus.addListener(CupidMinions::registerPayloads);
        NeoForge.EVENT_BUS.addListener(CupidMinions::death);
        NeoForge.EVENT_BUS.addListener(CupidMinions::loggedOut);
        NeoForge.EVENT_BUS.addListener(CupidMinions::changedDimension);
        NeoForge.EVENT_BUS.addListener(CupidMinions::loggedIn);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
            .playToServer(Designate.TYPE, Designate.CODEC, (message, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer owner) designate(owner);
            }))
            .playToClient(Control.TYPE, Control.CODEC, (message, context) -> context.enqueueWork(() -> {
                Player player = context.player();
                if (!message.active()) {
                    player.getPersistentData().remove(CLIENT_CONTROL);
                    return;
                }
                var state = new net.minecraft.nbt.CompoundTag();
                state.putDouble("X", message.x());
                state.putDouble("Y", message.y());
                state.putDouble("Z", message.z());
                state.putDouble("Stop", message.stopDistance());
                player.getPersistentData().put(CLIENT_CONTROL, state);
            }));
    }

    static boolean charm(ServerLevel level, Player keeper, ServerPlayer target, boolean pastelCupid) {
        if (target == keeper || target.isCreative() || target.isSpectator() || !keeper.canHarmPlayer(target)) return false;
        var data = target.getPersistentData();
        if (data.hasUUID(HeartMagic.OWNER)
            && level.getGameTime() < data.getLong(HeartMagic.END)
            && !data.getUUID(HeartMagic.OWNER).equals(keeper.getUUID())) return false;

        target.addTag(HeartMagic.OWNER);
        data.putUUID(HeartMagic.OWNER, keeper.getUUID());
        data.putBoolean("uwu.pastel_cupid", pastelCupid);
        data.putLong(HeartMagic.END, level.getGameTime() + CHARM_TICKS);
        data.remove(COMMAND_TARGET);
        data.remove(COMMAND_END);
        level.sendParticles(CupidParticles.PASTEL_HEART.get(), target.getX(), target.getEyeY(), target.getZ(),
            28, .55, .45, .55, .045);
        target.displayClientMessage(net.minecraft.network.chat.Component.literal("You have been charmed into a temporary minion."), true);
        keeper.displayClientMessage(net.minecraft.network.chat.Component.literal(target.getGameProfile().getName() + " is charmed for 40 seconds."), true);
        sendControl(target, keeper, 3.0);
        return true;
    }

    static void tick(ServerLevel level, ServerPlayer minion) {
        var data = minion.getPersistentData();
        if (!minion.getTags().contains(HeartMagic.OWNER) || !data.hasUUID(HeartMagic.OWNER)) return;
        if (!minion.isAlive() || minion.isSpectator() || level.getGameTime() >= data.getLong(HeartMagic.END)) {
            release(minion);
            return;
        }
        if (minion.tickCount % 8 == 0)
            level.sendParticles(CupidParticles.PASTEL_HEART.get(), minion.getX(), minion.getEyeY(), minion.getZ(),
                2, .22, .25, .22, .015);

        if (minion.tickCount % 2 != 0) return;
        Player owner = level.getServer().getPlayerList().getPlayer(data.getUUID(HeartMagic.OWNER));
        if (!(owner instanceof ServerPlayer keeper) || !keeper.isAlive() || keeper.serverLevel() != level) {
            release(minion);
            return;
        }

        LivingEntity foe = assignedTarget(level, keeper, minion);
        if (foe == null) {
            foe = keeper.tickCount - keeper.getLastHurtByMobTimestamp() < 220 ? keeper.getLastHurtByMob() : null;
            if (!validTarget(keeper, foe))
                foe = keeper.tickCount - keeper.getLastHurtMobTimestamp() < 220 ? keeper.getLastHurtMob() : null;
            if (!validTarget(keeper, foe)) foe = null;
        }

        LivingEntity destination = foe == null ? keeper : foe;
        double stopDistance = foe == null ? 3.0 : 1.7;
        sendControl(minion, destination, stopDistance);

        if (foe != null && minion.tickCount % 20 == 0 && minion.distanceToSqr(foe) <= 7.0
            && minion.hasLineOfSight(foe) && validTarget(keeper, foe)) {
            minion.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, foe.position());
            minion.swing(InteractionHand.MAIN_HAND, true);
            minion.attack(foe);
        }
    }

    static LivingEntity assignedTarget(ServerLevel level, Player keeper, LivingEntity minion) {
        var data = minion.getPersistentData();
        if (!data.hasUUID(COMMAND_TARGET)) return null;
        if (level.getGameTime() >= data.getLong(COMMAND_END)
            || !(level.getEntity(data.getUUID(COMMAND_TARGET)) instanceof LivingEntity target)
            || !validTarget(keeper, target)) {
            data.remove(COMMAND_TARGET);
            data.remove(COMMAND_END);
            return null;
        }
        return target;
    }

    private static void designate(ServerPlayer owner) {
        if (owner.isSpectator() || !owner.getMainHandItem().is(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get())) return;
        LivingEntity target = rayTarget(owner);
        if (target == null || !validTarget(owner, target)) {
            owner.displayClientMessage(net.minecraft.network.chat.Component.literal("No eligible target in your sights."), true);
            return;
        }
        ServerLevel level = owner.serverLevel();
        int ordered = 0;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity minion) || minion == owner) continue;
            var data = minion.getPersistentData();
            if (!minion.getTags().contains(HeartMagic.OWNER) || !data.hasUUID(HeartMagic.OWNER)
                || !data.getUUID(HeartMagic.OWNER).equals(owner.getUUID())
                || level.getGameTime() >= data.getLong(HeartMagic.END)) continue;
            data.putUUID(COMMAND_TARGET, target.getUUID());
            data.putLong(COMMAND_END, data.getLong(HeartMagic.END));
            if (minion instanceof Mob mob) mob.setTarget(target);
            if (minion instanceof ServerPlayer player) sendControl(player, target, 1.7);
            ordered++;
        }
        if (ordered == 0) {
            owner.displayClientMessage(net.minecraft.network.chat.Component.literal("You have no charmed allies to command."), true);
            return;
        }
        level.sendParticles(CupidParticles.PASTEL_HEART.get(), target.getX(), target.getEyeY(), target.getZ(),
            10, .35, .35, .35, .025);
        owner.displayClientMessage(net.minecraft.network.chat.Component.literal("Your charmed allies are targeting "
            + target.getDisplayName().getString() + "."), true);
    }

    private static LivingEntity rayTarget(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(COMMAND_RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) end = blockHit.getLocation();
        AABB search = player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.25);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity candidate : level.getEntities(player, search, entity -> entity instanceof LivingEntity living
            && living.isAlive() && validTarget(player, living))) {
            Optional<Vec3> intersection = candidate.getBoundingBox().inflate(.3).clip(start, end);
            if (intersection.isEmpty()) continue;
            double distance = start.distanceToSqr(intersection.get());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = (LivingEntity) candidate;
            }
        }
        return best;
    }

    private static boolean validTarget(Player keeper, LivingEntity target) {
        return target != null && target.isAlive() && target != keeper && !HeartMagic.friendly(keeper, target)
            && (!(target instanceof Player other) || keeper.canHarmPlayer(other));
    }

    private static void sendControl(ServerPlayer minion, LivingEntity target, double stopDistance) {
        PacketDistributor.sendToPlayer(minion, new Control(true, target.getX(), target.getY(), target.getZ(), stopDistance));
    }

    private static void release(ServerPlayer player) {
        player.removeTag(HeartMagic.OWNER);
        var data = player.getPersistentData();
        data.remove(HeartMagic.OWNER);
        data.remove(HeartMagic.END);
        data.remove("uwu.pastel_cupid");
        data.remove(COMMAND_TARGET);
        data.remove(COMMAND_END);
        PacketDistributor.sendToPlayer(player, new Control(false, 0, 0, 0, 0));
        if (player.level() instanceof ServerLevel level)
            level.sendParticles(ParticleTypes.CHERRY_LEAVES, player.getX(), player.getY() + .8, player.getZ(), 10,
                .28, .35, .28, .025);
    }

    private static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getTags().contains(HeartMagic.OWNER)) release(player);
    }

    private static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getTags().contains(HeartMagic.OWNER)) release(player);
    }

    private static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getTags().contains(HeartMagic.OWNER)) release(player);
    }

    private static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var data = player.getPersistentData();
        if (player.getTags().contains(HeartMagic.OWNER) && data.hasUUID(HeartMagic.OWNER)
            && player.serverLevel().getGameTime() < data.getLong(HeartMagic.END)) {
            Player owner = player.serverLevel().getServer().getPlayerList().getPlayer(data.getUUID(HeartMagic.OWNER));
            if (owner instanceof ServerPlayer keeper && keeper.serverLevel() == player.serverLevel()) sendControl(player, keeper, 3.0);
            else release(player);
        }
    }
}
