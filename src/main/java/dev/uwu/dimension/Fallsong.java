package dev.uwu.dimension;

import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

@EventBusSubscriber(modid = "uwudimension")
public final class Fallsong {
    private static final String DROP = "uwudimension.fallsong_drop";
    private static final double LIFT = 0.66D;
    private static final Predicate<ItemStack> HAS_DROP;
    static {
        CompoundTag charged = new CompoundTag();
        charged.putBoolean(DROP, true);
        HAS_DROP = CustomData.itemMatcher(DataComponents.CUSTOM_DATA, charged);
    }

    private Fallsong() {}

    public static boolean isCharged(ItemStack stack) {
        return stack.is(HeartShields.HEARTWATER_WINDOW_SHIELD.get()) && HAS_DROP.test(stack);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void catchDrop(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack shield = player.getUseItem();
        if (!shield.is(HeartShields.HEARTWATER_WINDOW_SHIELD.get())
                || player.getItemInHand(player.getUsedItemHand()) != shield || isCharged(shield)) return;

        var level = player.serverLevel();
        var damage = event.getDamageContainer();
        var server = level.getServer();
        // Only the stack that intercepted this hit may earn the drop. Vanilla
        // finalizes block amount and durability after the event has returned.
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (event.isCanceled() || damage.getBlockedDamage() <= 0 || shield.isEmpty()
                    || isCharged(shield) || !player.isAlive() || player.level() != level) return;
            CustomData.update(DataComponents.CUSTOM_DATA, shield, data -> data.putBoolean(DROP, true));
            player.getInventory().setChanged();
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.4F, 1.4F);
        }));
    }

    @SubscribeEvent
    public static void jump(LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || !player.onGround()
                || player.isPassenger() || player.isSpectator() || player.getAbilities().flying
                || player.isFallFlying() || player.isSwimming() || player.onClimbable()) return;

        ItemStack shield = player.getOffhandItem();
        if (!isCharged(shield)) {
            shield = player.getMainHandItem();
            if (!isCharged(shield)) return;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, shield, data -> data.remove(DROP));
        player.getInventory().setChanged();
        var velocity = player.getDeltaMovement();
        if (velocity.y < LIFT) {
            player.setDeltaMovement(velocity.x, LIFT, velocity.z);
            // ServerGamePacketListenerImpl invokes jumpFromGround for a real
            // client's ground-to-air transition. Explicitly update the owner,
            // since changing the server-side velocity alone won't lift them.
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
        FallsongEffects.lift(player.serverLevel(), player);
    }
}
