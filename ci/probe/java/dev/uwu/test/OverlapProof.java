package dev.uwu.test;

import dev.uwu.dimension.*;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** Disposable standard-start fixtures. No test classes ship in the main JAR. */
public final class OverlapProof {
    private static final DeferredRegister<StructurePieceType> TYPES=DeferredRegister.create(Registries.STRUCTURE_PIECE,"heartwater_probe");
    private static final DeferredHolder<StructurePieceType,StructurePieceType> MARKER=TYPES.register("marker",()->Marker::new);
    public static void register(IEventBus bus){TYPES.register(bus);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError("OVERLAP "+message);}
    public static final class Marker extends StructurePiece {
        Marker(BoundingBox box){super(MARKER.get(),0,box);}
        Marker(StructurePieceSerializationContext ctx,CompoundTag tag){super(MARKER.get(),tag);}
        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext ctx,CompoundTag tag){}
        @Override public void postProcess(WorldGenLevel level,StructureManager manager,ChunkGenerator generator,
                RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos pivot){
            for(int x=boundingBox.minX();x<=boundingBox.maxX();x++)for(int z=boundingBox.minZ();z<=boundingBox.maxZ();z++)
                for(int y=boundingBox.minY();y<=boundingBox.maxY();y++)if(clip.isInside(x,y,z))level.setBlock(new BlockPos(x,y,z),Blocks.GOLD_BLOCK.defaultBlockState(),2);
        }
    }
    private static Structure foreign(ServerLevel level){return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("minecraft:swamp_hut"));}
    private static StructureStart start(ServerLevel level,ChunkPos origin,BoundingBox box){
        return new StructureStart(foreign(level),origin,0,new PiecesContainer(List.of(new Marker(box))));
    }
    private static String fingerprint(List<AuthoredFootprints.Envelope> parts){
        return parts.stream().map(p->p.part()+":"+p.box().minX()+","+p.box().minY()+","+p.box().minZ()+","+p.box().maxX()+","+p.box().maxY()+","+p.box().maxZ()).sorted().reduce("",(a,b)->a+b+"\n");
    }
    private static BoundingBox point(BlockPos p){return new BoundingBox(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ());}
    private static Object construct(Class<?> type,Class<?>[] types,Object... args){
        try{var constructor=type.getDeclaredConstructor(types);constructor.setAccessible(true);return constructor.newInstance(args);}
        catch(ReflectiveOperationException e){throw new AssertionError("fixture constructor",e);}
    }
    private static Object invoke(Class<?> type,Object instance,String name,Class<?>[] types,Object... args){
        try{var method=type.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(instance,args);}
        catch(ReflectiveOperationException e){throw new AssertionError("fixture method "+name,e);}
    }
    @SuppressWarnings("unchecked") private static void rotations(ServerLevel level,BlossomVillage.Plan original)throws Exception{
        var surveyType=Class.forName("dev.uwu.dimension.AuthoredFootprints$Survey");
        var valleyType=Class.forName("dev.uwu.dimension.HeartwaterFeature$Valley");
        var result=surveyType.getDeclaredField("result");result.setAccessible(true);
        for(int turn=0;turn<4;turn++){
            var s=original.site();var site=new GroveSites.Site(s.x(),s.z(),turn,s.entryY(),s.gx(),s.gz(),true,true);
            var sink=construct(surveyType,new Class<?>[]{ServerLevel.class,GroveSites.Site.class},level,site);
            invoke(surveyType,sink,"part",new Class<?>[]{String.class,Runnable.class},"rotation",(Runnable)()->invoke(surveyType,sink,"box",new Class<?>[]{int.class,int.class,int.class,int.class,int.class,int.class},-7,-19,12,3,70,25));
            var parts=(List<AuthoredFootprints.Envelope>)result.get(sink);var b=parts.getFirst().box();
            for(int x:new int[]{-7,3})for(int z:new int[]{12,25})check(b.isInside(site.pos(x,0,z)),"rotation misses corner "+turn);
            var v=construct(valleyType,new Class<?>[]{ServerLevel.class,GroveSites.Site.class,surveyType},level,site,sink);
            var p=new BlossomVillage.Plan(site,original.x(),original.z(),original.y(),original.road(),original.bends(),original.houses(),original.dusk());
            invoke(surveyType,sink,"part",new Class<?>[]{String.class,Runnable.class},"houses_roads_gates",(Runnable)()->invoke(BlossomVillage.class,null,"place",new Class<?>[]{valleyType,BlossomVillage.Plan.class},v,p));
            invoke(surveyType,sink,"part",new Class<?>[]{String.class,Runnable.class},"moonveil_geometry",(Runnable)()->invoke(MoonveilTreehouse.class,null,"place",new Class<?>[]{valleyType,MoonveilTreehouse.Plot.class},v,new MoonveilTreehouse.Plot(p,0,80,-170,level.getSeed())));
            check(parts.size()==3,"synthetic rotated builder envelopes missing");
            check(parts.get(2).box().getYSpan()>30,"Moonveil crown omitted");
        }
        System.out.println("OVERLAP ROTATIONS PASS four rotated real village and Moonveil builders, full corners and crown");
    }
    public static void run(ServerLevel level,boolean restart)throws Exception{
        var first=GroveSites.first(level);check(first.usable(),"no usable entry site");
        int loaded=level.getChunkSource().getLoadedChunksCount();
        var parts=AuthoredFootprints.cell(level,first.gx(),first.gz());check(!parts.isEmpty(),"no authored reservations");
        String signature=fingerprint(parts);var file=Path.of("overlap-checkpoint.json");
        if(restart){
            var data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            check(signature.equals(data.get("footprints").getAsString()),"footprints changed after restart/partial generation");
            var box=new BoundingBox(data.get("ax").getAsInt(),data.get("ay").getAsInt(),data.get("az").getAsInt(),data.get("ax").getAsInt()+1,data.get("ay").getAsInt()+1,data.get("az").getAsInt()+1);
            check(AuthoredFootprints.check(level,box)==AuthoredFootprints.Decision.CLEAR,"accepted space changed after restart");
            var origin=new ChunkPos(data.get("cx").getAsInt(),data.get("cz").getAsInt());var chunk=level.getChunk(origin.x,origin.z);
            var saved=level.structureManager().getStartForStructure(SectionPos.bottomOf(chunk),foreign(level),chunk);
            check(saved!=null&&saved.isValid(),"accepted start lost on disk");
            check(level.getBlockState(new BlockPos(box.minX(),box.minY(),box.minZ())).is(Blocks.GOLD_BLOCK),"first accepted piece missing after restart");
            saved.placeInChunk(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(7),box,origin);
            check(fingerprint(AuthoredFootprints.cell(level,first.gx(),first.gz())).equals(signature),"partial completion changed reservations");
            System.out.println("OVERLAP RESTART PASS same plans and accepted start; partial placement retained/completed without rewriting authored state");return;
        }
        check(loaded==level.getChunkSource().getLoadedChunksCount(),"planning loaded neighboring chunks");
        var names=new HashSet<String>();for(var e:parts){
            names.add(e.part());var b=e.box();
            check(AuthoredFootprints.check(level,new BoundingBox(b.minX(),b.minY(),b.minZ(),b.minX(),b.minY(),b.minZ()))==AuthoredFootprints.Decision.OVERLAP,"missed envelope boundary "+e.part());
            check(AuthoredFootprints.check(level,new BoundingBox(b.maxX(),b.maxY(),b.maxZ(),b.maxX(),b.maxY(),b.maxZ()))==AuthoredFootprints.Decision.OVERLAP,"missed far boundary "+e.part());
        }
        for(String name:List.of("petal_village","spring_and_bank","pools_and_boardwalk","deep_pools_and_connections","bathhouse","archive","mirror_court","entrance_and_descent"))check(names.contains(name),"missing authored part "+name);
        var village=BlossomVillage.first(level,false);rotations(level,village);
        var box=parts.stream().filter(p->p.part().equals("petal_village")).findFirst().orElseThrow().box();
        var hit=point(village.plaza());var origin=new ChunkPos((hit.minX()>>4)+30,(hit.minZ()>>4)+30);
        var chunk=level.getChunk(origin.x,origin.z);var section=SectionPos.bottomOf(chunk);var manager=level.structureManager();
        manager.setStartForStructure(section,foreign(level),start(level,origin,hit),chunk);
        check(manager.getStartForStructure(section,foreign(level),chunk)==StructureStart.INVALID_START,"neighbor-origin start not rejected atomically");
        var wide=new BoundingBox(hit.minX()-1500,hit.minY(),hit.minZ()-100,hit.maxX()+1500,hit.maxY()+1,hit.maxZ()+100);
        check(AuthoredFootprints.check(level,wide)==AuthoredFootprints.Decision.OVERLAP,"large start footprint missed");
        check(AuthoredFootprints.check(level,new BoundingBox(-10_000_000,0,-10_000_000,10_000_000,10,10_000_000))==AuthoredFootprints.Decision.BUDGET,"oversize query not bounded");
        var untouched=level.getServer().overworld();var outside=untouched.getChunk(0,0);
        var nativeStart=start(untouched,new ChunkPos(0,0),hit);untouched.structureManager().setStartForStructure(SectionPos.bottomOf(outside),foreign(untouched),nativeStart,outside);
        check(untouched.structureManager().getStartForStructure(SectionPos.bottomOf(outside),foreign(untouched),outside)==nativeStart,"other dimension altered");
        var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);var vanillaVillage=registry.get(ResourceLocation.parse("minecraft:village_plains"));
        manager.setStartForStructure(section,vanillaVillage,new StructureStart(vanillaVillage,origin,0,new PiecesContainer(List.of(new Marker(hit)))),chunk);
        check(manager.getStartForStructure(section,vanillaVillage,chunk)==null,"existing replaced-village tag policy lost");
        BlockPos open=null;
        for(int r=300;r<=1200&&open==null;r+=100)for(int sign:new int[]{-1,1}){
            var candidate=new BlockPos(first.x()+r*sign,160,first.z()+r);
            if(AuthoredFootprints.check(level,point(candidate))==AuthoredFootprints.Decision.CLEAR){open=candidate;break;}
        }
        check(open!=null,"no non-overlapping foreign fixture space");
        var acceptedBox=new BoundingBox(open.getX(),open.getY(),open.getZ(),open.getX()+1,open.getY()+1,open.getZ()+1);
        var acceptedOrigin=new ChunkPos(open);var acceptedChunk=level.getChunk(acceptedOrigin.x,acceptedOrigin.z);
        var accepted=start(level,acceptedOrigin,acceptedBox);manager.setStartForStructure(SectionPos.bottomOf(acceptedChunk),foreign(level),accepted,acceptedChunk);
        check(manager.getStartForStructure(SectionPos.bottomOf(acceptedChunk),foreign(level),acceptedChunk)==accepted,"non-overlapping foreign start lost");
        var firstSlice=new BoundingBox(open.getX(),open.getY(),open.getZ(),open.getX(),open.getY()+1,open.getZ()+1);
        accepted.placeInChunk(level,manager,level.getChunkSource().getGenerator(),RandomSource.create(1),firstSlice,acceptedOrigin);
        check(level.getBlockState(open).is(Blocks.GOLD_BLOCK),"accepted foreign piece failed to place");
        var plaza=village.plaza();level.getChunkAt(plaza);level.getChunkAt(plaza.offset(16,0,16));
        var bell=village.site().pos(village.x()+2,village.y()+1,village.z()-2);level.getChunkAt(bell);
        check(level.getBlockState(bell).is(Blocks.BELL),"authored village placement was lost");
        AuthoredFootprints.clear(level);check(fingerprint(AuthoredFootprints.cell(level,first.gx(),first.gz())).equals(signature),"generation order/world blocks changed reservations");
        check(AuthoredFootprints.check(level,hit)==AuthoredFootprints.Decision.OVERLAP,"reverse generation order accepted overlap");
        // An arbitrary feature/direct writer bypasses StructureManager. Demonstrate and label that limit.
        var direct=bell.above(25);var before=level.getBlockState(direct);level.setBlockAndUpdate(direct,Blocks.EMERALD_BLOCK.defaultBlockState());
        check(level.getBlockState(direct).is(Blocks.EMERALD_BLOCK),"direct-write scope fixture did not run");level.setBlockAndUpdate(direct,before);
        var data=new JsonObject();data.addProperty("seed",level.getSeed());data.addProperty("footprints",signature);data.addProperty("ax",open.getX());data.addProperty("ay",open.getY());data.addProperty("az",open.getZ());data.addProperty("cx",acceptedOrigin.x);data.addProperty("cz",acceptedOrigin.z);
        Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(data));
        System.out.println("OVERLAP SERVER PASS seed="+level.getSeed()+" parts="+names+"; full/neighbor/large starts rejected; clear starts allowed; dimension/tag policy preserved; no planning chunk loads; authored bell retained");
        System.out.println("OVERLAP DIRECT-WRITE LIMIT CONFIRMED arbitrary feature/direct writes bypass standard start protection");
    }
}
