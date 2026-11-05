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
    static final DeferredBlock<Block> QUILT = stone("petal_quilt", 2);
    static final DeferredBlock<Block> ROSETTE = stone("rosette_mosaic", 8);
    static final DeferredBlock<Block> LACE = stone("midnight_lace", 5);
    static final DeferredBlock<Blossom> BLOSSOM = BLOCKS.register("drift_blossom", Blossom::new);
    static { ITEMS.register("drift_blossom", () -> new net.minecraft.world.item.PlaceOnWaterBlockItem(BLOSSOM.get(), new net.minecraft.world.item.Item.Properties())); }
    public static final class Blossom extends WaterlilyBlock {
        public Blossom(){super(BlockBehaviour.Properties.of().instabreak().noCollission().noOcclusion().sound(SoundType.LILY_PAD));}
    }
    public static void reveal(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity rabbit, net.minecraft.world.entity.Entity keeper){
        Bellwake.reveal(level,rabbit,keeper);
    }
    private static DeferredBlock<Block> stone(String name, int light) {
        var b = BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().strength(1.8F, 6F)
                .sound(SoundType.CALCITE).lightLevel(s -> light));
        ITEMS.registerSimpleBlockItem(name, b);
        return b;
    }
    static {
        FEATURES.register("heartwater_landscape", ()->new HeartwaterFeature(false));
        FEATURES.register("blossom_settlements", ()->new HeartwaterFeature(true));
    }
    public static void register(IEventBus bus) {
        Bellwake.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        FEATURES.register(bus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(HeartwaterAmbience::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(HeartwaterFeature::finishNewStairs);
    }
    private final boolean surface;
    private HeartwaterFeature(boolean surface) { super(NoneFeatureConfiguration.CODEC);this.surface=surface; }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // The biome modifier also participates in vanilla cherry and lush biomes. It must
        // never write a landmark in any dimension other than the new Blossom Reach.
        var level=context.level().getLevel();
        if (!level.dimension().equals(ReefLife.REACH)) return false;
        var pos=new ChunkPos(context.origin());
        var site=GroveSites.forChunk(level,pos);
        if(!site.usable())return false;
        var valley=new Valley(context.level(),context.level().getChunk(pos.x,pos.z),site);
        if(surface)valley.surfaceLandmarks();else valley.draw();
        return true;
    }
    // Neighboring chunks can decorate across the border after our own feature has run.
    // Finish the two buried, connected stair flights once at promotion to a full chunk.
    // Work only through the supplied chunk, without requesting a neighboring live chunk.
    private static void finishNewStairs(net.neoforged.neoforge.event.level.ChunkEvent.Load event){
        if(!event.isNewChunk() || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || !level.dimension().equals(ReefLife.REACH))return;
        var chunk=event.getChunk();
        var site=GroveSites.forChunk(level,chunk.getPos());
        if(!site.usable())return;
        var valley=new Valley(level,chunk,site);
        if(valley.overlaps(15,68,41,136)&&GroveSites.adventure(level,site))valley.buriedStairs();
        if(valley.overlaps(-182,-155,182,35))for(boolean dusk:new boolean[]{false,true}){
            var plan=BlossomVillage.plan(level,site,dusk);
            if(plan.isPresent()){
                var village=plan.get();
                if(valley.overlaps(village.x()-58,village.z()-58,village.x()+58,village.z()+58)){
                    VillageTrees.root(level,chunk);break;
                }
            }
        }
    }
    static final class Valley {
        final WorldGenLevel level;
        final ChunkAccess chunk;
        final int cx, cz, turns, minX, maxX, minZ, maxZ;
        final boolean shrine;
        final long seed;
        final GroveSites.Site site;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final BlockState air = Blocks.AIR.defaultBlockState();
        final BlockState water = Blocks.WATER.defaultBlockState();
        final BlockState falling = water.setValue(LiquidBlock.LEVEL, 8);
        final BlockState leaf = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        Valley(WorldGenLevel level, ChunkAccess chunk,GroveSites.Site site) {
            ChunkPos pos=chunk.getPos();
            this.level = level;
            this.chunk = chunk;
            seed = level.getSeed();
            this.site=site;
            cx=site.x();cz=site.z();turns=site.turns();shrine=GroveSites.shrine(level.getLevel(),site);
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
            if(!inside(x,z))return;
            cursor.set(wx(x,z),y,wz(x,z));
            BlockState before=chunk.setBlockState(cursor,s,false);
            if(before!=null && before.hasBlockEntity() && before.getBlock()!=s.getBlock())chunk.removeBlockEntity(cursor);
        }
        void block(int x,int y,int z,Block b){put(x,y,z,b.defaultBlockState());}
        void box(int x1,int y1,int z1,int x2,int y2,int z2,BlockState s){
            for(int z=Math.max(minZ,z1);z<=Math.min(maxZ,z2);z++)
                for(int x=Math.max(minX,x1);x<=Math.min(maxX,x2);x++)
                    for(int y=y1;y<=y2;y++)put(x,y,z,s);
        }
        void box(int a,int b,int c,int d,int e,int f,Block v){box(a,b,c,d,e,f,v.defaultBlockState());}
        BlockState stair(Block b, Direction d) { return b.defaultBlockState().setValue(StairBlock.FACING,face(d)); }
        void surfaceLandmarks(){
            // Place doors, foundations and the spring bank before vanilla grows its cherry trees.
            // Later tree placements therefore see final ground, and never get amputated by grading.

            if(overlaps(-166,-125,166,-6)) {
                for(boolean dusk:new boolean[]{false,true})BlossomVillage.plan(level.getLevel(),site,dusk).ifPresent(p->BlossomVillage.place(this,p));
            }
            if(GroveSites.adventure(level.getLevel(),site))falls();
        }
        void draw() {
            if(!overlaps(-62,Math.min(site.lip()-15,8),70,165)||!GroveSites.adventure(level.getLevel(),site))return;
            pools(); deep(); bathhouse(); archive(); if(shrine)hangingShrine();

            MirrorCourt.build(this);
            descent();
        }
        void falls(){
            int lip=site.lip(),floor=site.entryY();
            if(!overlaps(-13,lip-14,13,lip+9))return;
            // A small rounded spring head, tied into the actual hillside. No other
            // surface area is raised or flattened. The lip alone projects over the cave mouth.
            for(int z=lip-12;z<=lip+8;z++)for(int x=-12;x<=12;x++){
                if(!inside(x,z))continue;
                double radius=x*x/150.0+(z-lip)*(z-lip)/100.0;
                if(radius>=1)continue;
                int ground=GroveSites.height(level.getLevel(),wx(x,z),wz(x,z));
                int top=chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG,wx(x,z)&15,wz(x,z)&15);
                int springHeight=z<lip?floor:floor+7;
                int target=(int)Math.round(ground+(springHeight-ground)*(1-Math.pow(radius,1.5)));
                boolean lane=Math.abs(x)<=2&&z>=lip-10&&z<=lip;
                if(lane)target=z<lip?floor:floor+7;
                for(int y=Math.min(ground-2,target-2);y<=target;y++)block(x,y,z,y==target?Blocks.GRASS_BLOCK:y>target-3?Blocks.DIRT:Blocks.STONE);
                if(top>target)box(x,target+1,z,x,top+1,z,air);
                if(!lane && (mix(x,z,seed)&7)==0)block(x,target+1,z,Blocks.PINK_PETALS);
                if(z==lip&&Math.abs(x)<=2){
                    box(x,floor+1,z,x,floor+7,z,Blocks.MOSSY_COBBLESTONE);
                    block(x,floor+8,z,Blocks.WATER);
                }
                if(z==lip-1&&Math.abs(x)<=1)box(x,floor+1,z,x,floor+7,z,falling);
            }
            for(int z=lip-10;z<lip-1;z++)for(int x=-2;x<=2;x++){
                block(x,floor,z,Math.abs(x)==2?Blocks.MOSS_BLOCK:Blocks.SMOOTH_BASALT);
                box(x,floor+1,z,x,floor+4,z,air);
            }
            // Shallow cups curve along either side of the causeway. Only a few rooted
            // blossoms float above source water; unoccupied edge columns blend into moss.
            for(int side:new int[]{-1,1})for(int dx=-2;dx<=2;dx++)for(int dz=-3;dz<=3;dz++){
                double edge=dx*dx/6.5+dz*dz/11.;
                if(edge>1)continue;
                int x=side*(5+dx),z=lip-4+dz;
                block(x,floor-1,z,Blocks.CLAY);block(x,floor,z,Blocks.WATER);
                box(x,floor+1,z,x,floor+3,z,air);
                if((mix(x,z,seed)&7)==1)block(x,floor+1,z,BLOSSOM.get());
            }
        }
        void descent(){
            int lip=site.lip(),first=lip+4;
            for(int z=lip-1;z<=56;z++) {
                int floor=site.entryY()-Math.clamp(z-first,0,site.entryY()-52);
                passage(-2,floor,z,2,floor+4,z);
                if(z>=first&&z<53)box(-2,floor,z,2,floor,z,stair(Blocks.MOSSY_STONE_BRICK_STAIRS,Direction.NORTH));
                if(z>lip+1 && z%7==0){block(-3,floor+2,z,GLASS.get());block(3,floor+2,z,GLASS.get());}
            }
            for(int x=-1;x<=1;x++)box(x,site.entryY()+1,lip-1,x,site.entryY()+6,lip-1,falling);
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
            cavern(-26,127,17,15,-48,16,false);
            // A final, tranquil spring below the archive: quiet water, bright mineral beds.
            for(int x=-23;x<=16;x++){
                int floor=-19-Math.clamp(14-x,0,29);
                passage(x,floor,126,x,floor+5,128);
                if(x<=14&&x>-15)box(x,floor,126,x,floor,128,stair(Blocks.MOSSY_STONE_BRICK_STAIRS,Direction.EAST));
            }
            for(int dz=-1;dz<=1;dz++)box(-34,-47,129+dz,-34,-35,129+dz,falling);
            for(int x=-37;x<-20;x+=4)block(x,-48,123,GLASS.get());
            for(int x=16;x<=41;x++){
                int floor=52-Math.clamp(x-17,0,23);
                passage(x,floor,65,x,floor+5,67);
                if(x>=17&&x<40)box(x,floor,65,x,floor,67,stair(Blocks.POLISHED_ANDESITE_STAIRS,Direction.WEST));
            }
            buriedStairs();
            for(int z=70;z<=105;z+=6)block(42,30-Math.clamp(z-80,0,29),z,GLASS.get());
            for(int z=113;z<138;z+=4)block(18,-19,z,GLASS.get());
            for(int z=112;z<=114;z++)box(48,1,z,48,14,z,falling);
        }
        void buriedStairs(){
            for(int z=68;z<=109;z++){
                int floor=29-Math.clamp(z-80,0,29);
                passage(39,floor,z,41,floor+5,z);
                if(z>=80&&z<109)box(39,floor,z,41,floor,z,stair(Blocks.DEEPSLATE_BRICK_STAIRS,Direction.NORTH));
            }
            for(int x=16;x<=40;x++){
                int floor=Math.max(-19,Math.min(0,x-39));
                passage(x,floor,109,x,floor+5,111);
                if(x<=39&&x>20)box(x,floor,109,x,floor,111,stair(Blocks.DEEPSLATE_TILE_STAIRS,Direction.EAST));
            }
            // Carry the landing through the lower flight's south turn to the archive
            // doorway. Its trim starts at z137 and already supplies that final floor.
            passage(15,-19,110,17,-14,136);
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
                block(x,60,z,ROSETTE.get());
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
            for(int x=8;x<26;x+=4)block(x,-12,145,LACE.get());
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






