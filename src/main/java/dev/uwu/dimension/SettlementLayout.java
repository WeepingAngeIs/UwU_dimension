package dev.uwu.dimension;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.regex.Pattern;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.level.LevelEvent;

/** A world keeps its settlement lattice across restarts. Pre-lattice saves retain
 * their historical district assignments in the village lots already on disk. */
public final class SettlementLayout extends SavedData {
    private static final Factory<SettlementLayout> FACTORY=new Factory<>(SettlementLayout::new,SettlementLayout::read,null);
    private static final Map<ServerLevel,SettlementLayout> ACTIVE=java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Pattern REGION=Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private final Map<Long,BitSet> previous=new HashMap<>();
    private final Map<Long,BitSet> beforeTreehouses=new HashMap<>();
    private boolean treehouseSnapshot;
    private final Map<Long,Integer> legacySites=new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<Long> oldCells=new HashSet<>();
    private int distance;

    static void load(LevelEvent.Load event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!ReefLife.isSettlementRealm(level))return;
        var data=level.getDataStorage().computeIfAbsent(FACTORY,"uwudimension_native_settlements");
        var root=level.getServer().getWorldPath(LevelResource.ROOT);
        var dim=level.dimension().location();
        var regions=root.resolve("dimensions").resolve(dim.getNamespace()).resolve(dim.getPath()).resolve("region");
        if(data.distance==0){
            data.distance=AcquisitionPolicy.SETTLEMENT_DISTANCE.get();
            indexPrevious(regions,data.previous);
            data.setDirty();
        }
        if(!data.treehouseSnapshot){
            indexPrevious(regions,data.beforeTreehouses);
            data.treehouseSnapshot=true;
            data.setDirty();
        }
        data.previous.forEach((key,bits)->{
            var region=new ChunkPos(key);
            for(int i=bits.nextSetBit(0);i>=0;i=bits.nextSetBit(i+1)){
                int x=(region.x*32+(i&31))*16, z=(region.z*32+(i>>5))*16;
                data.oldCells.add(ChunkPos.asLong(Math.floorDiv(x+GroveSites.SPACING/2,GroveSites.SPACING),
                        Math.floorDiv(z+GroveSites.SPACING/2,GroveSites.SPACING)));
            }
        });
        ACTIVE.put(level,data);
    }

    private static void indexPrevious(Path region,Map<Long,BitSet> snapshot) {
        if(!Files.isDirectory(region))return;
        // Read only the location headers (4 KiB per old region) exactly once. No saved
        // chunk or entity is loaded, rewritten, or marked dirty by this migration.
        try(var files=Files.newDirectoryStream(region,"r.*.*.mca")){
            for(var path:files){
                var name=REGION.matcher(path.getFileName().toString());
                if(!name.matches())continue;
                try(var channel=FileChannel.open(path)) {
                    var bytes=ByteBuffer.allocate(4096);
                    while(bytes.hasRemaining() && channel.read(bytes)>0){}
                    bytes.flip();
                    var bits=new BitSet(1024);
                    for(int i=0;bytes.remaining()>=4;i++) if(bytes.getInt()!=0)bits.set(i);
                    if(!bits.isEmpty())snapshot.put(ChunkPos.asLong(Integer.parseInt(name.group(1)),Integer.parseInt(name.group(2))),bits);
                }
            }
        }catch(IOException error){throw new IllegalStateException("Could not index saved Sakura village lots in "+region,error);}
    }
    private boolean oldChunk(int x,int z){
        var bits=previous.get(ChunkPos.asLong(x>>5,z>>5));
        return bits!=null && bits.get((x&31)+32*(z&31));
    }
    static boolean untouchedForTreehouse(ServerLevel level,int x1,int z1,int x2,int z2) {
        var snapshot=active(level).beforeTreehouses;
        for(int x=x1>>4;x<=(x2>>4);x++)for(int z=z1>>4;z<=(z2>>4);z++){
            var bits=snapshot.get(ChunkPos.asLong(x>>5,z>>5));
            if(bits!=null && bits.get((x&31)+32*(z&31)))return false;
        }
        return true;
    }
    private int legacyMask(ServerLevel level,GroveSites.Site site) {
        if(!oldCells.contains(ChunkPos.asLong(site.gx(),site.gz())))return 0;
        return legacySites.computeIfAbsent(ChunkPos.asLong(site.gx(),site.gz()), key->{
            int mask=0;
            for(boolean dusk:new boolean[]{false,true}){
                boolean eligible=site.equals(GroveSites.first(level)) ||
                        (HeartwaterFeature.Valley.mix(site.x()+(dusk?727:953),site.z()+331,level.getSeed())&7)<(dusk?3:4);
                if(!eligible)continue;
                var plan=BlossomVillage.survey(level,site,dusk);
                if(plan.isEmpty())continue;
                var v=plan.get();
                boolean occupied=oldChunk(v.plaza().getX()>>4,v.plaza().getZ()>>4);
                for(var house:v.houses()){
                    var a=site.pos(house.x()-house.width()-2,0,house.z()-house.depth()-2);
                    var b=site.pos(house.x()+house.width()+2,0,house.z()+house.depth()+2);
                    for(int x=Math.min(a.getX(),b.getX())>>4;x<=(Math.max(a.getX(),b.getX())>>4);x++)
                        for(int z=Math.min(a.getZ(),b.getZ())>>4;z<=(Math.max(a.getZ(),b.getZ())>>4);z++)
                            occupied|=oldChunk(x,z);
                }
                if(occupied)mask|=dusk?2:1;
            }
            return mask;
        });
    }
    /** Reserving 300 blocks on either side covers the site's +/-144-block
     * jitter, the village's 114-block center offset and the largest roof/veranda.
     * Each candidate has a single faction, including the first Petal district. */
    static boolean admits(ServerLevel level,GroveSites.Site site,boolean dusk) {
        var data=active(level);
        var origin=GroveSites.first(level);
        int old=data.legacyMask(level,site);
        if(old!=0)return (old&(dusk?2:1))!=0;
        if(!lattice(origin,site.gx(),site.gz(),dusk,data.distance))return false;
        if(site.equals(origin))return true;
        // Older settlements are immovable. Reserve the surrounding cells when a
        // legacy district sits off the new lattice, even if its neighbor is still unexplored.
        int radius=stride(data.distance)-1;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(dx==0&&dz==0)continue;
            int gx=site.gx()+dx,gz=site.gz()+dz;
            if(!data.oldCells.contains(ChunkPos.asLong(gx,gz)))continue;
            var neighbor=GroveSites.site(level,gx,gz);
            if(neighbor.usable() && data.legacyMask(level,neighbor)!=0)return false;
        }
        return true;
    }
    static boolean mightHost(ServerLevel level,int gx,int gz,boolean dusk) {
        var data=active(level);
        return data.oldCells.contains(ChunkPos.asLong(gx,gz)) || lattice(GroveSites.first(level),gx,gz,dusk,data.distance);
    }
    private static boolean lattice(GroveSites.Site origin,int gx,int gz,boolean dusk,int distance){
        int stride=stride(distance), x=gx-origin.gx(), z=gz-origin.gz();
        if(Math.floorMod(x,stride)!=0 || Math.floorMod(z,stride)!=0)return false;
        return ((((x/stride)+(z/stride))&1)!=0)==dusk;
    }
    static int stride(int distance) {return Math.ceilDiv(distance+600,GroveSites.SPACING);}
    static boolean historical(ServerLevel level,GroveSites.Site site){return active(level).legacyMask(level,site)!=0;}
    public static int minimum(ServerLevel level){return active(level).distance;}
    private static SettlementLayout active(ServerLevel level) {
        var layout=ACTIVE.get(level);
        if(layout==null)throw new IllegalStateException("Settlement layout requested before level load in "+level.dimension());
        return layout;
    }
    private static SettlementLayout read(CompoundTag tag,HolderLookup.Provider registries){
        var data=new SettlementLayout();
        data.distance=tag.getInt("Spacing");
        var index=tag.getCompound("Existing");
        for(String key:index.getAllKeys())data.previous.put(Long.parseLong(key),BitSet.valueOf(index.getLongArray(key)));
        data.treehouseSnapshot=tag.getBoolean("TreehouseSnapshot");
        var trees=tag.getCompound("BeforeTreehouses");
        for(String key:trees.getAllKeys())data.beforeTreehouses.put(Long.parseLong(key),BitSet.valueOf(trees.getLongArray(key)));
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        tag.putInt("Spacing",distance);
        var index=new CompoundTag();
        previous.forEach((key,bits)->index.putLongArray(Long.toString(key),bits.toLongArray()));
        tag.put("Existing",index);
        tag.putBoolean("TreehouseSnapshot",treehouseSnapshot);
        var trees=new CompoundTag();
        beforeTreehouses.forEach((key,bits)->trees.putLongArray(Long.toString(key),bits.toLongArray()));
        tag.put("BeforeTreehouses",trees);
        return tag;
    }
}
