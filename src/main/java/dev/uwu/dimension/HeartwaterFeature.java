package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;



/** All writes are clipped to the decorating chunk. Coordinates below are local to a rotated valley. */
public final class HeartwaterFeature extends Feature<NoneFeatureConfiguration> {
    private static final String MOD = "uwudimension";
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MOD);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD);
    static final DeferredBlock<Block> ROSE = stone("rose_enamel", 0);
    static final DeferredBlock<Block> GLASS = stone("tideglass", 11);
    static final DeferredBlock<Block> HEART = stone("heartsill", 5);
    private static DeferredBlock<Block> stone(String name, int light) {
        var b = BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().strength(1.8F, 6F)
                .sound(SoundType.CALCITE).lightLevel(s -> light));
        ITEMS.registerSimpleBlockItem(name, b);
        return b;
    }
    static { FEATURES.register("heartwater_landscape", HeartwaterFeature::new); }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        FEATURES.register(bus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(HeartwaterAmbience::tick);
    }
    public HeartwaterFeature() { super(NoneFeatureConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        new Valley(context.level(), new ChunkPos(context.origin())).draw();
        return true;
    }
    static final class Valley {
        final WorldGenLevel level;
        final ChunkAccess chunk;
        final int cx, cz, turns, minX, maxX, minZ, maxZ;
        final boolean shrine;
        final long seed;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final BlockState air = Blocks.AIR.defaultBlockState();
        final BlockState water = Blocks.WATER.defaultBlockState();
        final BlockState falling = water.setValue(LiquidBlock.LEVEL, 8);
        final BlockState leaf = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        Valley(WorldGenLevel level, ChunkPos pos) {
            this.level = level;
            chunk = level.getChunk(pos.x, pos.z);
            seed = level.getSeed();
            // Cell boundaries align to chunk boundaries. The first journey always faces south.
            cx = 384 * Math.floorDiv(pos.getMinBlockX() + 192, 384);
            cz = 384 * Math.floorDiv(pos.getMinBlockZ() + 192, 384);
            int style = mix(cx, cz, seed);
            turns = cx == 0 && cz == 0 ? 0 : style & 3;
            shrine = cx == 0 && cz == 0 || (style & 12) == 0;
            int ax = pos.getMinBlockX()-cx, az = pos.getMinBlockZ()-cz;
            int bx=ax+15,bz=az+15;
            int px=switch(turns){case 1->az;case 2->-bx;case 3->-bz;default->ax;};
            int pz=switch(turns){case 1->-bx;case 2->-bz;case 3->ax;default->az;};
            minX=px;maxX=px+15;minZ=pz;maxZ=pz+15;
        }
        static int mix(int x,int z,long seed) {
            long h=seed ^ (x*0x632BE59BD9B4E019L) ^ (z*0x9E3779B97F4A7C15L);
            h=(h^(h>>>27))*0x94D049BB133111EBL;
            return (int)(h^(h>>>31));
        }
        int wx(int x,int z) { return cx + switch(turns){case 1->-z;case 2->-x;case 3->z;default->x;}; }
        int wz(int x,int z) { return cz + switch(turns){case 1->x;case 2->-z;case 3->-x;default->z;}; }
        Direction face(Direction d) { for(int i=0;i<turns;i++)d=d.getClockWise();return d; }
        boolean inside(int x,int z){return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}
        boolean overlaps(int a,int b,int c,int d){return a<=maxX&&c>=minX&&b<=maxZ&&d>=minZ;}
        void put(int x,int y,int z, BlockState s){
            if(inside(x,z))chunk.setBlockState(cursor.set(wx(x,z),y,wz(x,z)),s,false);
        }
        void block(int x,int y,int z,Block b){put(x,y,z,b.defaultBlockState());}
        void box(int x1,int y1,int z1,int x2,int y2,int z2,BlockState s){
            for(int z=Math.max(minZ,z1);z<=Math.min(maxZ,z2);z++)
                for(int x=Math.max(minX,x1);x<=Math.min(maxX,x2);x++)
                    for(int y=y1;y<=y2;y++)put(x,y,z,s);
        }
        void box(int a,int b,int c,int d,int e,int f,Block v){box(a,b,c,d,e,f,v.defaultBlockState());}
        BlockState stair(Block b, Direction d) { return b.defaultBlockState().setValue(StairBlock.FACING,face(d)); }
        void draw() {
            if(!overlaps(-110,-100,120,154))return;
            surface();
            if(overlaps(-62,-65,62,57)){
                trees();
                hamlet(-35,-22,false);
                hamlet(35,-36,true);
            }
            if(overlaps(-62,8,69,147)){
                falls();
                pools();
                descent();
                deep();
                bathhouse();
                archive();
                if(shrine) hangingShrine();
            }
        }
        int ridgeFront(int x){
            return 20 + (int)(2*Math.sin(x*.23)+3*Math.sin(x*.097));
        }
        int landscape(int x,int z) {
            if(z>=ridgeFront(x) && Math.abs(x)<64){
                double taper=Math.min(1,(64-Math.abs(x))/10.);
                double wave=4*Math.sin(x*.14)+3*Math.cos(z*.09)+5*Math.cos(x*.063+z*.03);
                return 80+(int)((24+wave)*taper);
            }
            return 80;
        }
        void surface() {
            for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++){
                double fade=Math.min((100-Math.abs(x))/31.,Math.min((z+85)/28.,(153-z)/26.));
                if(fade<=0)continue;
                fade=Math.min(1,fade);fade=fade*fade*(3-2*fade);
                int top=chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG,wx(x,z)&15,wz(x,z)&15);
                int target=(int)Math.round(top+(landscape(x,z)-top)*fade);
                int h=mix(x,z,seed);
                for(int y=target+1;y<=top;y++)put(x,y,z,air);
                for(int y=Math.min(top-1,target-4);y<target-2;y++){
                    Block b=y>82 && y%9<2?Blocks.CALCITE:y>82 && y%11<4?Blocks.TUFF:Blocks.STONE;
                    block(x,y,z,b);
                }
                box(x,target-2,z,x,target-1,z,Blocks.DIRT);
                block(x,target,z,Blocks.GRASS_BLOCK);
                if(Math.abs(x)<4 && z>=-3 && z<22)continue;
                if((h&15)==1)block(x,target+1,z,Blocks.PINK_PETALS);
                else if((h&127)==4)block(x,target+1,z,Blocks.ALLIUM);
                else if((h&63)==8)block(x,target+1,z,Blocks.AZURE_BLUET);
            }
        }
        void trees(){
            for(int x=-63;x<=63;x+=13)for(int z=-65;z<=57;z+=15){
                int h=mix(x,z,seed);
                int tx=x+(h&3),tz=z+((h>>>2)&3);
                if(!overlaps(tx-4,tz-4,tx+4,tz+4) || Math.abs(tx)<9 && tz<24 ||
                        Math.abs(tx+35)<19 && Math.abs(tz+22)<17 ||
                        Math.abs(tx-35)<19 && Math.abs(tz+36)<17 ||
                        Math.abs(tz-ridgeFront(tx))<4)continue;
                int y=landscape(tx,tz), lean=(h&16)==0?1:-1;
                int height=5+((h>>>5)&1);
                box(tx,y+1,tz,tx,y+height,tz,Blocks.CHERRY_LOG);
                block(tx+lean,y+height,tz,Blocks.CHERRY_LOG);
                block(tx+2*lean,y+height+1,tz,Blocks.CHERRY_LOG);
                for(int dx=-4;dx<=4;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-1;dy<=2;dy++){
                    if(dx*dx*.62+dz*dz+dy*dy*2>11 || dx==0&&dz==0&&dy<=0)continue;
                    put(tx+dx,y+height+1+dy,tz+dz,leaf);
                }
            }
        }
        void falls() {
            // A shallow bowl with stepping stones leads through the middle curtain.
            for(int x=-12;x<=12;x++)for(int z=8;z<=26;z++){
                double radius=x*x/135.0+(z-18)*(z-18)/95.;
                if(radius>1||z>=ridgeFront(x))continue;
                block(x,78,z,Blocks.CLAY);block(x,79,z,Blocks.WATER);put(x,80,z,water);put(x,81,z,air);
                if(x==0 || x==1)block(x,80,z,Blocks.SMOOTH_BASALT);
                else if((mix(x,z,seed)&31)==0)block(x,81,z,Blocks.LILY_PAD);
            }
            for(int center:new int[]{-23,0,19}){
                int lip=ridgeFront(center);
                int height=landscape(center,lip+1);
                for(int x=center-1;x<=center+1;x++){
                    for(int z=lip-1;z<=lip;z++){
                        box(x,81,z,x,height-1,z,falling);
                        put(x,height,z,water);
                    }
                    put(x,height,lip+1,water);
                }
            }
        }
        void descent(){
            // Consecutive stairs each have a solid tread, six clear blocks and a level landing.
            for(int z=18;z<=59;z++) {
                int floor=80-Math.clamp(z-25,0,28);
                box(-3,floor-1,z,3,floor-1,z,Blocks.STONE);
                box(-3,floor,z,3,floor,z,Blocks.MOSS_BLOCK);
                box(-3,floor+1,z,3,floor+5,z,air);
                if(z>=25&&z<53)box(-2,floor,z,2,floor,z,stair(Blocks.MOSSY_STONE_BRICK_STAIRS,Direction.NORTH));
                for(int x:new int[]{-3,3})if(z%7==0)block(x,floor+1,z,GLASS.get());
            }
            // Rehang water in front of the dry lintel, leaving a visible opening on each bank.
            for(int x=-1;x<=1;x++)box(x,81,19,x,86,19,falling);
        }
        void pools(){
            cavern(0,67,19,15,52,14,false);
            // A solid boardwalk crosses the first lake. Both mouths meet the central axis.
            box(-1,52,53,1,52,82,Blocks.CHERRY_PLANKS);
            for(int z=55;z<81;z+=5)for(int x:new int[]{-2,2})block(x,52,z,GLASS.get());
            box(-3,53,78,3,57,82,air);
            box(-3,52,78,3,52,82,ROSE.get());
            box(0,52,65,19,52,67,Blocks.CHERRY_PLANKS);
        }
        void cavern(int cx,int cz,int rx,int rz,int shore,int height,boolean dark){
            if(!overlaps(cx-rx,cz-rz,cx+rx,cz+rz))return;
            for(int x=Math.max(minX,cx-rx);x<=Math.min(maxX,cx+rx);x++)for(int z=Math.max(minZ,cz-rz);z<=Math.min(maxZ,cz+rz);z++){
                double dx=(x-cx)/(double)rx,dz=(z-cz)/(double)rz;
                double r=dx*dx+dz*dz;
                if(r>1)continue;
                double arch=Math.sqrt(1-r);
                int roof=shore+4+(int)(height*arch+Math.sin(x*.39)*Math.sin(z*.31)*1.7);
                int depth=r<.7?(int)(1+3*(.7-r)):0;
                int floor=shore-depth;
                int h=mix(x,z,seed);
                Block bed=(h&31)==0?GLASS.get():(dark?Blocks.DEEPSLATE:Blocks.CLAY);
                block(x,floor,z,bed);block(x,roof,z,(h&15)==0?GLASS.get():Blocks.SMOOTH_BASALT);
                box(x,floor+1,z,x,shore,z,water);
                box(x,shore+1,z,x,roof-1,z,air);
                if((h&63)==15)block(x,roof-1,z,Blocks.SHROOMLIGHT);
                if(depth==0 &&(h&15)==0)block(x,shore+1,z,Blocks.SMALL_AMETHYST_BUD);
            }
        }
        void deep(){
            // Three interlocking pockets: an echo pool, a blue grotto, then the bell lake.
            cavern(38,76,13,14,29,10,false);
            cavern(42,115,20,17,0,18,true);
            cavern(16,129,19,12,-19,12,false);
            for(int x=16;x<=41;x++){
                int floor=52-Math.clamp(x-17,0,23);
                passage(x,floor,65,x,floor+5,67);
                if(x>=17&&x<40)box(x,floor,65,x,floor,67,stair(Blocks.POLISHED_ANDESITE_STAIRS,Direction.WEST));
            }
            for(int z=68;z<=109;z++){
                int floor=29-Math.clamp(z-80,0,29);
                passage(40,floor,z,42,floor+5,z);
                if(z>=80&&z<109)box(39,floor,z,41,floor,z,stair(Blocks.DEEPSLATE_BRICK_STAIRS,Direction.NORTH));
            }
            for(int x=16;x<=40;x++){
                int floor=Math.max(-19,Math.min(0,x-39));
                passage(x,floor,109,x,floor+5,111);
                if(x<=39&&x>20)box(x,floor,109,x,floor,111,stair(Blocks.DEEPSLATE_TILE_STAIRS,Direction.EAST));
            }
            passage(15,-19,110,17,-14,140);
            for(int z=70;z<=105;z+=6)block(41,30-Math.clamp(z-80,0,29),z,GLASS.get());
            for(int z=113;z<138;z+=4)block(18,-19,z,GLASS.get());
            for(int z=112;z<=114;z++)box(48,1,z,48,14,z,falling);
        }
        void passage(int x,int floor,int z,int x2,int ceiling,int z2){
            box(x,floor-1,z,x2,floor,z2,Blocks.POLISHED_ANDESITE);
            box(x,floor+1,z,x2,ceiling,z2,air);
        }
        void shell(int x1,int y1,int z1,int x2,int y2,int z2,Block finish){
            if(finish != Blocks.CRIMSON_PLANKS){
                Block rock = y1 < 0 ? Blocks.DEEPSLATE : Blocks.TUFF;
                box(x1-1,y1-1,z1-1,x2+1,y2+1,z1-1,rock);box(x1-1,y1-1,z2+1,x2+1,y2+1,z2+1,rock);
                box(x1-1,y1-1,z1,x1-1,y2+1,z2,rock);box(x2+1,y1-1,z1,x2+1,y2+1,z2,rock);
                box(x1,y1-1,z1,x2,y1-1,z2,rock);box(x1,y2+1,z1,x2,y2+1,z2,rock);
            }
            // Every outside face is obsidian, overlapping at all twelve edges.
            box(x1,y1,z1,x2,y2,z1,Blocks.OBSIDIAN);box(x1,y1,z2,x2,y2,z2,Blocks.OBSIDIAN);
            box(x1,y1,z1,x1,y2,z2,Blocks.OBSIDIAN);box(x2,y1,z1,x2,y2,z2,Blocks.OBSIDIAN);
            box(x1,y1,z1,x2,y1,z2,Blocks.OBSIDIAN);box(x1,y2,z1,x2,y2,z2,Blocks.OBSIDIAN);
            box(x1+1,y1+1,z1+1,x2-1,y2-1,z2-1,finish);
            box(x1+2,y1+2,z1+2,x2-2,y2-2,z2-2,air);
        }
        void doorway(int x,int y,int z,Block trim){
            box(x-2,y,z-2,x+2,y+4,z-1,trim);
            box(x-1,y+1,z-2,x+1,y+3,z+1,air);
            block(x,y+4,z-2,HEART.get());block(x-2,y+2,z-2,GLASS.get());block(x+2,y+2,z-2,GLASS.get());
        }
        void bathhouse(){
            if(!overlaps(-14,79,14,106))return;
            shell(-14,51,82,14,62,106,ROSE.get());
            doorway(0,52,82,Blocks.QUARTZ_BRICKS);
            box(-5,52,88,5,52,99,water);
            for(int z=86;z<=102;z+=4)for(int x:new int[]{-11,11}){
                block(x,60,z,Blocks.SEA_LANTERN);
                box(x,53,z,x,54,z,Blocks.CHERRY_PLANKS);
                put(x,55,z,Blocks.WHITE_CARPET.defaultBlockState());
            }
            for(int x:new int[]{-4,0,4}){
                block(x,53,101,Blocks.SMOOTH_QUARTZ);
                block(x,54,101,Blocks.PINK_CARPET);
            }
            for(int z:new int[]{89,96}){
                box(8,53,z,11,57,z,Blocks.CHERRY_PLANKS);
                box(8,57,z,8,57,z+3,HEART.get());
                block(10,53,z+2,Blocks.LIGHT_GRAY_CARPET);
            }
            for(int z=97;z<102;z++)block(7+z%2,53,z,Blocks.GRAY_CARPET);
            chest(0,53,103,"rose_bathhouse");
        }
        void archive(){
            if(!overlaps(5,137,27,151))return;
            shell(5,-20,139,27,-10,150,Blocks.DEEPSLATE_TILES);
            doorway(16,-19,139,Blocks.CHISELED_TUFF_BRICKS);
            for(int x=8;x<=24;x+=4)for(int z=143;z<=147;z++){
                if(x==16 || x==24)continue;
                box(x,-18,z,x,-16,z,Blocks.BOOKSHELF);
                block(x,-15,z,HEART.get());
            }
            for(int x=8;x<26;x+=4)block(x,-12,145,GLASS.get());
            chest(23,-18,147,"tide_archive");
        }
        void hangingShrine(){
            if(!overlaps(29,106,61,129))return;
            // Whole roof is inverted: broad eaves at its top narrow to the downward finial.
            // Human-accessible gallery and ascent stay upright below the ceiling anchors.
            for(int i=0;i<5;i++)box(50-i,3+i,118-i,52+i,3+i,120+i,
                    i%2==0?Blocks.DARK_PRISMARINE:Blocks.POLISHED_BLACKSTONE);
            box(44,8,112,58,8,126,Blocks.WARPED_PLANKS);
            for(int x:new int[]{45,57})for(int z:new int[]{113,125}){
                box(x,9,z,x,26,z,Blocks.STRIPPED_CRIMSON_STEM);
                block(x,7,z,Blocks.SHROOMLIGHT);
            }
            for(int z:new int[]{112,126})box(44,12,z,58,12,z,Blocks.RED_NETHER_BRICKS);
            box(57,9,113,57,11,125,Blocks.CRIMSON_PLANKS);
            for(int z:new int[]{115,123}) {
                block(56,11,z,Blocks.OCHRE_FROGLIGHT);block(47,7,z,Blocks.OCHRE_FROGLIGHT);
            }
            // Upside-down torii: twin hanging uprights with lower crossbars.
            for(int z:new int[]{115,122})box(44,2,z,44,8,z,Blocks.STRIPPED_CRIMSON_STEM);
            box(44,2,114,44,2,123,Blocks.NETHER_BRICKS);box(44,4,115,44,4,122,Blocks.RED_NETHER_BRICKS);
            passage(40,0,109,42,5,118);
            for(int z=118;z<=125;z++){
                int floor=z-118;
                passage(40,floor,z,42,floor+5,z);
                box(40,floor+1,z,42,floor+1,z,stair(Blocks.CRIMSON_STAIRS,Direction.SOUTH));
            }
            passage(40,8,126,48,13,127);
            shell(50,7,115,57,15,123,Blocks.CRIMSON_PLANKS);
            // Keep the gallery level around the reliquary's one gilded north door.
            doorway(53,8,115,Blocks.CRIMSON_PLANKS);
            chest(55,9,120,"inverted_shrine");
        }
        void hamlet(int ox,int oz,boolean dark){
            if(!overlaps(ox-19,oz-16,ox+19,oz+16))return;
            Block wood=dark?Blocks.DARK_OAK_PLANKS:Blocks.CHERRY_PLANKS;
            Block post=dark?Blocks.POLISHED_BLACKSTONE_BRICKS:Blocks.STRIPPED_CHERRY_LOG;
            Block roof=dark?Blocks.DEEPSLATE_TILES:Blocks.PURPUR_BLOCK;
            box(ox-16,80,oz-1,ox+16,80,oz+1,dark?Blocks.POLISHED_TUFF:Blocks.CALCITE);
            for(int off:new int[]{-11,0,11}){
                int x=ox+off,z=oz+(off==0?7:-7);
                int front=off==0?z-3:z+3;
                box(x-3,80,z-3,x+3,83,z+3,wood);
                box(x-2,81,z-2,x+2,83,z+2,air);
                box(x,81,front,x,83,front,air);
                for(int dx:new int[]{-3,3})for(int dz:new int[]{-3,3})box(x+dx,81,z+dz,x+dx,84,z+dz,post);
                for(int dx:new int[]{-3,3})block(x+dx,82,z,Blocks.AMETHYST_BLOCK);
                for(int i=0;i<4;i++)box(x-4+i,84+i,z-4,x+4-i,84+i,z+4,roof);
                for(int end:new int[]{-4,4})block(x,88,z+end,dark?Blocks.CHISELED_DEEPSLATE:HEART.get());
                block(x,83,z,Blocks.SHROOMLIGHT);
                box(x,80,Math.min(front,oz),x,80,Math.max(front,oz),wood);
                spawn(x,81,z,dark);
            }
            for(int x:new int[]{ox-6,ox+6}){
                box(x,81,oz+2,x,83,oz+2,post);
                block(x,84,oz+2,dark?Blocks.SOUL_LANTERN:Blocks.LANTERN);
            }
        }
        void spawn(int x,int y,int z,boolean dark){
            if(!inside(x,z))return;
            var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(MOD,dark?"dusk_keeper":"petal_keeper"));
            if(type.create(level.getLevel()) instanceof Mob mob){
                mob.moveTo(wx(x,z)+.5,y,wz(x,z)+.5,0,0);mob.setPersistenceRequired();
                level.addFreshEntity(mob);
            }
        }
        void chest(int x,int y,int z,String table){
            if(!inside(x,z))return;
            BlockPos p=new BlockPos(wx(x,z),y,wz(x,z));
            Block loot=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("lootr","lootr_chest"));
            BlockState state=loot.defaultBlockState().setValue(ChestBlock.FACING,face(Direction.NORTH));
            level.setBlock(p,state,2);
            if(level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity chest){
                chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.fromNamespaceAndPath(MOD,"chests/"+table)));
                chest.setChanged();
            } else throw new IllegalStateException("Missing Lootr chest block entity at "+p);
        }
    }
}



