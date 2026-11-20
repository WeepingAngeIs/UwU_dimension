package dev.uwu.dimension;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Read-only, seed-plan reservations, evaluated before a new structure start is committed. */
public final class AuthoredFootprints {
    private AuthoredFootprints(){}
    public static final int LOCAL_RADIUS=256, MAX_CELLS=256;
    private static final int PAD=144+LOCAL_RADIUS;
    private static final Map<ServerLevel,Map<Long,List<Envelope>>> CACHE=new WeakHashMap<>();
    public record Envelope(String part,BoundingBox box){}
    public enum Decision { CLEAR, OVERLAP, BUDGET }
    private static synchronized Map<Long,List<Envelope>> cache(ServerLevel level){
        return CACHE.computeIfAbsent(level,ignored->new ConcurrentHashMap<>());
    }
    public static List<Envelope> cell(ServerLevel level,int gx,int gz){
        if(!ReefLife.isSettlementRealm(level))return List.of();
        var cache=cache(level);if(cache.size()>512)cache.clear();
        return cache.computeIfAbsent(ChunkPos.asLong(gx,gz),ignored->{
            var site=GroveSites.site(level,gx,gz);if(!site.usable())return List.of();
            var sink=new Survey(level,site);
            var valley=new HeartwaterFeature.Valley(level,site,sink);
            valley.surfaceLandmarks();valley.draw();
            return List.copyOf(sink.result);
        });
    }
    public static Decision check(ServerLevel level,BoundingBox foreign){
        if(!ReefLife.isSettlementRealm(level)||foreign.maxY()<level.getMinBuildHeight()
                ||foreign.minY()>=level.getMaxBuildHeight())return Decision.CLEAR;
        long x0=Math.floorDiv((long)foreign.minX()-PAD+GroveSites.SPACING/2,GroveSites.SPACING);
        long x1=Math.floorDiv((long)foreign.maxX()+PAD+GroveSites.SPACING/2,GroveSites.SPACING);
        long z0=Math.floorDiv((long)foreign.minZ()-PAD+GroveSites.SPACING/2,GroveSites.SPACING);
        long z1=Math.floorDiv((long)foreign.maxZ()+PAD+GroveSites.SPACING/2,GroveSites.SPACING);
        // Never turn an enormous or malformed mod bounding box into an unbounded terrain survey.
        // Such starts are conservatively declined, not partially clipped or silently accepted.
        if((x1-x0+1)*(z1-z0+1)>MAX_CELLS)return Decision.BUDGET;
        for(long x=x0;x<=x1;x++)for(long z=z0;z<=z1;z++)
            for(var part:cell(level,(int)x,(int)z))if(part.box().intersects(foreign))return Decision.OVERLAP;
        return Decision.CLEAR;
    }
    public static Decision check(ServerLevel level,StructureStart start){
        return start.isValid()?check(level,start.getBoundingBox()):Decision.CLEAR;
    }
    /** Drops only derived memory; persistent settlement snapshots remain the source of truth. */
    public static void clear(ServerLevel level){synchronized(AuthoredFootprints.class){CACHE.remove(level);}}
    static final class Survey {
        final ServerLevel level;final GroveSites.Site site;final List<Envelope> result=new ArrayList<>();
        private String name="unclassified";private BoundingBox bounds;
        Survey(ServerLevel level,GroveSites.Site site){this.level=level;this.site=site;}
        void part(String next,Runnable action){
            var previous=name;var prior=bounds;name=next;bounds=null;
            try{action.run();if(bounds!=null)result.add(new Envelope(next,bounds));}
            finally{name=previous;bounds=prior;}
        }
        void box(int x,int y,int z,int xx,int yy,int zz){
            if(Math.max(Math.abs(x),Math.abs(xx))>LOCAL_RADIUS||Math.max(Math.abs(z),Math.abs(zz))>LOCAL_RADIUS)
                throw new IllegalStateException("Authored plan exceeds survey bound: "+name);
            int low=Math.max(level.getMinBuildHeight(),Math.min(y,yy)),high=Math.min(level.getMaxBuildHeight()-1,Math.max(y,yy));
            if(low>high)return;
            int ax=site.wx(x,z),az=site.wz(x,z),bx=site.wx(xx,zz),bz=site.wz(xx,zz);
            var b=new BoundingBox(Math.min(ax,bx),low,Math.min(az,bz),Math.max(ax,bx),high,Math.max(az,bz));
            if(bounds==null)bounds=b;else bounds.encapsulate(b);
        }
    }
}
