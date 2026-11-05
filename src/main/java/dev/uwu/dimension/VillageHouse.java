package dev.uwu.dimension;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Decorative blocks cropped from four separate houses in japanesevillagev4.schem.
 * Coordinates have already been rotated to put each original entrance on the north wall. */
final class VillageHouse {
    private record Piece(int x,int y,int z,int material) {}
    private final List<Piece> pieces;
    private final BlockState[][] palette;
    private final int turns;
    final int width,depth,radiusX,radiusZ;
    static final List<VillageHouse> STYLES=List.of(load(0),load(1),load(2),load(3));

    private static VillageHouse load(int style){
        String path="/data/uwudimension/village/"+style+".json";
        try(var reader=new InputStreamReader(Objects.requireNonNull(VillageHouse.class.getResourceAsStream(path),path),StandardCharsets.UTF_8)){
            return new VillageHouse(JsonParser.parseReader(reader).getAsJsonObject());
        }catch(IOException e){throw new IllegalStateException("Failed to read village house "+path,e);}
    }
    private VillageHouse(JsonObject data){
        turns=data.get("turns").getAsInt();width=data.get("width").getAsInt();depth=data.get("depth").getAsInt();
        radiusX=data.get("radiusX").getAsInt();radiusZ=data.get("radiusZ").getAsInt();
        var materials=data.getAsJsonArray("palette");palette=new BlockState[2][materials.size()];
        for(int i=0;i<materials.size();i++){
            palette[0][i]=parse(materials.get(i).getAsString(),false);
            palette[1][i]=parse(materials.get(i).getAsString(),true);
        }
        var source=data.getAsJsonArray("blocks");
        var blocks=new ArrayList<Piece>(source.size());
        for(var entry:source){
            var p=entry.getAsJsonArray();
            blocks.add(new Piece(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt(),p.get(3).getAsInt()));
        }
        pieces=List.copyOf(blocks);
    }
    private static BlockState parse(String encoded,boolean dark){
        String[] parts=encoded.split("\\[",2);
        var original=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(parts[0]));
        var block=original;
        if(original==Blocks.WHITE_CONCRETE_POWDER)block=Blocks.WHITE_CONCRETE;
        if(original==Blocks.SPRUCE_LOG)block=dark?Blocks.STRIPPED_DARK_OAK_LOG:Blocks.STRIPPED_CHERRY_LOG;
        if(original==Blocks.SPRUCE_STAIRS)block=PinkRoofTiles.stairs(dark);
        if(original==Blocks.SPRUCE_SLAB)block=PinkRoofTiles.slab(dark);
        if(original==Blocks.RED_TERRACOTTA)block=PinkRoofTiles.tile(dark);
        if(dark&&original==Blocks.LANTERN)block=Blocks.SOUL_LANTERN;
        var state=block.defaultBlockState();
        if(parts.length==2)for(String pair:parts[1].substring(0,parts[1].length()-1).split(",")){
            String[] value=pair.split("=");
            var property=block.getStateDefinition().getProperty(value[0]);
            if(property!=null)state=apply(state,property,value[1]);
        }
        return state;
    }
    private static <T extends Comparable<T>> BlockState apply(BlockState state,Property<T> property,String value){
        return state.setValue(property,property.getValue(value).orElseThrow());
    }
    int roofHeight(int localX,int localZ) {
        return pieces.stream().filter(b->b.x()==localX && b.z()==localZ).mapToInt(Piece::y).max().orElseThrow();
    }
    void place(HeartwaterFeature.Valley v,BlossomVillage.House h,boolean dusk){
        int sign=h.side();
        Rotation rotation=Rotation.values()[(v.turns+turns+(sign<0?2:0))&3];
        for(var b:pieces){
            int x=h.x()+sign*b.x(),z=h.z()+sign*b.z();
            if(!v.inside(x,z))continue;
            var state=palette[dusk?1:0][b.material()];
            // The source's side posts sometimes project beyond its interior floor.
            // Give each such post a stone footing down to this lot's actual ground.
            if(b.y()==1&&(Math.abs(b.x())>width||Math.abs(b.z())>depth)
                    && (state.is(net.minecraft.tags.BlockTags.LOGS)||state.is(Blocks.STONE_BRICKS)))
                BlossomVillage.floor(v,x,h.floor(),z,Blocks.STONE_BRICKS);
            if(state.is(Blocks.PINK_CONCRETE)){
                if(b.x()==0||b.z()==0)state=PinkRoofTiles.tile(dusk).defaultBlockState();
                else if((b.y()+(dusk?1:0))%4==0)state=Blocks.PINK_TERRACOTTA.defaultBlockState();
            }
            v.put(x,h.floor()+b.y(),z,state.rotate(rotation));
        }
    }
}
