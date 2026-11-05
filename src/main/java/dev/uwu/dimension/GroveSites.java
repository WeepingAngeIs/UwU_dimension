package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

/** Pure land survey. Base-height queries sample the generator, never a neighboring generating chunk. */
public final class GroveSites {
    public static final int SPACING=768;
    private static final class Maps {
        final Map<Long,Site> sites=new ConcurrentHashMap<>();
        final Map<Long,Integer> heights=new ConcurrentHashMap<>();
        volatile Site first;
    }
    private static final Map<ServerLevel,Maps> MAPS=new WeakHashMap<>();
    private static synchronized Maps maps(ServerLevel level){return MAPS.computeIfAbsent(level,k->new Maps());}
    public record Site(int x,int z,int turns,int entryY,int gx,int gz,boolean usable,boolean shrine) {
        public int wx(int a,int b){return x+switch(turns){case 1->-b;case 2->-a;case 3->b;default->a;};}
        public int wz(int a,int b){return z+switch(turns){case 1->a;case 2->-b;case 3->-a;default->b;};}
        public BlockPos pos(int a,int y,int b){return new BlockPos(wx(a,b),y,wz(a,b));}
        public int lip(){return 53-(entryY-52)-4;}
    }
    static int height(ServerLevel level,int x,int z){
        Maps m=maps(level);
        // No unbounded per-column storage during long exploration. Only survey/sampled road points are cached.
        if(m.heights.size()>80000)m.heights.clear();
        return m.heights.computeIfAbsent(ChunkPos.asLong(x,z),k->
            level.getChunkSource().getGenerator().getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,level.getChunkSource().randomState())-1);
    }
    static boolean cherry(ServerLevel level,int x,int y,int z){
        var chunks=level.getChunkSource();
        return chunks.getGenerator().getBiomeSource().getNoiseBiome(
                net.minecraft.core.QuartPos.fromBlock(x),net.minecraft.core.QuartPos.fromBlock(y),
                net.minecraft.core.QuartPos.fromBlock(z),chunks.randomState().sampler())
                .is(net.minecraft.world.level.biome.Biomes.CHERRY_GROVE);
    }
    public static Site site(ServerLevel level,int gx,int gz){
        Maps m=maps(level);
        if(m.sites.size()>4000)m.sites.clear();
        return m.sites.computeIfAbsent(ChunkPos.asLong(gx,gz),k->survey(level,gx,gz));
    }
    static Site forChunk(ServerLevel level,ChunkPos pos) {
        return site(level,Math.floorDiv(pos.getMinBlockX()+SPACING/2,SPACING),Math.floorDiv(pos.getMinBlockZ()+SPACING/2,SPACING));
    }
    private static Site survey(ServerLevel level,int gx,int gz){
        int style=HeartwaterFeature.Valley.mix(gx*SPACING,gz*SPACING,level.getSeed());
        var r=new Random(((long)style<<32)^level.getSeed());
        int turn=gx==0&&gz==0?0:r.nextInt(4);
        for(int i=0;i<42;i++){
            int x=gx*SPACING+r.nextInt(289)-144,z=gz*SPACING+r.nextInt(289)-144;
            int y=height(level,x,z);
            if(y<68||y>130)continue;
            Site s=new Site(x,z,turn,y,gx,gz,true,(style&3)==0);
            int bank=height(level,s.wx(0,s.lip()),s.wz(0,s.lip()));
            int front=height(level,s.wx(0,s.lip()-9),s.wz(0,s.lip()-9));
            if(Math.abs(bank-y)>6||Math.abs(front-y)>5)continue;
            if(!cherry(level,s.wx(0,s.lip()-9),y+1,s.wz(0,s.lip()-9)))continue;
            if(height(level,s.wx(0,65),s.wz(0,65))<69||height(level,s.wx(0,102),s.wz(0,102))<69)continue;
            return s;
        }
        return new Site(gx*SPACING,gz*SPACING,turn,0,gx,gz,false,false);
    }
    public static Site first(ServerLevel level){
        Maps m=maps(level);Site first=m.first;if(first!=null)return first;
        for(int ring=0;ring<=18;ring++)for(int gx=-ring;gx<=ring;gx++)for(int gz=-ring;gz<=ring;gz++){
            if(Math.max(Math.abs(gx),Math.abs(gz))!=ring)continue;
            Site s=site(level,gx,gz);
            if(s.usable()){m.first=s;return s;}
        }
        throw new IllegalStateException("Unable to find inland cherry foothills");
    }
    public static boolean adventure(ServerLevel level,Site s){
        return s.equals(first(level)) || (HeartwaterFeature.Valley.mix(s.x()+1771,s.z()-293,level.getSeed())&7)<3;
    }
    static boolean settlement(ServerLevel level,Site s,boolean dusk){
        return s.equals(first(level)) || (HeartwaterFeature.Valley.mix(s.x()+(dusk?727:953),s.z()+331,level.getSeed())&7)<(dusk?3:4);
    }
    public static boolean shrine(ServerLevel level,Site s){return s.equals(first(level)) || s.shrine()&&adventure(level,s);}
    public static BlockPos arrival(ServerLevel level){Site s=first(level);return s.pos(0,s.entryY()+1,s.lip()-9);}
    public static BlockPos village(ServerLevel level,boolean dusk){return BlossomVillage.first(level,dusk).plaza();}
}
