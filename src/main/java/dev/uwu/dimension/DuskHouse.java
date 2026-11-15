package dev.uwu.dimension;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;

/** Short chapels, lantern halls, paired belfries and star observatories. Their roofs
 * are cut as pitched gables; none of the four borrow the Petal pagoda roof. */
final class DuskHouse {
    static int roofHeight(BlossomVillage.House h,int dx) { return 5+h.width()+1-Math.abs(dx); }

    static void place(HeartwaterFeature.Valley v,BlossomVillage.House h) {
        var w=ReachArchitecture.DUSK;
        int y=h.floor(),width=h.width(),depth=h.depth(),side=h.side();
        int front=h.z()-side*depth,back=h.z()+side*depth;
        for(int dx=-width-1;dx<=width+1;dx++){
            int x=h.x()+dx, roof=roofHeight(h,dx);
            for(int dz=-depth-1;dz<=depth+1;dz++){
                int z=h.z()+dz, edge=Math.abs(dx);
                if(edge==0) v.block(x,y+roof,z,ReachArchitecture.ROOF_SLAB.get());
                else v.put(x,y+roof,z,v.stair(ReachArchitecture.ROOF_STEP.get(),dx<0?Direction.EAST:Direction.WEST));
                // The exposed triangular ends carry a pointed glass rose over the entrance.
                if(Math.abs(dz)==depth && edge<=width){
                    for(int dy=1;dy<roof;dy++){
                        var tile=dy==1||dy==5?ReachArchitecture.SLATE.get():w.boards().get();
                        if(edge==width || (edge==2 && dy<=5)) tile=w.beam().get();
                        if(dy>=5 && edge+Math.abs(dy-7)<=2)tile=ReachArchitecture.window(true,Integer.compare(dx,0));
                        if(dy==6 && edge>=3)tile=HeartwaterFeature.LACE.get();
                        v.block(x,y+dy,z,tile);
                    }
                }
                if(edge==width-1 && Math.abs(dz)<depth && Math.floorMod(dz+depth,4)==2)
                    v.block(x,y+2,z,w.slab().get());
                if(edge==width && Math.abs(dz)<depth){
                    for(int dy=1;dy<=5;dy++){
                        boolean bay=Math.floorMod(dz+depth,4)==2;
                        var tile=dy==1||dy==5?ReachArchitecture.SLATE.get():w.boards().get();
                        if(Math.floorMod(dz+depth,4)==0)tile=w.beam().get();
                        if(bay && dy>=2 && dy<=4)tile=ReachArchitecture.window(true,1);
                        v.block(x,y+dy,z,tile);
                    }
                    if(Math.floorMod(dz+depth,4)==0) {
                        int out=x+Integer.signum(dx);
                        v.box(out,y+1,z,out,y+2,z,ReachArchitecture.SLATE.get());
                        v.put(out,y+3,z,v.stair(ReachArchitecture.steps(true),dx<0?Direction.EAST:Direction.WEST));
                    }
                }
            }
        }
        for(int dx:new int[]{-2,2}){
            int x=h.x()+dx, porch=front-side;
            v.box(x,y+1,porch,x,y+3,porch,ReachArchitecture.RIB.get());
            v.put(x,y+4,porch,v.stair(ReachArchitecture.steps(true),dx<0?Direction.EAST:Direction.WEST)
                    .setValue(StairBlock.HALF,Half.TOP));
            v.put(x,y+6,porch,v.stair(w.stairs().get(),dx<0?Direction.EAST:Direction.WEST));
            v.block(x,y+5,porch,HeartwaterFeature.LACE.get());
            int jut=front+side;
            var shutter=w.shutter().get().defaultBlockState().setValue(TrapDoorBlock.OPEN,true)
                    .setValue(TrapDoorBlock.FACING,v.face(side>0?Direction.NORTH:Direction.SOUTH));
            v.put(x,y+3,jut,shutter);
        }
        for(int dx:new int[]{-1,1})v.block(h.x()+dx,y+5,front-side,ReachArchitecture.SLATE.get());
        v.block(h.x(),y+6,front-side,ReachArchitecture.TRACERY.get());
        for(int dx:new int[]{-width,width}){
            int x=h.x()+dx, end=front-side;
            v.box(x,y+5,end,x,y+7,end,w.beam().get());
            v.block(x,y+8,end,ReachArchitecture.ROOF_SLAB.get());
            v.block(x,y+4,end,SakuraMaterials.CHAIN.get());
            v.put(x,y+3,end,HeartLanterns.DUSK.get().defaultBlockState().setValue(LanternBlock.HANGING,true));
        }
        // A pair of interior rib vaults meets over the nave. The center row stays
        // open to villagers and the western clerestory keeps its three clear skylights.
        for(int z:new int[]{front+side*(depth>3?1:0),back-side}){
            for(int reach=1;reach<width;reach++){
                int rise=5+width-reach;
                for(int dx:new int[]{-reach,reach})v.block(h.x()+dx,y+rise,z,ReachArchitecture.RIB.get());
            }
            v.block(h.x(),y+width+4,z,ReachArchitecture.TRACERY.get());
        }
        // Each footprint has a different skyline while its central entrance, bed
        // alcove, jobs and sightline through the nave are shared with keeper navigation.
        switch(h.style()){
            case 0 -> spire(v,h.x(),y+roofHeight(h,0),front,3);
            case 1 -> {
                spire(v,h.x(),y+roofHeight(h,0),front,2);
                spire(v,h.x(),y+roofHeight(h,0),back,2);
            }
            case 2 -> {
                spire(v,h.x()-2,y+roofHeight(h,-2),front,4);
                spire(v,h.x()+2,y+roofHeight(h,2),front,4);
            }
            case 3 -> {
                int top=y+roofHeight(h,0);
                v.box(h.x()-1,top,h.z()-1,h.x()+1,top,h.z()+1,ReachArchitecture.SLATE.get());
                v.box(h.x(),top+1,h.z(),h.x(),top+2,h.z(),ReachArchitecture.window(true,1));
                spire(v,h.x(),top+2,h.z(),2);
            }
        }
        // Low gallery rails flank the front step, leaving the door's center lane open.
        for(int dx=-width+1;dx<width;dx++) if(Math.abs(dx)>2){
            var rail=w.fence().get().defaultBlockState();
            for(Direction direction:new Direction[]{v.face(Direction.EAST),v.face(Direction.WEST)})
                rail=rail.setValue(switch(direction){case EAST->net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST; case WEST->net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST; case NORTH->net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH; default->net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH;},true);
            v.put(h.x()+dx,y+1,front-side,rail);
        }
    }
    private static void spire(HeartwaterFeature.Valley v,int x,int y,int z,int height) {
        v.block(x,y,z,ReachArchitecture.SHINGLES.get());
        v.block(x,y+1,z,ReachArchitecture.TRACERY.get());
        v.box(x,y+2,z,x,y+height,z,ReachArchitecture.DUSK.fence().get());
        v.block(x,y+height+1,z,SakuraMaterials.CHAIN.get());
    }
}
