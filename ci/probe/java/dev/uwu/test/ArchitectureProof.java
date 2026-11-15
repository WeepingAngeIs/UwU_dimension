package dev.uwu.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.SettlementLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import noobanidus.mods.lootr.common.block.entity.LootrChestBlockEntity;

/** Samples the generated fabric itself. Never generates a structure through a test-only builder. */
final class ArchitectureProof {
    private static final String NS="uwudimension:";
    private static final Set<String> IMPORTED=Set.of("spruce_planks","spruce_stairs","spruce_slab","spruce_trapdoor",
            "oak_planks","oak_stairs","oak_fence","oak_slab","oak_trapdoor","stone_bricks","smooth_stone",
            "pink_concrete","white_concrete","white_concrete_powder","red_terracotta","cobblestone","chain");
    private static String id(ServerLevel level, BlockPos p) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString();
    }
    private static void require(boolean pass,String detail){if(!pass)throw new AssertionError(detail);}
    private static void exact(ServerLevel level,BlockPos p,String expected) {
        String got=id(level,p);
        require(got.equals(expected),"Expected "+expected+" at "+p+"; found "+got);
    }

    static void legacy(ServerLevel level) {
        if(!Files.exists(Path.of("baseline-world")))return;
        level.getChunk(20,20);
        exact(level,new BlockPos(321,185,320),"minecraft:spruce_planks");
        exact(level,new BlockPos(322,185,320),"minecraft:oak_fence");
        var step=level.getBlockState(new BlockPos(323,185,320));
        require(step.is(Blocks.OAK_STAIRS) && step.getValue(StairBlock.FACING)==net.minecraft.core.Direction.WEST,
                "The earlier release's stair lost its ID or facing on upgrade");
        exact(level,new BlockPos(324,185,320),"minecraft:cobblestone");
        var baseline=Path.of("old-native-dusk.txt");
        if(Files.exists(baseline)){
            try{
                var lines=Files.readAllLines(baseline);
                var cell=lines.getFirst().split(" ");
                var site=GroveSites.site(level,Integer.parseInt(cell[0]),Integer.parseInt(cell[1]));
                var prior=BlossomVillage.plan(level,site,true).orElseThrow();
                require(prior.plaza().asLong()==Long.parseLong(cell[2]),"A real old Dusk plaza moved");
                for(String line:lines.subList(1,lines.size())){
                    var sample=line.split(" ",2);
                    var pos=BlockPos.of(Long.parseLong(sample[0]));
                    require(level.getBlockState(pos).toString().equals(sample[1]),"Changed old generated block at "+pos+": "+sample[1]+" -> "+level.getBlockState(pos));
                }
                System.out.println("ARCHITECTURE NATIVE UPGRADE PASS | original Dusk district, oriented blocks, jobs and bell "+prior.plaza());
            }catch(java.io.IOException error){throw new IllegalStateException(error);}
        }
        System.out.println("ARCHITECTURE LEGACY PASS | previous-version spruce, oak rail, oriented steps and cobble retained in saved chunk");
    }

    static void run(ServerLevel level) {
        for(String timber:new String[]{"silkwood","velvetwood"}) for(String cut:new String[]{"planks","beam","door","shutter","slab","stairs","fence"}){
            String item=timber+"_"+cut;
            var holder=level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(NS+"architecture/"+item)).orElseThrow();
            var output=holder.value().getResultItem(level.registryAccess()).getItem();
            require(BuiltInRegistries.ITEM.getKey(output).toString().equals(NS+item),"Wrong result for "+item);
        }
        for(boolean dusk:new boolean[]{false,true}){
            var village=BlossomVillage.first(level,dusk);
            String prefix=dusk?"velvetwood":"silkwood";
            Map<String,Integer> counts=new HashMap<>();
            for(var house:village.houses()){
                for(int dx=-house.width()-1;dx<=house.width()+1;dx++)
                    for(int dz=-house.depth()-1;dz<=house.depth()+1;dz++)
                        for(int dy=1;dy<=16;dy++){
                            BlockPos p=village.site().pos(house.x()+dx,house.floor()+dy,house.z()+dz);
                            String material=id(level,p);
                            if(material.startsWith("minecraft:") && IMPORTED.contains(material.substring(10)))
                                throw new AssertionError("Original schematic material "+material+" in new house "+house.style()+" at "+p);
                            counts.merge(material,1,Integer::sum);
                        }
                exact(level,village.site().pos(house.x(),house.floor(),house.z()),NS+(dusk?"black_heartsill":"heartsill"));
                BlockPos door=village.doorway(house);
                exact(level,door,NS+prefix+"_door");
                require(level.getBlockState(door).is(BlockTags.WOODEN_DOORS) && level.getBlockState(door.above()).getBlock()==level.getBlockState(door).getBlock()
                        && level.getBlockState(door.above()).getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER,
                        "Keeper door is not a usable wood door at "+door);
                require(level.getBlockState(village.bedHead(house)).is(BlockTags.BEDS) && level.getBlockState(village.workstation(house)).is(house.job()),
                        "Lost bed/job in "+village.doorway(house));
            }
            for(String member:new String[]{"_beam","_fence","_slab","_stairs","_planks","_shutter"})
                require(counts.getOrDefault(NS+prefix+member,0)>0,"No "+prefix+member+" in surveyed "+village.plaza());
            for(String accent:dusk?new String[]{"silver_chain","dusk_heart_lantern","nightbriar_shingle_stairs","nightbriar_shingle_slab","starvein_frieze","opal_leaded_glass"}:new String[]{"silver_chain","heart_neon_lantern","petalsilk_plaster","camellia_glaze","opal_leaded_glass"})
                require(counts.getOrDefault(NS+accent,0)>0,"Missing "+accent+" at "+village.plaza());
            exact(level,village.site().pos(village.x()+2,village.y()+1,village.z()-2),"minecraft:bell");
            for(int i=4;i<village.road().length-4;i+=4){
                int dx=i-village.road().length/2;
                if(Math.abs(dx)<4)continue;
                var p=village.site().pos(village.x()+dx,village.road()[i],village.z()+village.bends()[i]);
                require(id(level,p).startsWith(NS),"Vanilla road at "+p);
            }
        }
        var petal=BlossomVillage.first(level,false);
        var dusk=BlossomVillage.first(level,true);
        require(!petal.site().equals(dusk.site()),"Opposite native factions share a site");
        int min=SettlementLayout.minimum(level);
        for(var a:petal.houses())for(var b:dusk.houses()){
            var pa=petal.site().pos(a.x(),0,a.z());
            var pb=dusk.site().pos(b.x(),0,b.z());
            // Circumscribed radii put the complete house footprints inside the check.
            require(Math.sqrt(pa.distSqr(pb))-22>=min,"Generated districts closer than "+min+" at "+pa+" / "+pb);
        }
        require(BlossomVillage.plan(level,petal.site(),true).isEmpty() && BlossomVillage.plan(level,dusk.site(),false).isEmpty(),
                "Opposite native population assigned to an already-claimed district");
        var s=GroveSites.first(level);
        int lip=s.lip();
        for(int z=lip+1;z<52;z++){
            int floor=s.entryY()-Math.clamp(z-(lip+4),0,s.entryY()-52);
            for(int x=-3;x<=3;x++){
                if(Math.abs(x)==3)for(int y=0;y<=5;y++)require(id(level,s.pos(x,floor+y,z)).startsWith(NS),"Exposed entrance side at "+s.pos(x,floor+y,z));
                require(id(level,s.pos(x,floor+5,z)).startsWith(NS),"Unlined tunnel roof at "+s.pos(x,floor+5,z));
            }
        }
        // Side faces, their projecting flutes, and the negative space of the adjacent bay.
        for(int[] room:new int[][]{{-14,51,82},{5,-20,139},{-17,-20,139}}){
            int x=room[0]+1,y=room[1]+4,z=room[2]+7;
            exact(level,s.pos(x+1,y,z),NS+"fluted_nacre");
            require(id(level,s.pos(x,y,z+2)).startsWith(NS),"Bare recessed chamber wall at "+s.pos(x,y,z+2));
            // Individual benches and bookcases can stand against some bays. A clear upper pocket remains.
            require(level.getBlockState(s.pos(x+1,y+2,z+2)).isAir(),"Expected depth between ribs at "+s.pos(x+1,y+2,z+2));
        }
        for(Object[] chest:new Object[][]{{0,53,103,"rose_bathhouse"},{23,-18,147,"tide_archive"},
                {55,9,120,"inverted_shrine"},{-7,-18,159,"mirror_court"}}){
            var p=s.pos((int)chest[0],(int)chest[1],(int)chest[2]);
            require(level.getBlockEntity(p) instanceof LootrChestBlockEntity box && box.getInfoLootTable()!=null
                    && box.getInfoLootTable().location().toString().equals(NS+"chests/"+chest[3]),"Lost loot at "+p);
        }
        exact(level,s.pos(-7,-17,146),NS+"mirror_court_bell");
        exact(level,s.pos(49,6,114),NS+"moonpearl_lamp");
        System.out.println("REACH ARCHITECTURE PASS | two settlement joineries, working beds/doors/jobs, roof and both passage walls, three vaulted interiors, four Lootr rewards, shrine cladding and Mirror Court bell");
    }
}
