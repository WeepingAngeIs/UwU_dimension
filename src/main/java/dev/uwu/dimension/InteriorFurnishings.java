package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/** Seating, storage, reading lights and glazed openings for settlements and public rooms. */
final class InteriorFurnishings {
    private static final Block[] PETAL_GLASS = {Blocks.PINK_STAINED_GLASS, Blocks.WHITE_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS};
    private static final Block[] DUSK_GLASS = {Blocks.MAGENTA_STAINED_GLASS, Blocks.WHITE_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS};

    static void house(HeartwaterFeature.Valley v, BlossomVillage.House h, boolean dusk, VillageHouse shell) {
        int x=h.x(), y=h.floor(), z=h.z(), side=h.side();
        int tea=x-h.width()+1, cabinet=x+h.width()-1, back=z+side*(h.depth()-1);
        Direction facingDoor=side>0?Direction.NORTH:Direction.SOUTH;
        String wood=dusk?"dark_oak":"cherry";
        place(v,tea,y+1,z-side,wood+"_table",facingDoor);
        place(v,tea,y+1,z,wood+"_chair",facingDoor);
        place(v,cabinet,y+1,back,wood+"_drawer",Direction.WEST);
        place(v,cabinet,y+2,back,dusk?"purple_lamp":"pink_lamp",Direction.UP);

        Block[] glass=dusk?DUSK_GLASS:PETAL_GLASS;
        for(int offset=-1;offset<=1;offset++) {
            int pane=x+offset;
            int wall=z+side*h.depth();
            // Remove recessed fittings behind the glass and projecting panels outside it.
            for (int d=h.depth()-1;d<=shell.radiusZ;d++) {
                if(d!=h.depth()) v.box(pane,y+2,z+side*d,pane,y+3,z+side*d,v.air);
            }
            v.box(pane,y+2,wall,pane,y+3,wall,glass[offset+1]);
            int roofZ=z+side*offset;
            int roofY=y+shell.roofHeight(-2*side,offset);
            v.box(x-2,y+3,roofZ,x-2,roofY-1,roofZ,v.air);
            v.block(x-2,roofY,roofZ,glass[offset+1]);
        }
    }

    static void bathhouse(HeartwaterFeature.Valley v) {
        place(v,-9,53,91,"cherry_bench",Direction.EAST);
        place(v,-9,53,97,"cherry_bench",Direction.EAST);
        place(v,-9,53,94,"cherry_table",Direction.EAST);
        place(v,-9,54,94,"pink_lamp",Direction.UP);
        place(v,11,53,100,"bamboo_drawer",Direction.WEST);
    }

    static void archive(HeartwaterFeature.Valley v) {
        place(v,23,-18,143,"dark_oak_table",Direction.WEST);
        place(v,23,-17,143,"white_lamp",Direction.UP);
        place(v,24,-18,143,"dark_oak_chair",Direction.WEST);
        place(v,8,-18,141,"dark_oak_drawer",Direction.SOUTH);
    }

    private static void place(HeartwaterFeature.Valley v, int x, int y, int z, String id, Direction facing) {
        if(!v.inside(x,z)) return;
        var key=ResourceLocation.fromNamespaceAndPath("another_furniture",id);
        var block=BuiltInRegistries.BLOCK.getOptional(key).orElseThrow(()->new IllegalStateException("Missing required furniture "+key));
        BlockState state=block.defaultBlockState();
        var direction=facing.getAxis().isHorizontal()?v.face(facing):facing;
        var property=block.getStateDefinition().getProperty("facing");
        if(property!=null) state=set(state,property,direction.getSerializedName());
        if(state.hasProperty(BlockStateProperties.LIT)) state=state.setValue(BlockStateProperties.LIT,true);
        // Use the worldgen API so functional drawers and other block entities are saved.
        v.level.setBlock(new BlockPos(v.wx(x,z),y,v.wz(x,z)),state,Block.UPDATE_CLIENTS);
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String serialized) {
        return state.setValue(property,property.getValue(serialized).orElseThrow());
    }
}
