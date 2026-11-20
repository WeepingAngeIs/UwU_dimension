package dev.uwu.dimension;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid="uwudimension",value=Dist.CLIENT)
public final class ScytheClientInput {
    private static java.lang.ref.WeakReference<net.minecraft.world.level.Level> lastLevel=new java.lang.ref.WeakReference<>(null);
    private static long lastWaveTick=Long.MIN_VALUE;

    /** Called before the optional combat mod takes over Minecraft.startAttack. */
    public static void requestWave() {
        var mc=Minecraft.getInstance();
        var player=mc.player;
        if(player==null||mc.screen!=null||player.isSpectator()||player.isUsingItem()||player.isHandsBusy())return;
        var stack=player.getMainHandItem();
        boolean scythe=stack.is(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get())
                || stack.getItem() instanceof CounterArsenal.CounterItem item&&item.entry.shape().equals("scythe")
                || GlitchBow.isScythe(stack);
        if(!scythe||player.getCooldowns().isOnCooldown(stack.getItem()))return;
        long tick=player.level().getGameTime();
        // The early keybinding path and the normal NeoForge callback share one request.
        if(lastLevel.get()==player.level()&&lastWaveTick==tick)return;
        lastLevel=new java.lang.ref.WeakReference<>(player.level());
        lastWaveTick=tick;
        PacketDistributor.sendToServer(new ScytheInput.Slash());
    }

    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered event){
        var p=Minecraft.getInstance().player;
        if(event.isAttack()&&p!=null&&(p.getMainHandItem().is(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get()) || p.getMainHandItem().getItem() instanceof CounterArsenal.CounterItem c && c.entry.shape().equals("scythe") || GlitchBow.isScythe(p.getMainHandItem()))){
            event.setCanceled(true);event.setSwingHand(true);requestWave();
        }
    }
}
