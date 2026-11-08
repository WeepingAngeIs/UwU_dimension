package dev.uwu.dimension;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid="uwudimension",value=Dist.CLIENT)
public final class GuardianFlightClient {
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event){
        var p=Minecraft.getInstance().player;if(p==null||!p.getPersistentData().contains(GuardianFlight.CLIENT))return;
        var n=p.getPersistentData().getCompound(GuardianFlight.CLIENT);
        if(!p.isAlive()||p.tickCount>n.getInt("Until")){p.noCulling=n.getBoolean("OriginalCulling");p.getPersistentData().remove(GuardianFlight.CLIENT);p.setDeltaMovement(0,0,0);return;}
        // The interpolated third-person camera can be almost one high-speed tick behind the
        // current bounding box. Keep the local body visible while its actual pose interpolates.
        p.noCulling=true;
        p.setDeltaMovement(n.getDouble("X"),n.getDouble("Y"),n.getDouble("Z"));
        p.fallDistance=0;
    }
    @SubscribeEvent public static void trail(ClientTickEvent.Post event){
        var p=Minecraft.getInstance().player;if(p==null||!p.getPersistentData().contains(GuardianFlight.CLIENT))return;
        p.level().addParticle(ParticleTypes.END_ROD,p.getX(),p.getY()-.3,p.getZ(),0,-.5,0);
        p.level().addParticle(ParticleTypes.CHERRY_LEAVES,p.getX()+.4,p.getY(),p.getZ(),0,-.3,0);
    }
}
