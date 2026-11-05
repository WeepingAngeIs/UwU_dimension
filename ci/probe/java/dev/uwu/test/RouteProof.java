package dev.uwu.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.BlossomVillage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
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
    private final GroveSites.Site site;
    private RouteProof(ServerLevel level,GroveSites.Site site){
        this.level=level;this.site=site;this.cx=site.x();this.cz=site.z();this.turns=site.turns();
    }
    public static void run(ServerLevel level){
        GroveSites.Site first=GroveSites.first(level);
        boolean[] done=new boolean[4];new RouteProof(level,first).verify();done[first.turns()]=true;
        for(int ring=1;ring<17;ring++){
            for(int gx=-ring;gx<=ring;gx++)for(int gz=-ring;gz<=ring;gz++){
                if(Math.max(Math.abs(gx),Math.abs(gz))!=ring)continue;
                var s=GroveSites.site(level,first.gx()+gx,first.gz()+gz);
                if(!s.usable()||done[s.turns()]||!GroveSites.shrine(level,s))continue;
                new RouteProof(level,s).verify();done[s.turns()]=true;
            }
            if(done[0]&&done[1]&&done[2]&&done[3])break;
        }
        if(!(done[0]&&done[1]&&done[2]&&done[3]))throw new AssertionError("Missing rotated shrine site");
        for(boolean dusk:new boolean[]{false,true})village(level,BlossomVillage.first(level,dusk));
        terrain(level,2787,-1117);
        var legacy=level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("uwudimension","heartwater")));
        if(legacy!=null)terrain(legacy,2787,-1117);
        terrain(level,first.x()+198,first.z()+192);
    }
    private static void terrain(ServerLevel level,int x,int z){
        // Away from foundations there must be no projected shelf. Compare actual ground
        // to raw Overworld noise per column, crossing both local chunk boundaries.
        loadColumns(level,x-16,z-16,x+16,z+16);
        int matches=0,examined=0,trees=0;
        for(int a=x-16;a<=x+16;a+=2)for(int b=z-16;b<=z+16;b+=2){
            int baseline=level.getChunkSource().getGenerator().getBaseHeight(a,b,Heightmap.Types.OCEAN_FLOOR_WG,level,level.getChunkSource().randomState())-1;
            var top=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,new BlockPos(a,0,b)).below();
            boolean trunk=false;
            while(top.getY()>level.getMinBuildHeight() && (level.getBlockState(top).is(BlockTags.LOGS)||level.getBlockState(top).is(BlockTags.LEAVES)||!level.getBlockState(top).isSolidRender(level,top))){
                if(level.getBlockState(top).is(BlockTags.LOGS))trunk=true;
                top=top.below();
            }
            if(trunk)trees++;
            if(Math.abs(top.getY()-baseline)<=3)matches++;
            examined++;
            for(int y=Math.max(level.getMinBuildHeight()+1,baseline-3);y<baseline+16;y++){
                BlockPos plant=new BlockPos(a,y,b);
                var state=level.getBlockState(plant);
                if((state.is(Blocks.PINK_PETALS)||state.is(Blocks.SHORT_GRASS)||state.is(Blocks.TALL_GRASS)) && !state.canSurvive(level,plant))
                    throw new AssertionError("Floating plant at "+plant);
            }
        }
        if(matches<examined*.70)throw new AssertionError("Terrain cut or plateau at "+x+","+z+" native="+matches+"/"+examined);
        System.out.println("GROVE TERRAIN native columns="+matches+"/"+examined+" tree columns="+trees+" centered "+x+","+z);
        int height=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        BlockPos center=new BlockPos(x,height,z);
        boolean grove=level.getBiome(center).is(Biomes.CHERRY_GROVE);
        supportedTrees(level,center,32,grove,"TERRAIN");
    }
    private static void loadColumns(ServerLevel level,int minX,int minZ,int maxX,int maxZ){
        for(int cx=minX>>4;cx<=(maxX>>4);cx++)for(int cz=minZ>>4;cz<=(maxZ>>4);cz++)
            level.getChunk(cx,cz);
    }
    private static void supportedTrees(ServerLevel level,BlockPos center,int radius,boolean required,String label){
        int x0=center.getX(),z0=center.getZ();
        loadColumns(level,x0-radius,z0-radius,x0+radius,z0+radius);
        java.util.Set<BlockPos> visited=new java.util.HashSet<>();
        int verified=0;
        for(int x=x0-radius;x<=x0+radius;x++)for(int z=z0-radius;z<=z0+radius;z++){
            int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z);
            for(int y=top-1;y>=Math.max(level.getMinBuildHeight(),top-20);y--){
                BlockPos start=new BlockPos(x,y,z);
                var state=level.getBlockState(start);
                // Only raw trunks of locally generated tree species qualify. All building
                // timbers are stripped. Roofs, walls, and floating beams cannot prove a root.
                if(!(state.is(Blocks.CHERRY_LOG)||state.is(Blocks.BIRCH_LOG)||state.is(Blocks.JUNGLE_LOG)||state.is(Blocks.OAK_LOG))
                        ||!visited.add(start))continue;
                var species=state.getBlock();
                java.util.ArrayDeque<BlockPos> next=new java.util.ArrayDeque<>();
                next.add(start);
                boolean grounded=false;
                int logs=0;
                while(!next.isEmpty()){
                    BlockPos log=next.removeFirst();
                    logs++;
                    if(level.getBlockState(log.below()).is(BlockTags.DIRT))grounded=true;
                    for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=-1;dy<=1;dy++){
                        BlockPos neighbor=log.offset(dx,dy,dz);
                        if(!visited.contains(neighbor)&&level.getBlockState(neighbor).is(species)&&visited.add(neighbor))next.add(neighbor);
                    }
                }
                if(!grounded)throw new AssertionError("Suspended "+species+" at "+start+" logs="+logs);
                verified++;
            }
        }
        if(required&&verified==0)throw new AssertionError("No rooted grove tree within "+radius+" blocks of "+center);
        System.out.println(label+" TREE ROOTS connected="+verified+" radius="+radius+" near "+center+" in "+level.dimension().location());
    }
    private static void village(ServerLevel level,BlossomVillage.Plan v){
        if(!level.getBiome(v.plaza()).is(Biomes.CHERRY_GROVE))throw new AssertionError("Settlement biome at "+v.plaza()+": "+level.getBiome(v.plaza()).unwrapKey());
        if(v.houses().size()<6)throw new AssertionError("Sparse village");
        java.util.Set<Integer> styles=new java.util.HashSet<>();
        supportedTrees(level,v.plaza(),48,true,"SETTLEMENT");
        for(var h:v.houses()){
            styles.add(h.style());
            var bed=v.bedHead(h);var door=v.doorway(h);
            if(!(level.getBlockState(bed).getBlock() instanceof BedBlock) || !(level.getBlockState(door).getBlock() instanceof DoorBlock)
                    || !level.getBlockState(v.workstation(h)).is(h.job()))throw new AssertionError("Incomplete house at "+door);
            for(int a=-h.width();a<=h.width();a++)for(int b=-h.depth();b<=h.depth();b++){
                BlockPos below=v.site().pos(h.x()+a,h.floor()-1,h.z()+b);
                if(!level.getBlockState(below).isSolidRender(level,below))throw new AssertionError("Floating house foundation "+below);
            }
        }
        System.out.println("NATURAL GROVE VILLAGE "+v.plaza()+" houses="+v.houses().size()+" styles="+styles+" faction="+v.dusk()+" real beds/jobs/doors and foundations");
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
        if(!Double.isFinite(support)){
            BlockPos at=BlockPos.containing(wx,feet,wz);
            throw problem("missing reachable support "+at+" states from feet down: "+level.getBlockState(at)+", "
                    +level.getBlockState(at.below())+", "+level.getBlockState(at.below(2)),x,feet,z);
        }
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
    private void tread(int x,int y,int z,Direction direction){
        stairs(x,y,z,direction);
        for(int rise=1;rise<=3;rise++){
            var p=pos(x,y+rise,z);if(!level.getBlockState(p).getCollisionShape(level,p).isEmpty())
                throw problem("obstruction above tread: "+level.getBlockState(p),x,y+rise,z);
        }
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
        begin(0,site.entryY()+1,site.lip()-9);walk(0,85);walk(-7,85);walk(-7,103);walk(-1,103);chest(0,53,103);
        walk(-7,103);walk(-7,85);walk(0,85);walk(0,66);walk(40,66);walk(40,109);
        walk(41,109);walk(41,126);walk(48,126);walk(48,112);walk(53,112);walk(53,120);walk(54,120);chest(55,9,120);
        walk(53,120);walk(53,112);walk(48,112);walk(48,126);walk(41,126);walk(41,110);walk(16,110);
        walk(16,148);walk(23,148);chest(23,-18,147);
        walk(16,148);walk(16,127);walk(-23,127);walk(16,127);walk(16,110);walk(40,110);walk(40,66);walk(0,66);walk(0,site.lip()-9);
        stairs(0,site.entryY()-2,site.lip()+6,Direction.NORTH);stairs(21,48,66,Direction.WEST);stairs(40,24,85,Direction.NORTH);
        stairs(10,-23,127,Direction.EAST);stairs(35,-4,110,Direction.EAST);stairs(41,4,121,Direction.SOUTH);
        // Inspect every tread across its whole width (the first release's centerline
        // probe missed a lamp sitting on one outer tread and an uncut side lane).
        for(int z=site.lip()+4;z<53;z++)for(int x=-2;x<=2;x++)tread(x,105-z,z,Direction.NORTH);
        for(int x=17;x<40;x++)for(int z=65;z<=67;z++)tread(x,69-x,z,Direction.WEST);
        for(int z=80;z<109;z++)for(int x=39;x<=41;x++)tread(x,109-z,z,Direction.NORTH);
        for(int x=21;x<=39;x++)for(int z=109;z<=111;z++)tread(x,x-39,z,Direction.EAST);
        for(int x=-14;x<=14;x++)for(int z=126;z<=128;z++)tread(x,x-33,z,Direction.EAST);
        for(int z=118;z<=125;z++)for(int x=40;x<=42;x++)tread(x,z-117,z,Direction.SOUTH);
        shell(-14,51,82,14,62,106,0,52);shell(5,-20,139,27,-10,150,16,-19);shell(50,7,115,57,15,123,53,8);shell(-17,-20,139,3,-6,163,-7,-19);
        System.out.println("ROUTE PROOF rotation="+turns+" origin="+cx+","+cz+" player-body samples="+samples+" four single-opening obsidian shells and three reward sites passed");
    }
}

