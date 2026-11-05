package dev.uwu.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.AABB;

/** Hosted-server traversal proof using actual block shapes and standing survival player dimensions. */
public final class RouteProof {
    private final ServerLevel level;
    private final int cx,cz,turns;
    private double lx,lz,feet;
    private int samples;
    private RouteProof(ServerLevel level,int cx,int cz,int turns){this.level=level;this.cx=cx;this.cz=cz;this.turns=turns;}
    private static int mix(int x,int z,long seed){
        long h=seed^(x*0x632BE59BD9B4E019L)^(z*0x9E3779B97F4A7C15L);
        h=(h^(h>>>27))*0x94D049BB133111EBL;return (int)(h^(h>>>31));
    }
    public static void run(ServerLevel level){
        List<int[]> cells=new ArrayList<>();
        for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)cells.add(new int[]{x*384,z*384});
        cells.sort(Comparator.comparingInt(c->Math.abs(c[0])+Math.abs(c[1])));
        boolean[] done=new boolean[4];
        for(var c:cells){
            int style=mix(c[0],c[1],level.getSeed());
            boolean spawn=c[0]==0&&c[1]==0;
            int rot=spawn?0:style&3;
            if(done[rot]||(!spawn&&(style&12)!=0))continue;
            new RouteProof(level,c[0],c[1],rot).verify();done[rot]=true;
            if(done[0]&&done[1]&&done[2]&&done[3])return;
        }
        throw new AssertionError("No shrine cell for every rotation");
    }
    private double worldX(double x,double z){return cx+.5+switch(turns){case 1->-z;case 2->-x;case 3->z;default->x;};}
    private double worldZ(double x,double z){return cz+.5+switch(turns){case 1->x;case 2->-z;case 3->-x;default->z;};}
    private BlockPos pos(int x,int y,int z){return BlockPos.containing(worldX(x,z),y,worldZ(x,z));}
    private void begin(double x,double y,double z){lx=x;lz=z;feet=y;check(x,z);}
    private void walk(int x,int z){
        double startX=lx,startZ=lz;
        int n=(int)Math.ceil(Math.hypot(x-lx,z-lz)*4);
        for(int i=1;i<=n;i++)check(startX+(x-startX)*i/n,startZ+(z-startZ)*i/n);
        lx=x;lz=z;
    }
    private void check(double x,double z){
        double wx=worldX(x,z),wz=worldZ(x,z);
        // Support under either foot can be at most one normal step higher or lower.
        AABB foot=new AABB(wx-.299,feet-.605,wz-.299,wx+.299,feet+.605,wz+.299);
        double support=Double.NEGATIVE_INFINITY;
        for(int px=(int)Math.floor(foot.minX);px<=Math.floor(foot.maxX);px++)
            for(int pz=(int)Math.floor(foot.minZ);pz<=Math.floor(foot.maxZ);pz++)
                for(int y=(int)Math.floor(foot.minY)-1;y<=Math.floor(foot.maxY);y++){
                    BlockPos p=new BlockPos(px,y,pz);
                    for(AABB local:level.getBlockState(p).getCollisionShape(level,p).toAabbs()){
                        AABB s=local.move(px,y,pz);
                        if(s.maxX>foot.minX&&s.minX<foot.maxX&&s.maxZ>foot.minZ&&s.minZ<foot.maxZ
                                &&s.maxY>=foot.minY&&s.maxY<=foot.maxY)support=Math.max(support,s.maxY);
                    }
                }
        if(!Double.isFinite(support))throw problem("missing reachable support",x,feet,z);
        AABB body=new AABB(wx-.299,support+.001,wz-.299,wx+.299,support+1.799,wz+.299);
        if(level.getBlockCollisions(null,body).iterator().hasNext())throw problem("blocked standing body",x,support,z);
        feet=support;samples++;
    }
    private AssertionError problem(String why,double x,double y,double z){
        return new AssertionError(why+" at local "+x+","+y+","+z+" rotation "+turns+" origin "+cx+","+cz);
    }
    private void stairs(int x,int y,int z,Direction facing){
        var b=level.getBlockState(pos(x,y,z));for(int i=0;i<turns;i++)facing=facing.getClockWise();
        if(!(b.getBlock() instanceof StairBlock)||b.getValue(StairBlock.FACING)!=facing)throw problem("stair orientation "+b,x,y,z);
    }
    private void shell(int ax,int ay,int az,int bx,int by,int bz,int doorX,int doorY){
        for(int x=ax;x<=bx;x++)for(int y=ay;y<=by;y++)for(int z=az;z<=bz;z++){
            if(x!=ax&&x!=bx&&y!=ay&&y!=by&&z!=az&&z!=bz)continue;
            boolean doorway=z==az&&Math.abs(x-doorX)<=1&&y>doorY&&y<=doorY+3;
            var b=level.getBlockState(pos(x,y,z));
            if(doorway?!b.isAir():!b.is(Blocks.OBSIDIAN))throw problem("shell/aperture mismatch "+b,x,y,z);
        }
    }
    private void chest(int x,int y,int z){
        var id=BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos(x,y,z)).getBlock()).toString();
        if(!id.equals("lootr:lootr_chest"))throw problem("missing Lootr reward",x,y,z);
    }
    private void verify(){
        begin(0,81,0);walk(0,85);walk(-7,85);walk(-7,103);walk(-1,103);chest(0,53,103);
        walk(-7,103);walk(-7,85);walk(0,85);walk(0,66);walk(40,66);walk(40,109);
        walk(41,109);walk(41,126);walk(48,126);walk(48,112);walk(53,112);walk(53,120);walk(54,120);chest(55,9,120);
        walk(53,120);walk(53,112);walk(48,112);walk(48,126);walk(41,126);walk(41,110);walk(16,110);
        walk(16,148);walk(23,148);chest(23,-18,147);
        walk(16,148);walk(16,127);walk(-23,127);walk(16,127);walk(16,110);walk(40,110);walk(40,66);walk(0,66);walk(0,0);
        stairs(0,76,29,Direction.NORTH);stairs(21,48,66,Direction.WEST);stairs(40,24,85,Direction.NORTH);
        stairs(10,-23,127,Direction.EAST);stairs(35,-4,110,Direction.EAST);stairs(41,4,121,Direction.SOUTH);
        shell(-14,51,82,14,62,106,0,52);shell(5,-20,139,27,-10,150,16,-19);shell(50,7,115,57,15,123,53,8);
        System.out.println("ROUTE PROOF rotation="+turns+" origin="+cx+","+cz+" player-body samples="+samples+" three single-opening obsidian shells and three reward sites passed");
    }
}

