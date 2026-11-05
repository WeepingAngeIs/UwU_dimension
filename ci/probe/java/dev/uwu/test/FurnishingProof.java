package dev.uwu.test;

import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.GroveSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

/** Independently inspect normal world generation, without placing any test blocks. */
final class FurnishingProof {
    static void verify(ServerLevel level) {
        int homes = 0;
        for (boolean dusk : new boolean[]{false,true}) {
            var village = BlossomVillage.first(level, dusk);
            for (var house : village.houses()) {
                String wood = dusk ? "dark_oak" : "cherry";
                furniture(level, inside(village,house,1-house.width(),1,-1), wood+"_table");
                furniture(level, inside(village,house,1-house.width(),1,0), wood+"_chair");
                furniture(level, inside(village,house,house.width()-1,1,house.depth()-1), wood+"_drawer");
                furniture(level, inside(village,house,house.width()-1,2,house.depth()-1), dusk?"purple_lamp":"pink_lamp");
                var colors = palette(dusk);
                for (int lane=-1; lane<=1; lane++) {
                    for (int dy=2; dy<=3; dy++) {
                        var window=inside(village,house,lane,dy,house.depth());
                        String id=BuiltInRegistries.BLOCK.getKey(level.getBlockState(window).getBlock()).toString();
                        String expected="minecraft:"+colors[lane+1]+"_stained_glass";
                        if (!id.equals(expected) && !id.equals(expected+"_pane"))
                            throw new AssertionError("Expected "+expected+" window at "+window+"; found "+id);
                    }
                    skylight(level,village,house,lane);
                }
                homes++;
            }
        }
        var valley=GroveSites.first(level);
        furniture(level,valley.pos(-9,53,91),"cherry_bench");
        furniture(level,valley.pos(-9,53,97),"cherry_bench");
        furniture(level,valley.pos(-9,53,94),"cherry_table");
        furniture(level,valley.pos(-9,54,94),"pink_lamp");
        furniture(level,valley.pos(11,53,100),"bamboo_drawer");
        furniture(level,valley.pos(23,-18,143),"dark_oak_table");
        furniture(level,valley.pos(23,-17,143),"white_lamp");
        furniture(level,valley.pos(24,-18,143),"dark_oak_chair");
        furniture(level,valley.pos(8,-18,141),"dark_oak_drawer");
        System.out.println("FURNISHED WORLD PASS | "+homes+" generated homes in both factions, real Another Furniture tables, chairs, drawers and lamps, stained windows and clear skylights, furnished bathhouse and archive");
    }

    static BlockPos inside(BlossomVillage.Plan v, BlossomVillage.House h, int dx, int dy, int inward) {
        return v.site().pos(h.x()+dx,h.floor()+dy,h.z()+inward*h.side());
    }

    static String[] palette(boolean dusk) {
        return dusk ? new String[]{"magenta","white","purple"} : new String[]{"pink","white","light_blue"};
    }

    static BlockPos skylight(ServerLevel level, BlossomVillage.Plan v, BlossomVillage.House h, int lane) {
        for (int dy=3; dy<=20; dy++) {
            var pos=inside(v,h,-2,dy,lane);
            var state=level.getBlockState(pos);
            if (dy>=5 && BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(
                    "minecraft:"+palette(v.dusk())[lane+1]+"_stained_glass")) return pos;
            if (!state.isAir()) throw new AssertionError("Obstructed skylight over "+v.doorway(h)+" at "+pos+" "+state);
        }
        throw new AssertionError("Missing glass in roof at "+v.doorway(h));
    }

    static void furniture(ServerLevel level, BlockPos pos, String expected) {
        String id=BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        if (expected.endsWith("_drawer") && level.getBlockEntity(pos)==null) throw new AssertionError("Missing drawer inventory at "+pos);
        if (!id.equals("another_furniture:"+expected)) throw new AssertionError("Expected Another Furniture "+expected+" at "+pos+", got "+id);
    }
}
