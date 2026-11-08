package dev.uwu.test;
import dev.uwu.dimension.*;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
public final class FlightProof {
    static int phase,ticks,samples;static LivingEntity flyer,blocked;static double previous,apex;static boolean reload;
    static UUID id;static final Path FILE=Path.of("flight.properties");static Properties saved;
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    static boolean tick(ServerLevel l)throws Exception{
        ticks++;
        if(phase==0){
            reload=Boolean.getBoolean("uwu.flightReload");
            if(reload){saved=new Properties();try(var in=Files.newInputStream(FILE)){saved.load(in);}id=UUID.fromString(saved.getProperty("id"));l.setChunkForced(Integer.parseInt(saved.getProperty("cx")),Integer.parseInt(saved.getProperty("cz")),true);}
            for(int x=-1;x<=7;x++)for(int z=-1;z<=1;z++)l.setChunkForced(x,z,true);
            phase=1;ticks=0;return false;
        }
        if(phase==1&&ticks>=30){
            if(reload){flyer=(LivingEntity)l.getEntity(id);check(flyer!=null,"Mid-flight saved entity missing");check(flyer.getPersistentData().contains(GuardianFlight.STATE),"Mid-flight saved state lost");previous=flyer.getY();phase=3;ticks=0;return false;}
            flyer=EntityType.HUSK.create(l);flyer.moveTo(0,250,0,0,0);l.addFreshEntity(flyer);
            blocked=EntityType.HUSK.create(l);blocked.moveTo(8.5,250,.5,0,0);l.addFreshEntity(blocked);
            for(int x=6;x<=11;x++)for(int z=-2;z<=2;z++)l.setBlockAndUpdate(new BlockPos(x,253,z),Blocks.OBSIDIAN.defaultBlockState());
            GuardianFlight.start(flyer,new Vec3(96,8250,0));GuardianFlight.start(blocked,new Vec3(8.5,8250,.5));
            previous=flyer.getY();phase=2;ticks=0;return false;
        }
        if(phase==2||phase==3){
            double y=flyer.getY(),step=y-previous;
            check(step<55,"Unobstructed flight used discontinuous position jump: "+step);
            check(flyer.getPersistentData().getInt(GuardianFlight.RECOVERIES)==0,"Unobstructed flight used recovery teleport");
            if(step>.5)samples++;
            if(ticks%20==0)System.out.println("MOB FLIGHT SAMPLE y="+y+" step="+step+" recovery=0");
            previous=y;
            if(phase==2&&ticks==65){
                check(samples>50&&y>2000,"Ordinary mob ticks did not produce sustained ascent");
                check(blocked.getPersistentData().getInt(GuardianFlight.RECOVERIES)==1&&!blocked.getPersistentData().contains(GuardianFlight.STATE),"Roof obstruction did not invoke single measured recovery");
                saved=new Properties();saved.setProperty("id",flyer.getUUID().toString());saved.setProperty("cx",""+flyer.chunkPosition().x);saved.setProperty("cz",""+flyer.chunkPosition().z);
                try(var out=Files.newOutputStream(FILE)){saved.store(out,"Real mid-flight server restart");}
                System.out.println("FLIGHT RESTART READY blocked recovery=1 clear flight recovery=0");return true;
            }
            if(phase==3&&!flyer.getPersistentData().contains(GuardianFlight.STATE)){
                check(y>8248&&y<8305,"Flight ended at wrong height "+y);check(samples>50,"No resumed native movement");apex=y;phase=4;ticks=0;
            }
            check(ticks<500,"Flight never completed");
        }
        if(phase==4&&ticks>25){check(flyer.getY()<apex-5,"Natural descent did not resume");System.out.println("UWU FLIGHT SERVER PASSED native ascent, zero recovery, obstruction fallback, real mid-flight restart, natural descent");return true;}
        return false;
    }
}
