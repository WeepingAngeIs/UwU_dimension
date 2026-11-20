package dev.uwu.dimension;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Standalone support for qualified melee clicks that vanilla's normal pick would miss. */
public final class DawnpetalVanillaReach {
    private static final int AIR = -1;
    private static final int INELIGIBLE = -2;
    private static long nextClientSequence;
    private record Seen(long sequence, long checkedAt, long nextSwing) {}
    private static final Map<ServerPlayer, Seen> LAST = new WeakHashMap<>();

    private record Click(long sequence, long tick, ResourceLocation dimension, int slot, UUID axe, int target)
            implements CustomPacketPayload {
        static final Type<Click> TYPE = new Type<>(UwUDimension.id("dawnpetal_click"));
        static final StreamCodec<RegistryFriendlyByteBuf, Click> CODEC = StreamCodec.of(
                (b,c) -> {
                    b.writeVarLong(c.sequence); b.writeLong(c.tick); b.writeResourceLocation(c.dimension);
                    b.writeVarInt(c.slot); b.writeUUID(c.axe); b.writeVarInt(c.target);
                },
                b -> new Click(b.readVarLong(), b.readLong(), b.readResourceLocation(),
                        b.readVarInt(), b.readUUID(), b.readVarInt()));
        @Override public Type<Click> type() { return TYPE; }
    }

    private DawnpetalVanillaReach() {}

    public static void register(IEventBus bus) {
        bus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").playToServer(Click.TYPE, Click.CODEC,
                (click, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) receive(player, click);
                }));
    }

    static UUID syncedIdentity(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.hasUUID("uwu_revival_id") ? data.getUUID("uwu_revival_id") : null;
    }

    private static UUID axeIdentity(Player p) {
        ItemStack stack = p.getMainHandItem();
        if (!stack.is(RevivalBlossoms.AXE.get()) || !p.isAlive() || p.isRemoved() || p.isSpectator()
                || p.isUsingItem() || p.getAttackStrengthScale(0) < 1F) return null;
        return syncedIdentity(stack);
    }

    // Same eye ray, pickable-entity predicate and inflated search volume as GameRenderer.pick.
    // Ordinary block clicks have priority, and a closer entity occludes a farther one.
    private static int pick(Player p) {
        double normal = p.entityInteractionRange();
        double extended = normal + DawnpetalReach.BONUS;
        double blockRange = p.blockInteractionRange();
        if (!Double.isFinite(extended) || normal < 0 || !Double.isFinite(blockRange)) return INELIGIBLE;
        var eye = p.getEyePosition(1);
        var block = p.pick(Math.max(extended, blockRange), 1, false);
        double limitSquared = extended * extended;
        if (block.getType() == HitResult.Type.BLOCK) {
            double distance = eye.distanceToSqr(block.getLocation());
            if (distance <= blockRange * blockRange) return INELIGIBLE;
            limitSquared = Math.min(limitSquared, distance);
        }
        var ray = p.getViewVector(1).scale(extended);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, eye.add(ray),
                p.getBoundingBox().expandTowards(ray).inflate(1),
                e -> !e.isSpectator() && e.isPickable(), limitSquared);
        if (hit == null) return AIR;
        double hitDistance = eye.distanceToSqr(hit.getLocation());
        if (hitDistance <= normal * normal || hitDistance > limitSquared
                || !(hit.getEntity() instanceof LivingEntity target) || !target.isAlive()
                || !target.isAttackable() || !p.hasLineOfSight(target)) return INELIGIBLE;
        return target.getId();
    }

    /** Response to an uncancelled, explicit startAttack miss. Returning true replaces just that miss. */
    public static boolean click(Player player) {
        if (ModList.get().isLoaded("bettercombat")) return false;
        UUID axe = axeIdentity(player);
        if (axe == null) return false;
        int target = pick(player);
        if (target == INELIGIBLE) return false;
        PacketDistributor.sendToServer(new Click(++nextClientSequence, player.level().getGameTime(),
                player.level().dimension().location(), player.getInventory().selected, axe, target));
        // Vanilla handles its original empty click (animation and cooldown) in the air case.
        if (target == AIR) return false;
        player.resetAttackStrengthTicker();
        return true;
    }

    private static void receive(ServerPlayer player, Click click) {
        if (ModList.get().isLoaded("bettercombat") || !player.serverLevel().getServer().isSameThread()
                || click.target < AIR || click.slot != player.getInventory().selected
                || !player.level().dimension().location().equals(click.dimension)) return;
        long localTime = player.level().getGameTime();
        long now = player.serverLevel().getServer().overworld().getGameTime();
        var previous = LAST.get(player);
        if (click.tick < localTime - 40 || click.tick > localTime + 2 || click.sequence <= 0
                || previous != null && (click.sequence <= previous.sequence || previous.checkedAt == now
                        || now < previous.nextSwing)) return;
        if (!click.axe.equals(axeIdentity(player))) return;
        LAST.put(player, new Seen(click.sequence, now, now));
        if (pick(player) != click.target) return;
        long nextSwing = now + Math.max(1, (int)Math.ceil(player.getCurrentItemAttackStrengthDelay()));
        LAST.put(player, new Seen(click.sequence, now, nextSwing));
        if (click.target == AIR) {
            DawnpetalSwing.start(player, 1, 6);
            DawnpetalSwing.strike(player);
            return;
        }
        var target = player.serverLevel().getEntity(click.target);
        if (!(target instanceof LivingEntity)) return;
        var packet = ServerboundInteractPacket.createAttackPacket(target, player.isShiftKeyDown());
        var connection = player.connection;
        // The normal packet handler retains its PvP, protection, permission and combat behavior.
        // A successful hit produces exactly one LivingDamageEvent.Post presentation.
        DawnpetalReach.forward(connection, packet, player.getMainHandItem(), () -> connection.handleInteract(packet));
    }
}
