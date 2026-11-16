package dev.uwu.test;
import dev.uwu.dimension.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.item.Items;
public final class ForestUpgradeProof {
    private static boolean untouched(ServerLevel level,int x,int z,int xx,int zz)throws Exception {var method=SettlementLayout.class.getDeclaredMethod("untouchedForForest",ServerLevel.class,int.class,int.class,int.class,int.class);method.setAccessible(true);return (boolean)method.invoke(null,level,x,z,xx,zz);}
    public static void run(ServerLevel level)throws Exception {
        var words=Files.readString(Path.of("forest-old-origin.txt")).split(",");var base=new BlockPos(Integer.parseInt(words[0]),Integer.parseInt(words[1]),Integer.parseInt(words[2]));
        if(untouched(level,base.getX()-17,base.getZ()-17,base.getX()+17,base.getZ()+17))throw new AssertionError("Existing beta7 chunks absent from migration snapshot");
        for(var e:GiantSakuraTree.plan(0,0).entrySet())if(!level.getBlockState(base.offset(e.getKey())).equals(e.getValue()))throw new AssertionError("Existing authored tree changed "+e.getKey());
        if(!(level.getBlockEntity(base.below(2)) instanceof ChestBlockEntity chest)||!chest.getItem(0).is(Items.DIAMOND)||chest.getItem(0).getCount()!=37||!level.getBlockState(base.offset(1,-2,0)).is(Blocks.OBSIDIAN))throw new AssertionError("Existing player build/inventory changed");
        if(!untouched(level,1000000,1000000,1000034,1000034))throw new AssertionError("Unexplored terrain incorrectly marked old");
        // A new tree cannot straddle any old boundary, even if its center is in a new chunk.
        for(int x=Math.floorDiv(base.getX()-32,SakuraForest.STEP_X);x<=Math.floorDiv(base.getX()+32,SakuraForest.STEP_X);x++)for(int z=Math.floorDiv(base.getZ()-32,SakuraForest.STEP_Z);z<=Math.floorDiv(base.getZ()+32,SakuraForest.STEP_Z);z++)
            SakuraForest.tree(level,x,z).ifPresent(tree->{if(tree.x()-17<=base.getX()+16&&tree.x()+17>=base.getX()-16&&tree.z()-17<=base.getZ()+16&&tree.z()+17>=base.getZ()-16)throw new AssertionError("Tree entered existing saved chunks");});
        var house=MoonveilTreehouse.first(level,false);
        var notes=Files.readAllLines(Path.of("forest-old-house.txt"));
        for(var note:notes){var parts=note.split(",");var point=BlockPos.of(Long.parseLong(parts[0]));
            if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(point).getBlock()).toString().equals(parts[1]))throw new AssertionError("Existing treehouse block changed "+point);
        }
        if(house.door().asLong()!=Long.parseLong(notes.getFirst().split(",")[0]))throw new AssertionError("Existing treehouse household plot changed");
        System.out.println("FOREST BETA7 UPGRADE PASS 5026 old blocks, chest NBT, build and new/old chunk boundary");
    }
}
