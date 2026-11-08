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
            var p=ctx.player();var item=p.getMainHandItem().getItem();
            var counter=item instanceof CounterArsenal.CounterItem c && c.entry.shape().equals("scythe")?c.entry:null;
            if(!(p.level() instanceof ServerLevel level)||p.isSpectator()||!p.isAlive()||(counter==null&&item!=Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get())||p.getCooldowns().isOnCooldown(item))return;
            if(p.getPersistentData().getLong("uwu.scythe_flight")>level.getGameTime())return;
            if(counter==null)RelicFlight.cast(level,p,Arsenal.Relic.VESPER_SCYTHE,true);
            else {CounterEffects.ward(level,p,counter);RelicFlight.castCounter(level,p,counter,true);}
            p.getCooldowns().addCooldown(item,14);
            p.getMainHandItem().hurtAndBreak(1,p,net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }));
    }
}
