package dev.uwu.dimension;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** A native two-keeper house grown into a buttressed tree at the edge of a settlement. */
public final class MoonveilTreehouse {
    public static final int FOOTPRINT=23;
    private static final String PENDING="MoonveilPendingHousehold";
    private static final Map<ServerLevel,Map<Key,Optional<Plot>>> PLANS=new WeakHashMap<>();
    private record Key(int gx,int gz,boolean dusk){}
    // gnarled branch ends, including the leader. The staggered leaf clusters form one canopy.
    private static final int[][] BOUGHS={{-13,24,-4},{9,27,-12},{14,23,4},{-8,28,12},
            {-4,32,-9},{7,31,8},{-12,29,3},{1,34,1}};
    private static synchronized Map<Key,Optional<Plot>> cache(ServerLevel level){
        return PLANS.computeIfAbsent(level,ignored->new ConcurrentHashMap<>());
    }
    public record Plot(BlossomVillage.Plan village,int x,int y,int z,long seed){
        public boolean dusk(){return village.dusk();}
        public BlockPos at(int dx,int dy,int dz){return village.site().pos(x+dx,y+dy,z+dz);}
        public BlockPos door(){return at(0,1,-7);}
        public BlockPos bedHead(int slot){return at(3,8,slot==0?-3:3);}
        public BlockPos job(int slot){return slot==0?at(4,1,-2):at(-1,8,3);}
        public BlockPos resident(int slot){return slot==0?at(2,1,-2):at(0,8,2);}
        public UUID residentId(int slot){
            if(slot<0||slot>1)throw new IllegalArgumentException("A treehouse has two original residents");
            return UUID.nameUUIDFromBytes(("uwudimension:sakura_reach/moonveil/"+seed+"/"+at(0,0,0).asLong()+"/"+slot)
                    .getBytes(StandardCharsets.UTF_8));
        }
    }
    public static Optional<Plot> plan(ServerLevel level,BlossomVillage.Plan village){
        if(!ReefLife.isSettlementRealm(level))return Optional.empty();
        var cache=cache(level);
        if(cache.size()>4096)cache.clear();
        return cache.computeIfAbsent(new Key(village.site().gx(),village.site().gz(),village.dusk()),unused->survey(level,village));
    }
    public static Plot first(ServerLevel level,boolean dusk){
        if(!ReefLife.isSettlementRealm(level))throw new IllegalArgumentException("Moonveil houses grow in Sakura Reach");
        var origin=GroveSites.first(level);
        int stride=SettlementLayout.stride(SettlementLayout.minimum(level));
        for(int ring=0;ring<=15;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
            int gx=origin.gx()+dx*stride,gz=origin.gz()+dz*stride;
            if(!SettlementLayout.mightHost(level,gx,gz,dusk))continue;
            var site=GroveSites.site(level,gx,gz);
            if(!site.usable())continue;
            var tree=BlossomVillage.plan(level,site,dusk).flatMap(v->plan(level,v));
            if(tree.isPresent())return tree.get();
        }
        throw new IllegalStateException("No untouched Moonveil treehouse land in the surveyed settlement rings");
    }
    private static Optional<Plot> survey(ServerLevel level,BlossomVillage.Plan v){
        var plaza=v.plaza();
        if(!SettlementLayout.untouchedForTreehouse(level,plaza.getX()-38,plaza.getZ()-38,plaza.getX()+38,plaza.getZ()+38))
            return Optional.empty();
        int sign=(HeartwaterFeature.Valley.mix(v.x(),v.z(),level.getSeed())&1)==0?1:-1;
        for(int distance:new int[]{49,58,67})for(int lane:new int[]{0,17,-17,31,-31}){
            for(int direction=0;direction<4;direction++){
                int x=v.x()+(direction<2?sign*lane:direction==2?distance:-distance);
                int z=v.z()+(direction<2?(direction==0?-distance:distance):sign*lane);
                var site=v.site();
                if(Math.abs(x)>185 || z< -180 || z>10)continue;
                int y=GroveSites.height(level,site.wx(x,z),site.wz(x,z));
                var plot=new Plot(v,x,y,z,level.getSeed());
                if(suitable(level,v,plot.at(0,0,0)))return Optional.of(plot);
            }
        }
        return Optional.empty();
    }
    /** Seed survey only. No candidate causes a live chunk, POI or ticket to be loaded. */
    public static boolean suitable(ServerLevel level,BlossomVillage.Plan village,BlockPos base){
        if(!ReefLife.isSettlementRealm(level))return false;
        int x=base.getX(),y=base.getY(),z=base.getZ();
        // Preserve reconstruction of an existing household; restrict only new untouched plots.
        if(SettlementLayout.untouchedForForest(level,x-FOOTPRINT,z-FOOTPRINT,x+FOOTPRINT,z+FOOTPRINT))
            for(int dx=-FOOTPRINT;dx<=FOOTPRINT;dx++)for(int dz=-FOOTPRINT;dz<=FOOTPRINT;dz++)
                if(SakuraForest.target(level,x+dx,z+dz))return false;
        if(y<=64 || y+42>=level.getMaxBuildHeight() || y!=GroveSites.height(level,x,z)
                || !level.getWorldBorder().isWithinBounds(base.offset(-FOOTPRINT,0,-FOOTPRINT))
                || !level.getWorldBorder().isWithinBounds(base.offset(FOOTPRINT,0,FOOTPRINT))
                || !SettlementLayout.untouchedForTreehouse(level,x-FOOTPRINT,z-FOOTPRINT,x+FOOTPRINT,z+FOOTPRINT))return false;
        var site=village.site();
        for(var h:village.houses()){
            var center=site.pos(h.x(),0,h.z());
            if(Math.abs(x-center.getX())<=FOOTPRINT+Math.max(h.width(),h.depth())+2
                    && Math.abs(z-center.getZ())<=FOOTPRINT+Math.max(h.width(),h.depth())+2)return false;
        }
        var plaza=village.plaza();
        if(base.distToCenterSqr(plaza.getX()+.5,y+.5,plaza.getZ()+.5)<42*42)return false;
        var spring=site.pos(0,y,site.lip());
        if(Math.abs(x-spring.getX())<=FOOTPRINT+16 && Math.abs(z-spring.getZ())<=FOOTPRINT+16)return false;
        for(int dx=-12;dx<=12;dx+=6)for(int dz=-12;dz<=12;dz+=6){
            int h=GroveSites.height(level,x+dx,z+dz);
            if(h<=63 || Math.abs(h-y)>5 || !GroveSites.cherry(level,x+dx,h+1,z+dz))return false;
        }
        return true;
    }

    static void place(HeartwaterFeature.Valley v,Plot p){
        if(!v.overlaps(p.x-FOOTPRINT,p.z-FOOTPRINT,p.x+FOOTPRINT,p.z+FOOTPRINT))return;
        new Builder(v,p).build();
    }

    private static final class Builder {
        final HeartwaterFeature.Valley v;
        final Plot p;
        final Block bark=MoonveilWood.WOOD.get(),boards=MoonveilWood.PLANKS.get();
        final BlockState leaves=MoonveilWood.LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);
        Builder(HeartwaterFeature.Valley v,Plot p){this.v=v;this.p=p;}
        void put(int x,int y,int z,BlockState s){v.put(p.x+x,p.y+y,p.z+z,s);}
        void block(int x,int y,int z,Block b){v.block(p.x+x,p.y+y,p.z+z,b);}
        void box(int x,int y,int z,int xx,int yy,int zz,Block b){v.box(p.x+x,p.y+y,p.z+z,p.x+xx,p.y+yy,p.z+zz,b);}
        void wood(int x,int y,int z,Direction.Axis axis){
            var facing=axis==Direction.Axis.Y?axis:v.face(axis==Direction.Axis.X?Direction.EAST:Direction.SOUTH).getAxis();
            put(x,y,z,MoonveilWood.LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS,facing));
        }
        void build(){
            base();branches();balcony();interior();
            for(int slot=0;slot<2;slot++)resident(slot);
        }
        void base(){
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
                if(!v.inside(p.x+x,p.z+z))continue;
                int a=Math.abs(x),b=Math.abs(z),max=Math.max(a,b),sum=a+b;
                boolean shell=max<=7 && sum<=11;
                if(shell){
                    int h=GroveSites.height(v.level.getLevel(),v.wx(p.x+x,p.z+z),v.wz(p.x+x,p.z+z));
                    box(x,Math.min(h-p.y-1,-1),z,x,0,z,bark);
                    boolean wall=max==7 || sum==11;
                    if(wall){
                        box(x,1,z,x,16,z,bark);
                        if((a+b)%3==1)for(int y=1;y<=15;y++)wood(x,y,z,Direction.Axis.Y);
                    }else{
                        box(x,1,z,x,15,z,Blocks.AIR);
                        block(x,0,z,boards);block(x,7,z,boards);block(x,14,z,boards);
                    }
                    continue;
                }
                // Eight spreading buttress roots narrow into the native soil.
                int axial=Math.min(a,b),diagonal=Math.abs(a-b);
                if((axial<=1 && max<=12 || diagonal<=1 && max<=10) && !(a<=2 && b>6)){
                    int soil=GroveSites.height(v.level.getLevel(),v.wx(p.x+x,p.z+z),v.wz(p.x+x,p.z+z))-p.y;
                    int top=(axial<=1?13-max:11-max)+(axial==0||diagonal==0?1:0);
                    box(x,Math.min(soil,top),z,x,Math.max(soil,top),z,bark);
                }
            }
            for(int side:new int[]{-1,1}){
                var direction=side<0?Direction.SOUTH:Direction.NORTH;
                var door=MoonveilWood.DOOR.get().defaultBlockState().setValue(DoorBlock.FACING,v.face(direction));
                put(0,1,side*7,door);put(0,2,side*7,door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
                block(0,3,side*7,p.dusk()?ReachArchitecture.TRACERY.get():HeartwaterFeature.ROSETTE.get());
                for(int d=8;d<=12;d++)for(int xx=-1;xx<=1;xx++){
                    int earth=GroveSites.height(v.level.getLevel(),v.wx(p.x+xx,p.z+side*d),v.wz(p.x+xx,p.z+side*d))-p.y;
                    int path=(int)Math.round(earth*(d-8)/4.0);
                    box(xx,Math.min(path-1,earth),side*d,xx,path,side*d,bark);
                    block(xx,path,side*d,boards);
                    box(xx,path+1,side*d,xx,Math.max(earth+2,path+3),side*d,Blocks.AIR);
                }
                for(int y:new int[]{3,10})for(int i=-1;i<=1;i++){
                    block(side*7,y,i,ReachArchitecture.window(p.dusk(),i));
                    block(side*7,y+1,i,ReachArchitecture.window(p.dusk(),i));
                    if(i==0)block(side*7,y+2,i,ReachArchitecture.window(p.dusk(),0));
                    block(3+i,y,side*7,ReachArchitecture.window(p.dusk(),i));
                }
            }
        }
        void branches(){
            // Above the bedrooms the trunk forks. Each bough tapers, bends upward, and
            // carries its own irregular crown; the lower gallery remains visible below it.
            for(int[] tip:BOUGHS){
                int tx=tip[0],ty=tip[1],tz=tip[2],steps=Math.max(ty-14,Math.max(Math.abs(tx),Math.abs(tz)));
                int prevX=0,prevZ=0,prevY=14;
                for(int i=0;i<=steps;i++){
                    double t=i/(double)steps;
                    int x=(int)Math.round(tx*t),z=(int)Math.round(tz*t);
                    int y=14+(int)Math.round((ty-14)*(.60*t+.40*t*t));
                    // Join diagonals face-to-face, even at the narrow tips of a high bough.
                    if(x!=prevX)wood(x,prevY,prevZ,Direction.Axis.X);
                    if(z!=prevZ)wood(x,prevY,z,Direction.Axis.Z);
                    for(int rise=prevY;rise<=y;rise++)wood(x,rise,z,Direction.Axis.Y);
                    prevX=x;prevY=y;prevZ=z;
                    int radius=t<.24?3:t<.58?2:t<.92?1:0;
                    for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                        if(dx*dx+dz*dz>radius*radius+1)continue;
                        wood(x+dx,y,z+dz,t<.40?Direction.Axis.Y:Math.abs(tx)>Math.abs(tz)?Direction.Axis.X:Direction.Axis.Z);
                    }
                }
            }
            for(int[] tip:BOUGHS){
                int tx=tip[0],ty=tip[1]+2,tz=tip[2];
                for(int dx=-8;dx<=8;dx++)for(int dz=-8;dz<=8;dz++){
                    int x=tx+dx,z=tz+dz;
                    if(!v.inside(p.x+x,p.z+z))continue;
                    int j=HeartwaterFeature.Valley.mix(p.x+x,p.z+z,v.seed);
                    for(int dy=-3;dy<=4;dy++){
                        double shape=dx*dx/62.0+dz*dz/56.0+dy*dy/(dy<0?9.0:18.0);
                        if(shape>1.02+(j&3)*.035)continue;
                        var at=p.at(x,ty+dy,z);
                        var old=v.chunk.getBlockState(at);
                        if(old.is(bark)||old.is(MoonveilWood.LOG.get()))continue;
                        put(x,ty+dy,z,leaves);
                    }
                    if((j&31)==0 && dx*dx+dz*dz<36){
                        put(x,ty-3,z,leaves);put(x,ty-4,z,leaves);
                    }
                }
            }
        }
        void balcony(){
            for(int x=-4;x<=4;x++)for(int z=-10;z<=-7;z++){
                if(Math.abs(x)==4 && z==-10)continue;
                block(x,7,z,boards);
                if(z==-10 || Math.abs(x)==4)block(x,8,z,MoonveilWood.FENCE.get());
            }
            var facing=v.face(Direction.NORTH);
            var door=MoonveilWood.DOOR.get().defaultBlockState().setValue(DoorBlock.FACING,facing);
            put(0,8,-7,door);put(0,9,-7,door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
            for(int x:new int[]{-3,3}){
                wood(x,6,-8,Direction.Axis.Z);wood(x,5,-7,Direction.Axis.Y);
                box(x,9,-9,x,11,-9,MoonveilWood.FENCE.get());
                box(x,12,-9,x,12,-7,boards);
                block(x,11,-8,SakuraMaterials.CHAIN.get());
                put(x,10,-8,(p.dusk()?HeartLanterns.DUSK.get():HeartLanterns.HEART.get()).defaultBlockState()
                        .setValue(LanternBlock.HANGING,true));
            }
        }
        void interior(){
            // Enter directly onto the seven-step flight. A detour around the rear wall
            // would exceed a keeper's native 24-block navigation budget going to the upper beds.
            for(int i=0;i<7;i++)for(int x=-2;x<=-1;x++){
                int z=-5+i;
                if(i>=2)block(x,7,z,Blocks.AIR);
                if(i>0)box(x,1,z,x,i,z,boards);
                put(x,i+1,z,v.stair(MoonveilWood.STAIRS.get(),Direction.SOUTH));
            }
            for(int z=-3;z<=1;z++)block(-3,8,z,MoonveilWood.FENCE.get());
            for(int slot=0;slot<2;slot++){
                int z=slot==0?-3:3;
                var bed=(p.dusk()?Blocks.PURPLE_BED:Blocks.PINK_BED).defaultBlockState().setValue(BedBlock.FACING,v.face(Direction.EAST));
                put(2,8,z,bed.setValue(BedBlock.PART,BedPart.FOOT));put(3,8,z,bed.setValue(BedBlock.PART,BedPart.HEAD));
            }
            block(4,1,-2,Blocks.LOOM);block(-1,8,3,Blocks.CARTOGRAPHY_TABLE);
            for(int y:new int[]{1,8}){
                block(4,y,0,MoonveilWood.TABLE.get());block(3,y,0,MoonveilWood.STOOL.get());
                block(4,y+1,0,p.dusk()?HeartLanterns.DUSK.get():HeartLanterns.HEART.get());
                box(0,y,5,1,y+2,5,MoonveilWood.BOOKSHELF.get());
                block(0,y,2,HeartFloor.carpet(p.dusk()?net.minecraft.world.item.DyeColor.PURPLE:net.minecraft.world.item.DyeColor.PINK));
            }
        }
        void resident(int slot){
            var at=p.resident(slot);
            if(!v.chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(at)))return;
            var keeper=(p.dusk()?People.DUSK_KEEPER:People.PETAL_KEEPER).get().create(v.level.getLevel());
            if(keeper==null)throw new IllegalStateException("Missing Moonveil keeper type");
            keeper.setUUID(p.residentId(slot));keeper.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
            keeper.bindVillage(p.village.plaza());keeper.setPersistenceRequired();
            keeper.setVillagerData(keeper.getVillagerData().setProfession(slot==0?VillagerProfession.SHEPHERD:VillagerProfession.CARTOGRAPHER));
            var data=keeper.getPersistentData();
            data.putLong("MoonveilHouse",p.at(0,0,0).asLong());data.putInt("MoonveilSlot",slot);
            data.putLong("MoonveilBed",p.bedHead(slot).asLong());data.putLong("MoonveilWork",p.job(slot).asLong());
            data.putBoolean(PENDING,true);
            v.level.addFreshEntity(keeper);
        }
    }

    static void settle(Keeper keeper,ServerLevel level){
        var data=keeper.getPersistentData();
        if(!data.getBoolean(PENDING))return;
        if(keeper.ownerId()!=null){data.remove(PENDING);return;}
        var bed=BlockPos.of(data.getLong("MoonveilBed"));var job=BlockPos.of(data.getLong("MoonveilWork"));
        if(!level.hasChunkAt(bed)||!level.hasChunkAt(job))return;
        // Reserve each actual POI after promotion to a live chunk. Release ongoing management
        // once the household is initialized, including if a player has moved a furnishing.
        assign(keeper,level,bed,MemoryModuleType.HOME);
        assign(keeper,level,job,MemoryModuleType.JOB_SITE);
        data.remove(PENDING);
    }
    private static void assign(Keeper keeper,ServerLevel level,BlockPos at,MemoryModuleType<GlobalPos> memory){
        var brain=keeper.getBrain();
        if(brain.hasMemoryValue(memory))return;
        var type=PoiTypes.forState(level.getBlockState(at));
        if(type.isEmpty() || (memory==MemoryModuleType.HOME?!type.get().is(PoiTypes.HOME):
                !keeper.getVillagerData().getProfession().heldJobSite().test(type.get())))return;
        var pois=level.getPoiManager();
        if(pois.getType(at).isEmpty())pois.add(at,type.get());
        pois.take(type.get()::equals,(t,pos)->pos.equals(at),at,0)
                .ifPresent(pos->brain.setMemory(memory,GlobalPos.of(level.dimension(),pos)));
    }
}
