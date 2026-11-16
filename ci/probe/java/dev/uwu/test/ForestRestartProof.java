package dev.uwu.test;
import dev.uwu.dimension.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
public final class ForestRestartProof {
    static void run(ServerLevel level)throws Exception {
        var parts=Files.readString(Path.of("forest-rejected-root.txt")).split(",");
        int x=Integer.parseInt(parts[0]),y=Integer.parseInt(parts[1]),z=Integer.parseInt(parts[2]);
        if(SakuraForest.tree(level,Math.floorDiv(x+2,SakuraForest.STEP_X),Math.floorDiv(z+2,SakuraForest.STEP_Z)).isPresent())throw new AssertionError("Rejected forest stamp lost across restart");
        var state=level.getBlockState(new BlockPos(x,y,z));
        if(state.is(Blocks.CHERRY_WOOD)||state.is(Blocks.CHERRY_LOG))throw new AssertionError("Restart regenerated protected root");
        System.out.println("FOREST REJECTION RESTART PASS no delayed crown or retrogen after unclaim/restart");
    }
}
