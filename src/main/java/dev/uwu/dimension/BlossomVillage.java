package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
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
    private static final int HALF=29;
    private static int ground(ServerLevel level,GroveSites.Site s,int x,int z){return GroveSites.height(level,s.wx(x,z),s.wz(x,z));}
    public static Optional<Plan> plan(ServerLevel level,GroveSites.Site s,boolean dusk){
        if(!GroveSites.settlement(level,s,dusk))return Optional.empty();
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
            for(int side:new int[]{-1,1})for(int lot=0;lot<4;lot++){
                int hx=x-22+lot*14+random.nextInt(3),width=3+random.nextInt(2),depth=3+random.nextInt(2);
                int hz=z+bends[hx-x+HALF]+side*(depth+5+random.nextInt(3)),floor=ground(level,site,hx,hz-side*depth);
                int roadY=road[hx-x+HALF];boolean fits=Math.abs(floor-roadY)<=2;
                for(int ex:new int[]{-width,0,width})for(int ez:new int[]{-depth,0,depth}){
                    int earth=ground(level,site,hx+ex,hz+ez);
                    if(earth<64||Math.abs(earth-floor)>3||!GroveSites.cherry(level,site.wx(hx+ex,hz+ez),earth+1,site.wz(hx+ex,hz+ez)))fits=false;
                }
                if(fits)houses.add(new House(hx,hz,floor,width,depth,side,random.nextInt(4),JOBS[houses.size()%JOBS.length]));
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
    private static void floor(HeartwaterFeature.Valley v,int x,int y,int z,Block finish){
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
        clear(v,x,y+1,z);floor(v,x,y,z,dark?Blocks.TUFF_BRICKS:Blocks.DIRT_PATH);
    }
    private static void home(HeartwaterFeature.Valley v,Plan p,House h){
        int x=h.x(),z=h.z(),y=h.floor(),w=h.width(),d=h.depth(),side=h.side(),front=z-side*d;
        if(!v.overlaps(x-w-1,z-d-1,x+w+1,z+d+1))return;
        boolean dark=p.dusk();
        Block wall=dark?(h.style()%2==0?Blocks.DEEPSLATE_BRICKS:Blocks.DARK_OAK_PLANKS):(h.style()%2==0?Blocks.CALCITE:Blocks.CHERRY_PLANKS);
        Block timber=dark?Blocks.STRIPPED_DARK_OAK_LOG:Blocks.STRIPPED_CHERRY_LOG;
        Block roof=dark?Blocks.DEEPSLATE_TILE_STAIRS:(h.style()==2?Blocks.BRICK_STAIRS:Blocks.CHERRY_STAIRS);
        Direction inward=side>0?Direction.SOUTH:Direction.NORTH;
        for(int dx=-w-1;dx<=w+1;dx++)for(int dz=-d-1;dz<=d+1;dz++){
            clear(v,x+dx,y+1,z+dz);
            if(Math.abs(dx)>w||Math.abs(dz)>d)continue;
            floor(v,x+dx,y,z+dz,dark?Blocks.SPRUCE_PLANKS:Blocks.BIRCH_PLANKS);
            if(Math.abs(dx)==w||Math.abs(dz)==d)v.box(x+dx,y+1,z+dz,x+dx,y+4,z+dz,Math.abs(dx)==w&&Math.abs(dz)==d?timber:wall);
        }
        // Both gables and a stepped, overhanging pitched roof, with the stair backs toward the ridge.
        for(int dx=-w-1;dx<=w+1;dx++){
            int roofY=y+4+w+1-Math.abs(dx);
            for(int dz:new int[]{-d,d})if(Math.abs(dx)<=w)v.box(x+dx,y+4,z+dz,x+dx,roofY-1,z+dz,wall);
            if(dx==0)v.box(x,roofY,z-d-1,x,roofY,z+d+1,dark?Blocks.POLISHED_BLACKSTONE:Blocks.MANGROVE_PLANKS);
            else v.box(x+dx,roofY,z-d-1,x+dx,roofY,z+d+1,v.stair(roof,dx<0?Direction.EAST:Direction.WEST));
        }
        for(int dx:new int[]{-w,w})v.box(x+dx,y+2,z-1,x+dx,y+3,z+1,dark?Blocks.PURPLE_STAINED_GLASS_PANE:Blocks.PINK_STAINED_GLASS_PANE);
        for(int dx:new int[]{-2,2})v.block(x+dx,y+2,front,dark?HeartwaterFeature.LACE.get():HeartwaterFeature.ROSETTE.get());
        v.block(x,y+3,front,dark?HeartwaterFeature.LACE.get():HeartwaterFeature.QUILT.get());
        DoorBlock door=(DoorBlock)(dark?Blocks.DARK_OAK_DOOR:Blocks.CHERRY_DOOR);
        var ds=door.defaultBlockState().setValue(DoorBlock.FACING,v.face(inward));
        v.put(x,y+1,front,ds);v.put(x,y+2,front,ds.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
        if(h.style()==0){ // a glass-fronted florist's window
            v.put(x+w,y+1,z,Blocks.GLASS.defaultBlockState());
            v.block(x+w-1,y+1,z,dark?Blocks.POTTED_WITHER_ROSE:Blocks.POTTED_FLOWERING_AZALEA);
        } else if(h.style()==1){ // a faience chimney rises through the far roof slope
            v.box(x+w-1,y+3,z+side*(d-1),x+w-1,y+8,z+side*(d-1),dark?Blocks.POLISHED_BLACKSTONE_BRICKS:Blocks.BRICKS);
            v.block(x+w-1,y+9,z+side*(d-1),dark?Blocks.BLACKSTONE_WALL:Blocks.BRICK_WALL);
        } else if(h.style()==2){ // a hanging lantern marks the porch
            v.block(x,y+4,front-side,timber);
            v.put(x,y+3,front-side,(dark?Blocks.SOUL_LANTERN:Blocks.LANTERN).defaultBlockState().setValue(LanternBlock.HANGING,true));
        } else { // an illuminated rose window fills the upper gable
            v.block(x,y+5,front,dark?HeartwaterFeature.LACE.get():HeartwaterFeature.ROSETTE.get());
            v.block(x,y+6,front,dark?Blocks.PURPLE_STAINED_GLASS:Blocks.PINK_STAINED_GLASS);
        }
        // Bed foot faces the head. The aisle is level from the door to the workbench.
        var bed=(dark?Blocks.PURPLE_BED:Blocks.PINK_BED).defaultBlockState().setValue(BedBlock.FACING,v.face(inward));
        v.put(x-1,y+1,z,bed.setValue(BedBlock.PART,BedPart.FOOT));
        v.put(x-1,y+1,z+side,bed.setValue(BedBlock.PART,BedPart.HEAD));
        v.block(x+1,y+1,z+side,h.job());
        v.block(x+1,y+1,z-side,dark?Blocks.SOUL_LANTERN:Blocks.LANTERN);
        v.block(x-w+1,y+1,z+d-1,h.style()==0?Blocks.BOOKSHELF:dark?Blocks.POTTED_WITHER_ROSE:Blocks.POTTED_ALLIUM);
        if(v.inside(x,z)){
            var keeper=(dark?People.DUSK_KEEPER:People.PETAL_KEEPER).get().create(v.level.getLevel());
            if(keeper!=null){var at=p.resident(h);keeper.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);keeper.bindVillage(p.plaza());keeper.setPersistenceRequired();v.level.addFreshEntity(keeper);}
        }
    }
}

