package dev.uwu.dimension;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Anya's saved-world tree, with only explicitly documented bark substitutions. */
public final class GiantSakuraTree {
    private GiantSakuraTree() {}
    private static final Map<BlockPos,BlockState> EDITED=load();
    /** Legacy parameters remain source-compatible; the authored shape is fixed. */
    public static Map<BlockPos,BlockState> plan(int height,long seed) { return EDITED; }
    private static Map<BlockPos,BlockState> load() {
        var result=new LinkedHashMap<BlockPos,BlockState>();
        try(var stream=GiantSakuraTree.class.getResourceAsStream("/assets/uwudimension/giant_sakura_exact.json")) {
            if(stream==null)throw new IllegalStateException("Missing authored giant sakura");
            var entries=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray();
            if(entries.size()!=5026)throw new IllegalStateException("Authored tree block count changed");
            for(var value:entries){
                var entry=value.getAsJsonObject();var pos=entry.getAsJsonArray("pos");var spec=entry.getAsJsonObject("state");
                var id=ResourceLocation.parse(spec.get("Name").getAsString());
                var block=BuiltInRegistries.BLOCK.getOptional(id).orElseThrow();
                var state=block.defaultBlockState();
                for(var property:spec.getAsJsonObject("Properties").entrySet()){
                    var key=block.getStateDefinition().getProperty(property.getKey());
                    if(key==null)throw new IllegalStateException("Unknown tree property "+property.getKey());
                    state=apply(state,key,property.getValue().getAsString());
                }
                if(result.put(new BlockPos(pos.get(0).getAsInt(),pos.get(1).getAsInt(),pos.get(2).getAsInt()),state)!=null)
                    throw new IllegalStateException("Duplicate authored tree coordinate");
            }
            return Collections.unmodifiableMap(result);
        } catch(java.io.IOException ex){throw new IllegalStateException("Cannot load authored tree",ex);}
    }
    private static <T extends Comparable<T>> BlockState apply(BlockState state,Property<T> property,String value){
        return state.setValue(property,property.getValue(value).orElseThrow());
    }
}
