package dev.uwu.dimension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class CupidMinionClient {
    @SubscribeEvent
    public static void steer(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player) return;
        var data = player.getPersistentData();
        if (!data.contains("uwudimension.cupid_control")) return;
        var control = data.getCompound("uwudimension.cupid_control");
        double dx = control.getDouble("X") - player.getX();
        double dy = control.getDouble("Y") + .4 - player.getY();
        double dz = control.getDouble("Z") - player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double stop = control.getDouble("Stop");

        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, Math.max(.001, horizontal)) * (180.0 / Math.PI)));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.setXRot(pitch);

        var input = event.getInput();
        input.forwardImpulse = distance > stop ? 1.0F : 0.0F;
        input.leftImpulse = 0.0F;
        input.shiftKeyDown = false;
        input.jumping = distance > stop && player.onGround()
            && (player.horizontalCollision || dy > .8);
    }

    @SubscribeEvent
    public static void blockManualActions(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player.getPersistentData().contains("uwudimension.cupid_control")) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (event.isAttack() && player.getMainHandItem().is(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            PacketDistributor.sendToServer(new CupidMinions.Designate());
        }
    }
}
