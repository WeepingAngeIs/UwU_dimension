package dev.uwu.test;

import dev.uwu.dimension.BlossomWarden;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Owns one isolated disposable chunk to observe real entity unload, reload and a second dedicated JVM. */
final class GuardianFamilyDisk {
    static final ChunkPos CHUNK=new ChunkPos(487,331);
    private static final Path RECORD=Path.of("guardian-family-checkpoint.properties");
    private static final List<String> MEMBERS=List.of("male","girl","son","daughter");
    private static Properties state;
    private static long waitSince=-1,unloadedAt=-1;
    private static int step;
    private GuardianFamilyDisk(){}
    private static void check(boolean ok,String why){GuardianFamilyProof.check(ok,"world persistence "+why);}
    static void seed(ServerLevel level,GuardianFamilyProof.Family family)throws IOException{
        var home=new BlockPos(CHUNK.getMiddleBlockX(),level.getMaxBuildHeight()-60,CHUNK.getMiddleBlockZ());
        GuardianFamilyProof.floor(level,home);
        state=new Properties();int i=0;
        for(var g:family.all()){
            String key=MEMBERS.get(i);
            var pos=home.offset((i%2)*3-2,0,(i/2)*3-2);
            g.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            g.setNoGravity(true);g.setNoAi(true);
            check(g.chunkPosition().equals(CHUNK)&&level.getEntity(g.getUUID())==g,"arrive "+key);
            state.setProperty(key,g.getUUID().toString());
            state.setProperty(key+".cooldown",Long.toString(g.familyRestUntil()));
            state.setProperty(key+".remaining",Integer.toString(g.familyGrowthTicks()));
            i++;
        }
        state.setProperty("clock",Long.toString(level.getGameTime()));save();
        System.out.println("OWL GUARDIAN WORLD SAVE PREPARED "+state);
    }
    private static void save()throws IOException{
        try(var out=Files.newOutputStream(RECORD)){state.store(out,"Production births on the first dedicated JVM");}
    }
    private static BlossomWarden member(ServerLevel level,String key){
        var e=level.getEntity(UUID.fromString(state.getProperty(key)));
        return e instanceof BlossomWarden g?g:null;
    }
    private static void inspect(ServerLevel level,long allowedAgeDelta,String kind){
        for(String key:MEMBERS){
            var guardian=member(level,key);check(guardian!=null&&guardian.isAlive()&&guardian.chunkPosition().equals(CHUNK),kind+" missing "+key);
            boolean baby=key.equals("son")||key.equals("daughter");
            check(guardian.isBaby()==baby && guardian.female()==(key.equals("girl")||key.equals("daughter")),kind+" changed age/sex "+key);
            check(guardian.familyRestUntil()==Long.parseLong(state.getProperty(key+".cooldown")),kind+" changed cooldown "+key);
            if(baby){
                var n=guardian.saveWithoutId(new CompoundTag()).getCompound("OwlFamily");
                check(n.getUUID("Mother").toString().equals(state.getProperty("girl"))&&n.getUUID("Father").toString().equals(state.getProperty("male")),kind+" changed parent UUIDs");
                long initial=Long.parseLong(state.getProperty(key+".remaining")),now=guardian.familyGrowthTicks();
                check(now>0&&now<=initial&&initial-now<=allowedAgeDelta,kind+" advanced unloaded "+key+" initial="+initial+" current="+now+" max delta="+allowedAgeDelta);
            }
        }
    }
    static boolean roundTrip(ServerLevel level)throws IOException{
        if(state==null)throw new AssertionError("Guardian persistence family was never seeded");
        long now=level.getGameTime();
        if(waitSince<0)waitSince=now;
        check(now-waitSince<1500,"timed out in chunk unload phase "+step);
        if(step==0){
            for(String key:List.of("son","daughter")){
                var child=member(level,key);
                if(child!=null)state.setProperty(key+".remaining",Integer.toString(child.familyGrowthTicks()));
            }
            if(level.areEntitiesLoaded(CHUNK.toLong()) || MEMBERS.stream().anyMatch(k->member(level,k)!=null))return false;
            unloadedAt=now;step=1;return false;
        }
        if(step==1){
            check(!level.areEntitiesLoaded(CHUNK.toLong())&&MEMBERS.stream().allMatch(k->member(level,k)==null),"chunk reloaded before request");
            if(now-unloadedAt<85)return false;
            check(level.setChunkForced(CHUNK.x,CHUNK.z,true),"dedicated chunk unexpectedly held");step=2;return false;
        }
        if(!level.areEntitiesLoaded(CHUNK.toLong())||MEMBERS.stream().anyMatch(k->member(level,k)==null))return false;
        try{
            // The release and reacquisition of chunk tickets are asynchronous. The full period observed
            // with no entity storage must contribute zero age, regardless of how long ticket propagation took.
            long total=now-Long.parseLong(state.getProperty("clock"));
            inspect(level,Math.max(0,total-85)+2,"chunk reload");
            for(String key:List.of("son","daughter"))state.setProperty(key+".remaining",Integer.toString(member(level,key).familyGrowthTicks()));
            state.setProperty("clock",Long.toString(now));save();
            System.out.println("OWL GUARDIAN CHUNK UNLOAD PASS observed absent 85 ticks; same four UUIDs, parent ancestry, both baby sexes, paused growth and parental rest");
            return true;
        }finally{level.setChunkForced(CHUNK.x,CHUNK.z,false);}
    }
    static void restart(ServerLevel level)throws IOException{
        state=new Properties();
        try(var in=Files.newInputStream(RECORD)){state.load(in);}
        long elapsed=Math.max(0,level.getGameTime()-Long.parseLong(state.getProperty("clock")));
        inspect(level,elapsed+2,"dedicated restart");
        System.out.println("OWL GUARDIAN FAMILY DISK RESTART PASS real second server JVM, same two parents and two youthful children, exact parent UUIDs and cooldowns; son growth="
                +member(level,"son").familyGrowthTicks()+" daughter growth="+member(level,"daughter").familyGrowthTicks());
    }
}
