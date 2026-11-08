package dev.uwu.dimension;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid="uwudimension",value=Dist.CLIENT)
public final class ScytheClientInput {
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered event){
        var p=Minecraft.getInstance().player;
        if(event.isAttack()&&p!=null&&p.getMainHandItem().is(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get())){
            event.setCanceled(true);event.setSwingHand(true);PacketDistributor.sendToServer(new ScytheInput.Slash());
        }
    }
}
