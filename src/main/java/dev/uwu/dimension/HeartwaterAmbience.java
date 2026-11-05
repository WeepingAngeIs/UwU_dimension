package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

final class HeartwaterAmbience {
    static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p) || p.isSpectator())return;
        var level=p.serverLevel();
        if(!level.dimension().location().toString().equals("uwudimension:heartwater"))return;
        long t=level.getGameTime();
        if(t%5!=0)return;
        var random=p.getRandom();
        boolean wading=p.isInWater();
        if(p.getY()>70){
            if(t%15!=0)return;
            double x=p.getX()+random.nextDouble()*12-6,z=p.getZ()+random.nextDouble()*12-6;
            double y=p.getY()+1+random.nextDouble()*3;
            level.sendParticles(ParticleTypes.CHERRY_LEAVES,x,y,z,1,.1,.1,.1,.01);
            return;
        }
        if(p.getY()<18 && t%400<55 && !wading){
            // A narrow shoal scatters in sequence toward a motionless observer.
            double distance=12-(t%400)*.18, a=Math.floorDiv(t,400)*2.399963;
            double x=p.getX()+Math.cos(a)*distance,z=p.getZ()+Math.sin(a)*distance;
            if(level.isEmptyBlock(BlockPos.containing(x,p.getY()+.4,z)))
                level.sendParticles(ParticleTypes.GLOW,x,p.getY()+.4,z,5,.22,.09,.22,.055);
        }
        double a=random.nextDouble()*Math.PI*2, r=wading?1.2:3+random.nextDouble()*4;
        double dx=Math.cos(a),dz=Math.sin(a),x=p.getX()+dx*r,z=p.getZ()+dz*r;
        int top=p.getBlockY()+1;
        for(int y=top;y>=top-5;y--){
            var b=BlockPos.containing(x,y,z);
            if(!level.getFluidState(b).is(FluidTags.WATER)||!level.getFluidState(b.above()).isEmpty())continue;
            if(wading){
                for(int i=0;i<4;i++)level.sendParticles(ParticleTypes.END_ROD,x+dx*i*.35,y+1.08,z+dz*i*.35,0,dx*.08,.018,dz*.08,1);
            } else level.sendParticles(ParticleTypes.GLOW,x,y+1.1,z,2,.2,.05,.2,.003);
            break;
        }
    }
}
