package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Each road and doorstep is surveyed against the seed's underlying land. Water and steep sites are declined. */
public final class BlossomVillage {
    public record House(int x,int z,int floor,int width,int depth,int side,int style,Block job){}
    public record Plan(GroveSites.Site site,int x,int z,int y,int[] road,int[] bends,List<House> houses,boolean dusk){
        public BlockPos plaza(){return site.pos(x,y+1,z);}
        public BlockPos bedHead(House h){return site.pos(h.x()-1,h.floor()+1,h.z()+h.side());}
        public BlockPos workstation(House h){return site.pos(h.x()+1,h.floor()+1,h.z()+h.side());}
        public BlockPos doorway(House h){return site.pos(h.x(),h.floor()+1,h.z()-h.side()*h.depth());}
        public BlockPos resident(House h){return site.pos(h.x(),h.floor()+1,h.z());}
    }
    private record Key(int gx,int gz,boolean dusk){}
    private static final Map<ServerLevel,Map<Key,Optional<Plan>>> CACHE=new WeakHashMap<>();
    private static synchronized Map<Key,Optional<Plan>> cache(ServerLevel level){return CACHE.computeIfAbsent(level,k->new ConcurrentHashMap<>());}
    private static final Block[] JOBS={Blocks.COMPOSTER,Blocks.LOOM,Blocks.BARREL,Blocks.LECTERN,Blocks.SMITHING_TABLE,Blocks.FLETCHING_TABLE,Blocks.CARTOGRAPHY_TABLE,Blocks.STONECUTTER};
    private static final int HALF=32;
    private static int ground(ServerLevel level,GroveSites.Site s,int x,int z){return GroveSites.height(level,s.wx(x,z),s.wz(x,z));}
    public static Optional<Plan> plan(ServerLevel level,GroveSites.Site s,boolean dusk){
        if(!GroveSites.settlement(level,s,dusk))return Optional.empty();
        return survey(level,s,dusk);
    }
    // Pure terrain survey used while choosing the arrival site. Never asks whether this
    // region will host a settlement: that decision itself depends on the arrival site.
    static Optional<Plan> survey(ServerLevel level,GroveSites.Site s,boolean dusk){
        var db=cache(level);if(db.size()>5000)db.clear();
        return db.computeIfAbsent(new Key(s.gx(),s.gz(),dusk),k->design(level,s,dusk));
    }
    public static Plan first(ServerLevel level,boolean dusk){
        var s=GroveSites.first(level);
        for(int ring=0;ring<15;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
            var site=GroveSites.site(level,s.gx()+dx,s.gz()+dz);if(!site.usable())continue;
            var found=plan(level,site,dusk);if(found.isPresent())return found.get();
        }
        throw new IllegalStateException("No habitable village land");
    }
    private static Optional<Plan> design(ServerLevel level,GroveSites.Site site,boolean dusk){
        var random=new Random(HeartwaterFeature.Valley.mix(site.x(),site.z(),level.getSeed())^(dusk?81347569:6234971));
        for(int attempt=0;attempt<32;attempt++){
            int x=(dusk?1:-1)*(48+random.nextInt(66)),z=-32-random.nextInt(62);
            int center=ground(level,site,x,z);
            if(center<=65||center>143||!GroveSites.cherry(level,site.wx(x,z),center+1,site.wz(x,z)))continue;
            int left=ground(level,site,x-HALF,z),right=ground(level,site,x+HALF,z);
            if(Math.min(left,right)<=64 || Math.abs(left-right)>12 || Math.abs(left-center)>7 || Math.abs(right-center)>7)continue;
            int[] road=new int[HALF*2+1],bends=new int[road.length];
            double phase=random.nextDouble()*Math.PI*2,frequency=.035+random.nextDouble()*.055;
            double amplitude=2+random.nextDouble()*3;
            for(int i=0;i<road.length;i++)
                bends[i]=(int)Math.round(amplitude*(Math.sin((i-HALF)*frequency+phase)-Math.sin(phase)));
            boolean dry=true;
            for(int i=0;i<road.length;i++){
                int y=ground(level,site,x+i-HALF,z+bends[i]);
                if(y<64||i>0&&Math.abs(y-road[i-1])>1){dry=false;break;}
                road[i]=y;
            }
            if(!dry)continue;
            List<House> houses=new ArrayList<>();
            int firstStyle=random.nextInt(VillageHouse.STYLES.size());
            for(int side:new int[]{-1,1})for(int lot=0;lot<4;lot++){
                int style=(firstStyle+houses.size())%VillageHouse.STYLES.size();
                var template=VillageHouse.STYLES.get(style);
                int hx=x-24+lot*16,width=template.width,depth=template.depth;
                int hz=z+bends[hx-x+HALF]+side*(depth+6+random.nextInt(3)),floor=ground(level,site,hx,hz-side*depth);
                int roadY=road[hx-x+HALF];boolean fits=Math.abs(floor-roadY)<=2;
                for(int ex:new int[]{-width,0,width})for(int ez:new int[]{-depth,0,depth}){
                    int earth=ground(level,site,hx+ex,hz+ez);
                    if(earth<64||Math.abs(earth-floor)>3||!GroveSites.cherry(level,site.wx(hx+ex,hz+ez),earth+1,site.wz(hx+ex,hz+ez)))fits=false;
                }
                if(fits)houses.add(new House(hx,hz,floor,width,depth,side,style,JOBS[houses.size()%JOBS.length]));
            }
            if(houses.size()>=6)return Optional.of(new Plan(site,x,z,center,road,bends,List.copyOf(houses),dusk));
        }
        return Optional.empty();
    }
    static void place(HeartwaterFeature.Valley v,Plan p){
        if(!v.overlaps(p.x()-HALF-3,p.z()-30,p.x()+HALF+3,p.z()+30))return;
        for(int dx=-HALF;dx<=HALF;dx++)for(int dz=-1;dz<=1;dz++)
            road(v,p.x()+dx,p.road()[dx+HALF],p.z()+p.bends()[dx+HALF]+dz,p.dusk());
        for(House h:p.houses()){
            int za=p.z()+p.bends()[h.x()-p.x()+HALF]+h.side()*2,zb=h.z()-h.side()*h.depth();
            int y0=p.road()[h.x()-p.x()+HALF],n=Math.abs(zb-za);
            for(int i=0;i<=n;i++)for(int dx=-1;dx<=1;dx++){
                int y=y0+(int)Math.round((h.floor()-y0)*i/(double)Math.max(1,n));
                road(v,h.x()+dx,y,za+i*h.side(),p.dusk());
            }
            home(v,p,h);
        }
        int gateIndex=p.dusk()?p.road().length-3:2;
        gate(v,p.x()+gateIndex-HALF,p.road()[gateIndex],p.z()+p.bends()[gateIndex],p.dusk());
        boolean dark=p.dusk();
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)road(v,p.x()+dx,p.y(),p.z()+dz,dark);
        v.block(p.x(),p.y(),p.z(),dark?HeartwaterFeature.LACE.get():HeartwaterFeature.QUILT.get());
        // A real meeting POI supports gossip and the vanilla midday gathering schedule.
        int bellX=p.x()+2,bellZ=p.z()-2;
        v.block(bellX,p.y(),bellZ,dark?Blocks.POLISHED_BLACKSTONE:Blocks.CALCITE);
        v.put(bellX,p.y()+1,bellZ,Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING,v.face(Direction.NORTH)));
        if(v.inside(p.x(),p.z())){
            var warden=People.BLOSSOM_WARDEN.get().create(v.level.getLevel());
            if(warden!=null){
                var at=p.plaza();warden.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
                warden.bindVillage(at);warden.setPersistenceRequired();v.level.addFreshEntity(warden);
            }
        }
    }
    private static void gate(HeartwaterFeature.Valley v,int x,int y,int z,boolean dark){
        Block post=dark?Blocks.STRIPPED_DARK_OAK_LOG:Blocks.STRIPPED_MANGROVE_LOG;
        Block cap=PinkRoofTiles.slab(dark);
        for(int dz:new int[]{-3,3}){
            floor(v,x,y,z+dz,Blocks.STONE_BRICKS);
            v.box(x,y+1,z+dz,x,y+4,z+dz,post);
            v.block(x,y+1,z+dz,Blocks.POLISHED_BLACKSTONE);
        }
        v.box(x,y+3,z-3,x,y+3,z+3,post);
        v.box(x,y+5,z-4,x,y+5,z+4,post);
        v.box(x,y+6,z-4,x,y+6,z+4,cap);
        for(int end:new int[]{-5,5})v.block(x,y+6,z+end,cap);
    }
    static void floor(HeartwaterFeature.Valley v,int x,int y,int z,Block finish){
        if(!v.inside(x,z))return;
        // Find solid ground under this individual column, including cave mouths and eroded banks.
        // Stop at the first load-bearing rock. Never leave a hanging pavement or foundation.
        for(int b=y-1;b>v.level.getMinBuildHeight();b--){
            var at=new BlockPos(v.wx(x,z),b,v.wz(x,z));var state=v.chunk.getBlockState(at);
            if(state.isSolidRender(v.level,at)&&!state.is(net.minecraft.tags.BlockTags.LEAVES)&&!state.is(net.minecraft.tags.BlockTags.LOGS))break;
            v.block(x,b,z,Blocks.COBBLESTONE);
        }
        v.block(x,y,z,finish);
    }
    private static void clear(HeartwaterFeature.Valley v,int x,int y,int z){
        if(!v.inside(x,z))return;
        int top=v.chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG,v.wx(x,z)&15,v.wz(x,z)&15);
        v.box(x,y,z,x,Math.max(y+3,top+1),z,v.air);
    }
    private static void road(HeartwaterFeature.Valley v,int x,int y,int z,boolean dark){
        if(!v.inside(x,z))return;
        int patch=Math.floorMod(HeartwaterFeature.Valley.mix(x,z,v.seed),13);
        Block surface=patch<2?Blocks.COBBLESTONE:patch<4?Blocks.ANDESITE:patch==4?Blocks.MOSSY_STONE_BRICKS:
                dark?Blocks.TUFF_BRICKS:Blocks.DIRT_PATH;
        clear(v,x,y+1,z);floor(v,x,y,z,surface);
    }
    private static void home(HeartwaterFeature.Valley v,Plan p,House h){
        int x=h.x(),z=h.z(),y=h.floor(),w=h.width(),d=h.depth(),side=h.side(),front=z-side*d;
        var house=VillageHouse.STYLES.get(h.style());
        if(!v.overlaps(x-house.radiusX,z-house.radiusZ,x+house.radiusX,z+house.radiusZ))return;
        boolean dark=p.dusk();
        Direction inward=side>0?Direction.SOUTH:Direction.NORTH;
        Block boards=dark?Blocks.DARK_OAK_PLANKS:Blocks.OAK_PLANKS;
        for(int dx=-house.radiusX;dx<=house.radiusX;dx++)for(int dz=-house.radiusZ;dz<=house.radiusZ;dz++){
            clear(v,x+dx,y+1,z+dz);
            boolean interior=Math.abs(dx)<=w&&Math.abs(dz)<=d;
            boolean veranda=Math.abs(dx)<=w+1&&dz*side==-d-1;
            if(interior||veranda)floor(v,x+dx,y,z+dz,veranda?boards:inlay(dx,dz,w,d,dark));
        }
        house.place(v,h,dark);
        // Frame the original entrance and put the settlement's crest on its lintel.
        v.block(x,y+3,front,dark?HeartwaterFeature.LACE.get():HeartwaterFeature.ROSETTE.get());
        DoorBlock door=(DoorBlock)(dark?Blocks.DARK_OAK_DOOR:Blocks.CHERRY_DOOR);
        var ds=door.defaultBlockState().setValue(DoorBlock.FACING,v.face(inward));
        // Keep both the street landing and the central interior aisle walkable.
        for(int dz=0;dz<=d+2;dz++)if(dz!=d)v.box(x,y+1,z-side*dz,x,y+2,z-side*dz,v.air);
        v.put(x,y+1,front,ds);
        v.put(x,y+2,front,ds.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
        // Keep the center aisle open, with woven mats and a low tea shelf to its sides.
        var rug=HeartFloor.carpet(dark?DyeColor.PURPLE:DyeColor.MAGENTA);
        for(int dz=-1;dz<=1;dz++)v.block(x-w+2,y+1,z+dz,rug);
        v.block(x+w-1,y+1,z,HeartFloor.slab(dark?DyeColor.GRAY:DyeColor.WHITE));
        // Bed foot faces the head. The aisle is level from the door to the workbench.
        var bed=(dark?Blocks.PURPLE_BED:Blocks.PINK_BED).defaultBlockState().setValue(BedBlock.FACING,v.face(inward));
        v.put(x-1,y+1,z,bed.setValue(BedBlock.PART,BedPart.FOOT));
        v.put(x-1,y+1,z+side,bed.setValue(BedBlock.PART,BedPart.HEAD));
        v.block(x+1,y+1,z+side,h.job());
        v.block(x+1,y+1,z-side,dark?Blocks.SOUL_LANTERN:Blocks.LANTERN);
        v.block(x-w+1,y+1,z+d-1,h.style()==0?Blocks.BOOKSHELF:dark?Blocks.POTTED_WITHER_ROSE:Blocks.POTTED_ALLIUM);
        InteriorFurnishings.house(v,h,dark,house);
        resident(v,p,x,y+1,z,false);
        if(h==p.houses().getFirst()){
            // A family home has two real beds. The youngster grows up on vanilla time.
            v.put(x+2,y+1,z,bed.setValue(BedBlock.PART,BedPart.FOOT));
            v.put(x+2,y+1,z+side,bed.setValue(BedBlock.PART,BedPart.HEAD));
            resident(v,p,x+2,y+1,z-side,true);
        }
    }
    private static Block inlay(int x,int z,int width,int depth,boolean dusk){
        // The central stripe and alternating field repeat the pink heart from the first
        // Heartsill block. Frame the room one tile inside its timber wall posts.
        if(x==0)return HeartwaterFeature.HEART.get();
        if(Math.abs(x)==width-1||Math.abs(z)==depth-1)
            return HeartFloor.full(dusk?DyeColor.BLACK:DyeColor.MAGENTA);
        if((x+z)%2==0)return HeartwaterFeature.HEART.get();
        return HeartFloor.full(dusk?DyeColor.PURPLE:DyeColor.WHITE);
    }
    private static void resident(HeartwaterFeature.Valley v,Plan p,int x,int y,int z,boolean baby){
        if(!v.inside(x,z))return;
        var keeper=(p.dusk()?People.DUSK_KEEPER:People.PETAL_KEEPER).get().create(v.level.getLevel());
        if(keeper==null)return;
        if(baby)keeper.setAge(-24000);
        var at=p.site().pos(x,y,z);
        keeper.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
        keeper.bindVillage(p.plaza());keeper.setPersistenceRequired();v.level.addFreshEntity(keeper);
    }
}

