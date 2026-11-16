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
        var house=MoonveilTreehouse.first(level,false);
        var center=house.at(0,0,0);
        for(int x=(center.getX()-24)>>4;x<=(center.getX()+24)>>4;x++)for(int z=(center.getZ()-24)>>4;z<=(center.getZ()+24)>>4;z++)level.getChunk(x,z);
        if(!(level.getBlockState(house.door()).getBlock() instanceof DoorBlock)||!(level.getBlockState(house.bedHead(0)).getBlock() instanceof BedBlock)||!(level.getBlockState(house.bedHead(1)).getBlock() instanceof BedBlock))throw new AssertionError("Baseline treehouse did not actually generate");
        var notes=new java.util.ArrayList<String>();
        for(var point:java.util.List.of(house.door(),house.bedHead(0),house.bedHead(1)))
            notes.add(point.asLong()+","+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(point).getBlock()));
        Files.write(Path.of("forest-old-house.txt"),notes);
        System.out.println("FOREST BETA7 BASELINE SAVED "+base);
    }
}
