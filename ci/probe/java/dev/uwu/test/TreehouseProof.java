package dev.uwu.test;

import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.MoonveilTreehouse;
import dev.uwu.dimension.People;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Surveys native, fresh chunks. No call to the structure's construction or resident factories. */
final class TreehouseProof {
    private static final Path LEDGER=Path.of("moonveil-homes.properties");
    private static final List<MoonveilTreehouse.Plot> plots=new ArrayList<>();
    private static final Set<ChunkPos> tickets=new HashSet<>();
    private static long watching;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError("Moonveil treehouse: "+why);}
    static boolean ready(ServerLevel level,boolean restart){
        if(plots.isEmpty()){
            for(boolean dusk:new boolean[]{false,true}){
                var plot=MoonveilTreehouse.first(level,dusk);
                plots.add(plot);
                var center=new ChunkPos(plot.at(0,0,0));
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                    var c=new ChunkPos(center.x+dx,center.z+dz);
                    if(level.setChunkForced(c.x,c.z,true))tickets.add(c);
                }
                System.out.printf("MOONVEIL DISCOVERY %s dusk=%s base=%s resident0=%s resident1=%s%n",restart?"restart":"first",dusk,plot.at(0,0,0),plot.residentId(0),plot.residentId(1));
            }
            watching=level.getGameTime();
            return false;
        }
        for(var plot:plots)for(int slot=0;slot<2;slot++){
            if(!(level.getEntity(plot.residentId(slot)) instanceof Keeper keeper) || keeper.getPersistentData().getBoolean("MoonveilPendingHousehold")){
                check(level.getGameTime()-watching<380,"did not load native resident "+slot+" at "+plot.at(0,0,0)+"; nearby="+near(level,plot));
                return false;
            }
        }
        return true;
    }
    private static List<Keeper> near(ServerLevel level,MoonveilTreehouse.Plot p){
        return level.getEntitiesOfClass(Keeper.class,new AABB(p.at(0,15,0)).inflate(31,32,31));
    }
    private static String key(ServerLevel level,BlockPos pos){return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();}
    private static void route(ServerLevel level,Keeper probe,BlockPos from,BlockPos goal,int tolerance){
        probe.moveTo(from.getX()+.5,from.getY(),from.getZ()+.5,0,0);
        probe.setOnGround(true);
        var path=probe.getNavigation().createPath(goal,tolerance);
        check(path!=null&&path.canReach(),"native villager route "+from+" -> "+goal+"; final node="+(path==null?"none":path.getEndNode())+" nodes="+(path==null?"[]":java.util.stream.IntStream.range(0,path.getNodeCount()).mapToObj(i->path.getNode(i).asBlockPos().toShortString()).toList()));
    }
    private static void survey(ServerLevel level,MoonveilTreehouse.Plot p,Properties ledger){
        check(p.village().dusk()==p.dusk(),"wrong faction at "+p.door());
        var anchor=p.at(0,0,0);
        boolean dark=p.dusk();
        var idPrefix=dark?"dusk.":"petal.";
        ledger.setProperty(idPrefix+"origin",anchor.toShortString());
        var door=level.getBlockState(p.door());
        check(door.getBlock() instanceof DoorBlock&&door.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER
                &&level.getBlockState(p.door().above()).getBlock()==door.getBlock(),"two-block entrance door at "+p.door());
        var expected=dark?People.DUSK_KEEPER.get():People.PETAL_KEEPER.get();
        var visitor=expected.create(level);
        check(visitor!=null,"cannot create path-navigation probe");
        if(visitor.getNavigation() instanceof GroundPathNavigation nav)nav.setCanOpenDoors(true);
        var inside=p.at(0,1,-5);
        var a=p.bedHead(0);var b=p.bedHead(1);
        check(!a.equals(b)&&!p.job(0).equals(p.job(1)),"two households share a marked bed or job");
        for(int i=0;i<2;i++){
            var id=p.residentId(i);
            check(level.getEntity(id) instanceof Keeper,"lost resident "+id);
            var resident=(Keeper) level.getEntity(id);
            check(resident.getType()==expected&&resident.isAlive()&&resident.isPersistenceRequired(),"wrong faction/persistence "+id);
            ledger.setProperty(idPrefix+i,id.toString());
            var bedHead=p.bedHead(i);var workstation=p.job(i);
            var bed=level.getBlockState(bedHead);
            check(bed.is(BlockTags.BEDS)&&bed.getValue(BedBlock.PART)==BedPart.HEAD,"missing bed head "+p.bedHead(i));
            check(resident.getBrain().getMemory(MemoryModuleType.HOME).filter(g->g.dimension().equals(level.dimension())&&g.pos().equals(bedHead)).isPresent(),"wrong home for "+id);
            check(resident.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g->g.dimension().equals(level.dimension())&&g.pos().equals(workstation)).isPresent(),"wrong job for "+id);
            route(level,visitor,inside,p.bedHead(i),1);
            route(level,visitor,inside,p.job(i),1);
        }
        var assigned=near(level,p).stream().filter(k->k.getPersistentData().contains("MoonveilHouse")
                &&k.getPersistentData().getLong("MoonveilHouse")==anchor.asLong()).map(Keeper::getUUID).toList();
        check(assigned.size()==2&&assigned.containsAll(List.of(p.residentId(0),p.residentId(1))),"duplicate or missing residents "+assigned);
        // Local north is the entrance side, independent of the rotated site's direction.
        var front=p.at(0,1,-9);
        for(int i=0;i<7;i++)for(int x=-2;x<=-1;x++){
            var step=p.at(x,i+1,-5+i);
            check(key(level,step).equals("moonveil_stairs"),"missing stair "+step);
            check(level.getBlockState(step.above()).getCollisionShape(level,step.above()).isEmpty()
                    &&level.getBlockState(step.above(2)).getCollisionShape(level,step.above(2)).isEmpty(),"blocked stair headroom "+step);
        }
        route(level,visitor,front,inside,0);
        Map<String,Integer> count=new HashMap<>();
        for(int dx=-21;dx<=21;dx++)for(int dz=-21;dz<=21;dz++)for(int dy=0;dy<=43;dy++){
            String id=key(level,p.at(dx,dy,dz));if(id.contains("moonveil"))count.merge(id,1,Integer::sum);
        }
        check(count.getOrDefault("moonveil_log",0)>96&&count.getOrDefault("moonveil_leaves",0)>240,"sparse hollow tree "+count);
        for(String cut:List.of("planks","stairs","table","stool","bookshelf"))
            check(count.getOrDefault("moonveil_"+cut,0)>0,"missing furnished "+cut+" at "+anchor);
        int max=MoonveilTreehouse.FOOTPRINT+1;
        for(int i=-max;i<=max;i++)for(int y=1;y<=42;y++)for(var boundary:List.of(p.at(i,y,max),p.at(i,y,-max),p.at(max,y,i),p.at(-max,y,i))){
            check(!key(level,boundary).contains("moonveil"),"geometry escaped its declared footprint at "+boundary);
        }
        check(!MoonveilTreehouse.suitable(level,p.village(),new BlockPos(anchor.getX(),level.getSeaLevel(),anchor.getZ())),"accepted a flooded root base");
        var firstHouse=p.village().houses().getFirst();
        check(!MoonveilTreehouse.suitable(level,p.village(),p.village().doorway(firstHouse).below()),"would overwrite an occupied building");
        check(!MoonveilTreehouse.suitable(level,p.village(),p.village().plaza()),"would overwrite village plaza");
        check(!MoonveilTreehouse.suitable(level,p.village(),anchor.above(40)),"accepted an unsupported elevated site");
        check(!MoonveilTreehouse.suitable(level.getServer().overworld(),p.village(),anchor),"accepted the Overworld");
        System.out.println("MOONVEIL HOUSE SURVEY faction="+idPrefix+" anchor="+anchor+" biome="+level.getBiome(anchor).unwrapKey()+" blocks="+count+" home="+assigned);
    }
    private static void rejectedTerrain(ServerLevel level,MoonveilTreehouse.Plot plot){
        var chunkSource=level.getChunkSource();var generator=chunkSource.getGenerator();
        var land=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("uwudimension","sakura_settlement_land"));
        BlockPos water=null,other=null;
        var origin=plot.at(0,0,0);
        for(int ring=1;ring<=14&&(water==null||other==null);ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
            int x=origin.getX()+dx*128,z=origin.getZ()+dz*128;
            int y=generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,chunkSource.randomState())-1;
            var pos=new BlockPos(x,y,z);
            if(y<level.getSeaLevel()-1&&water==null)water=pos;
            if(y>level.getSeaLevel()+2&&other==null){
                var biome=generator.getBiomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x),net.minecraft.core.QuartPos.fromBlock(y+1),
                        net.minecraft.core.QuartPos.fromBlock(z),chunkSource.randomState().sampler());
                if(!biome.is(net.minecraft.world.level.biome.Biomes.CHERRY_GROVE)&&!biome.is(land))other=pos;
            }
        }
        check(water!=null&&!MoonveilTreehouse.suitable(level,plot.village(),water),"native underwater basin accepted, sample="+water);
        // A dimension preset can map every dry column to eligible Sakura-family land. Only claim a
        // dry incompatible biome was probed if one was actually available on this bounded survey.
        if(other!=null)check(!MoonveilTreehouse.suitable(level,plot.village(),other),"non-Sakura biome accepted at "+other);
        System.out.println("MOONVEIL SITE REJECTION real basin="+water+" dry incompatible biome="+other);
    }
    static void run(ServerLevel level,boolean restart){
        try{
            Properties actual=new Properties();
            if(!restart)MoonveilMaterialsProof.run(level);
            for(var p:plots)survey(level,p,actual);
            if(restart){
                var original=new Properties();
                try(var in=Files.newInputStream(LEDGER)){original.load(in);}
                check(actual.equals(original),"plot or assigned resident IDs changed after disk restart: "+actual+" vs "+original);
                System.out.println("MOONVEIL TREEHOUSE RESTART PASS two saved factions, four original residents and their individual beds and jobs");
            }else{
                rejectedTerrain(level,plots.getFirst());
                try(var out=Files.newOutputStream(LEDGER)){actual.store(out,"Native world-generated treehouses and assigned residents");}
                System.out.println("MOONVEIL TREEHOUSES PASS two inhabited giant trees with native wood, distinct beds/jobs, functional door and native villager paths");
            }
        }catch(IOException problem){throw new UncheckedIOException(problem);}
        finally{for(var chunk:tickets)level.setChunkForced(chunk.x,chunk.z,false);tickets.clear();}
    }
}
