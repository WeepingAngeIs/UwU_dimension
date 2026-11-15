package dev.uwu.dimension;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;

/** Room envelopes sit inside the protected shell. All measures are valley-local and rotate with the building. */
final class ReachChambers {
    enum Room { BATH, ARCHIVE, COURT, SHRINE }

    static Block face(Room room) {
        return switch(room) {
            case BATH -> ReachArchitecture.PEARL.get();
            case ARCHIVE, COURT -> ReachArchitecture.SLATE.get();
            case SHRINE -> ReachArchitecture.DUSK.boards().get();
        };
    }
    static void room(HeartwaterFeature.Valley v, Room room,int x1,int y1,int z1,int x2,int y2,int z2) {
        int left=x1+1, right=x2-1, near=z1+1, far=z2-1;
        for(int z=near;z<=far;z++){
            wall(v,room,left,z,1,0,z-near,y1+1,y2-1);
            wall(v,room,right,z,-1,0,far-z,y1+1,y2-1);
        }
        for(int x=left+1;x<right;x++){
            wall(v,room,x,near,0,1,x-left,y1+1,y2-1);
            wall(v,room,x,far,0,-1,right-x,y1+1,y2-1);
        }
        int span=room==Room.SHRINE?4:6;
        for(int z=near+2;z<far;z+=span){
            for(int x=left+1;x<right;x++){
                int setback=Math.min(x-left,right-x);
                int drop=setback==1?2:1;
                v.block(x,y2-1-drop,z,ReachArchitecture.RIB.get());
                if(setback>2 && (x-left)%5==0) v.block(x,y2-2,z,ReachArchitecture.PILOT.get());
            }
        }
    }

    private static void wall(HeartwaterFeature.Valley v, Room room,int x,int z,int dx,int dz,int along,int floor,int ceiling) {
        if(!v.overlaps(x-1,z-1,x+1,z+1)) return;
        int beat=Math.floorMod(along,room==Room.SHRINE?4:6);
        for(int y=floor+1;y<ceiling;y++) {
            Block panel;
            if(y==floor+1 || y==ceiling-1) panel=ReachArchitecture.SCALE.get();
            else if(y==floor+2 || y==ceiling-2) panel=ReachArchitecture.TRACERY.get();
            else if(beat==3 && (y==floor+4||y==floor+5)) panel=room==Room.ARCHIVE?
                    HeartwaterFeature.LACE.get():ReachArchitecture.window(room!=Room.BATH,-1);
            else panel=face(room);
            v.block(x,y,z,panel);
        }
        // The shallow galleries keep their original walkable footprint at floor level. A
        // pilaster starts in the skirting and leans out overhead into a forked capital.
        if(beat==0) {
            if(room!=Room.SHRINE) {
                int foot=room==Room.ARCHIVE && dz==-1 ? floor+3 : floor+1;
                v.box(x+dx,foot,z+dz,x+dx,ceiling-2,z+dz,ReachArchitecture.RIB.get());
            }
            else v.block(x+dx,ceiling-2,z+dz,ReachArchitecture.RIB.get());
        } else if(beat==1 || beat==(room==Room.SHRINE?3:5)) {
            v.block(x+dx,ceiling-2,z+dz,ReachArchitecture.RIB.get());
        }
    }

    /** This reliquary is seen from below and around all four sides. Its protective core
     * is continuously clad, with four hanging medallions under ribbed eaves. */
    static void shrineExterior(HeartwaterFeature.Valley v) {
        for(int x=49;x<=58;x++) for(int z=114;z<=124;z++){
            boolean side=x==49||x==58, end=z==114||z==124;
            if(side||end) {
                for(int y=7;y<=16;y++){
                    if(z==114 && Math.abs(x-53)<=1 && y>=9 && y<=11) continue;
                    int t=side?z-114:x-49;
                    Block tile=(y==7||y==12||y==16)?ReachArchitecture.TRACERY.get():
                            (t%4==0?ReachArchitecture.RIB.get():ReachArchitecture.DUSK.boards().get());
                    if(y==10 && t%4==2) tile=ReachArchitecture.PILOT.get();
                    v.block(x,y,z,tile);
                }
            } else {
                v.block(x,16,z,ReachArchitecture.SCALE.get());
                v.block(x,6,z,ReachArchitecture.SCALE.get());
            }
            if(side && end) v.block(x,6,z,ReachArchitecture.PILOT.get());
        }
    }

    /** The descent is cut through hillside dirt. Line the full mouth and every descending
     * bay: keyed panels alternate with bright pilasters and an overhead transverse arch. */
    static void entrance(HeartwaterFeature.Valley v,int z,int floor,int first) {
        int bay=Math.floorMod(z-first,6);
        for(int sign:new int[]{-1,1}){
            int x=sign*3;
            for(int y=0;y<=5;y++){
                Block block;
                if(bay==0) block=y==3?ReachArchitecture.PILOT.get():ReachArchitecture.RIB.get();
                else if(y==0 || y==5) block=ReachArchitecture.SLATE.get();
                else if(y==1 || y==4) block=ReachArchitecture.TRACERY.get();
                else block=bay==3?ReachArchitecture.SCALE.get():ReachArchitecture.PEARL.get();
                v.block(x,floor+y,z,block);
            }
        }
        for(int x=-2;x<=2;x++){
            v.block(x,floor+5,z,bay==0?ReachArchitecture.RIB.get():
                    Math.abs(x)==2?ReachArchitecture.SLATE.get():ReachArchitecture.PEARL.get());
            if(bay==0 && Math.abs(x)==2){
                Direction facing=x<0?Direction.WEST:Direction.EAST;
                v.put(x,floor+4,z,v.stair(ReachArchitecture.steps(false),facing).setValue(StairBlock.HALF,Half.TOP));
            }
        }
    }

    static void terrace(HeartwaterFeature.Valley v,int x1,int z1,int x2,int z2,int y,boolean shallower) {
        boolean alongZ=z1==z2;
        if(!alongZ && x1!=x2)return;
        int station=alongZ?z1:x1;
        int phase=Math.floorMod(station,7);
        for(int side:new int[]{-1,1}){
            int x=alongZ?(side<0?x1-1:x2+1):x1;
            int z=alongZ?z1:(side<0?z1-1:z2+1);
            for(int rise=0;rise<=6;rise++){
                Block face;
                if(phase==0) face=rise==3?ReachArchitecture.PILOT.get():ReachArchitecture.RIB.get();
                else if(rise==2||rise==5) face=ReachArchitecture.TRACERY.get();
                else if(rise==1) face=ReachArchitecture.SCALE.get();
                else face=ReachArchitecture.paving(!shallower);
                v.block(x,y+rise,z,face);
            }
        }
        v.box(x1,y+6,z1,x2,y+6,z2,phase==0?ReachArchitecture.RIB.get():ReachArchitecture.paving(!shallower));
    }
}
