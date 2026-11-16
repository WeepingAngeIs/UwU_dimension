package dev.uwu.dimension;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public final class ScytheInput {
    public record Slash() implements CustomPacketPayload {
        public static final Type<Slash> TYPE=new Type<>(Arsenal.id("scythe_slash"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Slash> CODEC=StreamCodec.unit(new Slash());
        @Override public Type<Slash> type(){return TYPE;}
    }
    static void register(RegisterPayloadHandlersEvent event){
        event.registrar("1").playToServer(Slash.TYPE,Slash.CODEC,(message,ctx)->ctx.enqueueWork(()->{
            var p=ctx.player();var stack=p.getMainHandItem();var item=stack.getItem();
            boolean glitch=item instanceof GlitchBow && GlitchBow.isScythe(stack);
            var counter=item instanceof CounterArsenal.CounterItem c && c.entry.shape().equals("scythe")?c.entry:glitch?GlitchBow.counter(stack):null;
            if(!(p.level() instanceof ServerLevel level)||p.isSpectator()||!p.isAlive()||(counter==null&&item!=Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get()&&!glitch)||p.getCooldowns().isOnCooldown(item))return;
            if(p.getPersistentData().getLong("uwu.scythe_flight")>level.getGameTime())return;
            if(glitch){
                if(stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getBoolean("uwu_glitch_pending"))return;
                boolean resistance=counter!=null&&CounterEffects.wardTracked(level,p,counter);
                if(!RelicFlight.castGlitchWave(level,p,stack,counter))return;
                if(counter!=null)GlitchBow.trackWard(level,stack,p,resistance);
            }
            else if(counter==null)RelicFlight.cast(level,p,Arsenal.Relic.VESPER_SCYTHE,true);
            else {CounterEffects.ward(level,p,counter);RelicFlight.castCounter(level,p,counter,true);}
            p.getCooldowns().addCooldown(item,14);
            p.getMainHandItem().hurtAndBreak(1,p,net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }));
    }
}
