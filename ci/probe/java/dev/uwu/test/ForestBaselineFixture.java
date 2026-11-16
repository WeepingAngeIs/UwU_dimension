package dev.uwu.test;
import dev.uwu.dimension.*;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
/** Runs with the real preserved beta.7 JAR, without linking the later forest class. */
public final class ForestBaselineFixture {
    public static void run(ServerLevel level)throws Exception {
        var generator=level.getChunkSource().getGenerator();BlockPos base=null;
        for(int r=0;r<=1024&&base==null;r+=16)for(int sign:new int[]{-1,1}){
            int x=r*sign,z=32;
            int h=generator.getBaseHeight(x,z,net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,level,level.getChunkSource().randomState());
            if(generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(h),QuartPos.fromBlock(z),level.getChunkSource().randomState().sampler()).is(net.minecraft.world.level.biome.Biomes.CHERRY_GROVE))base=new BlockPos(x,level.getMaxBuildHeight()-46,z);
        }
        if(base==null)throw new AssertionError("No baseline cherry fixture");
        for(int x=(base.getX()-18)>>4;x<=(base.getX()+18)>>4;x++)for(int z=(base.getZ()-18)>>4;z<=(base.getZ()+18)>>4;z++)level.getChunk(x,z);
        for(var e:GiantSakuraTree.plan(0,0).entrySet())level.setBlock(base.offset(e.getKey()),e.getValue(),2);
        level.setBlockAndUpdate(base.below(2),Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity)level.getBlockEntity(base.below(2))).setItem(0,new ItemStack(Items.DIAMOND,37));
        level.setBlockAndUpdate(base.offset(1,-2,0),Blocks.OBSIDIAN.defaultBlockState());
        Files.writeString(Path.of("forest-old-origin.txt"),base.getX()+","+base.getY()+","+base.getZ());
        System.out.println("FOREST BETA7 BASELINE SAVED "+base);
    }
}
