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
    private record TeaSet(Block table, Block chair) {
        static TeaSet select(boolean nativeRoom, NativeTeaFurniture.Family family, String oldWood) {
            return nativeRoom ? new TeaSet(family.table(),family.chair())
                    : new TeaSet(external(oldWood+"_table"),external(oldWood+"_chair"));
        }
    }

    static void house(HeartwaterFeature.Valley v, BlossomVillage.House h, boolean dusk, VillageHouse shell,boolean gothic) {
        int x=h.x(), y=h.floor(), z=h.z(), side=h.side();
        int tea=x-h.width()+1, cabinet=x+h.width()-1, back=z+side*(h.depth()-1);
        Direction facingDoor=side>0?Direction.NORTH:Direction.SOUTH;
        String wood=dusk?"dark_oak":"cherry";
        boolean nativeRoom=SettlementLayout.useNativeTea(v.level.getLevel(),v.site,
                x-shell.radiusX,z-shell.radiusZ,x+shell.radiusX,z+shell.radiusZ);
        TeaSet set=TeaSet.select(nativeRoom,dusk?NativeTeaFurniture.VELVETWOOD:NativeTeaFurniture.SILKWOOD,wood);
        place(v,tea,y+1,z-side,set.table(),facingDoor);
        place(v,tea,y+1,z,set.chair(),facingDoor);
        boolean newPetalRoom=!dusk && SettlementLayout.useSilkwoodDrawer(v.level.getLevel(),v.site,
                x-shell.radiusX,z-shell.radiusZ,x+shell.radiusX,z+shell.radiusZ);
        Block drawer=newPetalRoom?NativeDrawer.DRAWER.get():external(wood+"_drawer");
        place(v,cabinet,y+1,back,drawer,Direction.WEST);
        if(dusk) v.block(cabinet,y+2,back,HeartLanterns.DUSK.get());
        else place(v,cabinet,y+2,back,"pink_lamp",Direction.UP);

        furnishAlcove(v,h,shell,dusk,wood,nativeRoom,drawer);

        for(int offset=-1;offset<=1;offset++) {
            int pane=x+offset;
            int wall=z+side*h.depth();
            // Remove recessed fittings behind the glass and projecting panels outside it.
            for (int d=h.depth()-1;d<=shell.radiusZ;d++) {
                if(d!=h.depth()) v.box(pane,y+2,z+side*d,pane,y+3,z+side*d,v.air);
            }
            v.box(pane,y+2,wall,pane,y+3,wall,ReachArchitecture.window(dusk,offset));
            if(gothic && offset==0)v.block(pane,y+4,wall,ReachArchitecture.window(true,1));
            int roofZ=z+side*offset;
            int roofY=y+(gothic?DuskHouse.roofHeight(h,-2):shell.roofHeight(-2*side,offset));
            v.box(x-2,y+3,roofZ,x-2,roofY-1,roofZ,v.air);
            v.block(x-2,roofY,roofZ,ReachArchitecture.window(dusk,offset));
        }
    }

    private static void furnishAlcove(HeartwaterFeature.Valley v, BlossomVillage.House h, VillageHouse shell,
                                      boolean dusk, String wood, boolean nativeRoom, Block drawer) {
        int x=h.x()+h.width()-1, y=h.floor()+1, z=h.z(), side=h.side();
        int front=z-side, rear=z+side;
        switch (h.style()) {
            case 0 -> {
                // Tall bookcases flank a reading sofa facing into the room.
                v.box(x,y,front,x,y+2,front,Blocks.BOOKSHELF);
                v.box(x,y,rear,x,y+2,rear,Blocks.BOOKSHELF);
                place(v,x,y,z,dusk?"purple_sofa":"pink_sofa",Direction.WEST);
            }
            case 1 -> {
                v.block(x,y,z,Blocks.CRAFTING_TABLE);
                place(v,x,y,front,drawer,Direction.WEST);
                boolean nativeShelf = !dusk && SettlementLayout.useSilkwoodShelf(v.level.getLevel(), v.site,
                        h.x()-shell.radiusX, z-shell.radiusZ, h.x()+shell.radiusX, z+shell.radiusZ);
                if (nativeShelf) {
                    // Each chunk writes its own end, even when the other end is outside its generation slice.
                    Direction facing = v.face(Direction.WEST);
                    Direction towardBench = v.face(side > 0 ? Direction.SOUTH : Direction.NORTH);
                    var shelf = NativeShelf.SHELF.get();
                    place(v,x,y+1,front,shelf.pairedState(facing,towardBench));
                    place(v,x,y+1,z,shelf.pairedState(facing,towardBench.getOpposite()));
                } else {
                    place(v,x,y+1,front,wood+"_shelf",Direction.WEST);
                    place(v,x,y+1,z,wood+"_shelf",Direction.WEST);
                }
                place(v,x-1,y,z-2*side,dusk?"purple_stool":"pink_stool",Direction.EAST);
            }
            case 2 -> {
                TeaSet set=TeaSet.select(nativeRoom,NativeTeaFurniture.WOVEN_REED,"bamboo");
                place(v,x,y,z,set.table(),Direction.WEST);
                place(v,x,y,front,set.chair(),side>0?Direction.SOUTH:Direction.NORTH);
                place(v,x,y,rear,set.chair(),side>0?Direction.NORTH:Direction.SOUTH);
                v.block(x,y,z-2*side,ReachArchitecture.PEARL.get());
                v.block(x,y+1,z-2*side,Blocks.CAKE);
            }
            case 3 -> {
                place(v,x,y,z,wood+"_bench",Direction.WEST);
                for(int potZ:new int[]{front,rear}) {
                    v.block(x,y,potZ,ReachArchitecture.GLAZE.get());
                    v.block(x,y+1,potZ,potZ==front?Blocks.POTTED_AZALEA:Blocks.POTTED_FLOWERING_AZALEA);
                }
                boolean nativeBox = !dusk && SettlementLayout.useSilkwoodFlowerBox(v.level.getLevel(), v.site,
                        h.x()-shell.radiusX, z-shell.radiusZ, h.x()+shell.radiusX, z+shell.radiusZ);
                Block box = nativeBox ? NativeFlowerBox.BOX.get() : external(wood+"_flower_box");
                place(v,x,y,z-2*side,box,Direction.WEST);
            }
        }
    }

    static void bathhouse(HeartwaterFeature.Valley v) {
        boolean nativeRoom=SettlementLayout.useNativeTea(v.level.getLevel(),v.site,-14,79,14,106);
        place(v,-9,53,91,"cherry_bench",Direction.EAST);
        place(v,-9,53,97,"cherry_bench",Direction.EAST);
        place(v,-9,53,94,nativeRoom?NativeTeaFurniture.SILKWOOD.table():external("cherry_table"),Direction.EAST);
        place(v,-9,54,94,"pink_lamp",Direction.UP);
        place(v,11,53,100,"bamboo_drawer",Direction.WEST);
    }

    static void archive(HeartwaterFeature.Valley v) {
        boolean nativeRoom=SettlementLayout.useNativeTea(v.level.getLevel(),v.site,5,137,27,151);
        TeaSet set=TeaSet.select(nativeRoom,NativeTeaFurniture.VELVETWOOD,"dark_oak");
        place(v,23,-18,143,set.table(),Direction.WEST);
        place(v,23,-17,143,"white_lamp",Direction.UP);
        place(v,24,-18,143,set.chair(),Direction.WEST);
        place(v,8,-18,141,"dark_oak_drawer",Direction.SOUTH);
    }

    private static void place(HeartwaterFeature.Valley v, int x, int y, int z, String id, Direction facing) {
        if(v.inside(x,z)) place(v,x,y,z,external(id),facing);
    }

    private static Block external(String id) {
        var key=ResourceLocation.fromNamespaceAndPath("another_furniture",id);
        return BuiltInRegistries.BLOCK.getOptional(key).orElseThrow(()->new IllegalStateException("Missing required furniture "+key));
    }

    private static void place(HeartwaterFeature.Valley v, int x, int y, int z, Block block, Direction facing) {
        if(!v.inside(x,z)) return;
        BlockState state=block.defaultBlockState();
        var direction=facing.getAxis().isHorizontal()?v.face(facing):facing;
        var property=block.getStateDefinition().getProperty("facing");
        if(property!=null) state=set(state,property,direction.getSerializedName());
        if(state.hasProperty(BlockStateProperties.LIT)) state=state.setValue(BlockStateProperties.LIT,true);
        place(v,x,y,z,state);
    }

    private static void place(HeartwaterFeature.Valley v, int x, int y, int z, BlockState state) {
        if(!v.inside(x,z)) return;
        // Use the worldgen API so functional drawers and other block entities are saved.
        if(v.planning()){v.put(x,y,z,state);return;}
        v.level.setBlock(new BlockPos(v.wx(x,z),y,v.wz(x,z)),state,Block.UPDATE_CLIENTS);
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String serialized) {
        return state.setValue(property,property.getValue(serialized).orElseThrow());
    }
}
