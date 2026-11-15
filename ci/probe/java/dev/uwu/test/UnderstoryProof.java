package dev.uwu.test;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Samples normal chunk decoration first, then uses native survival item and harvest interactions on isolated plots. */
public final class UnderstoryProof {
    private static final List<String> CUTS=List.of("short_grass","tall_grass","lace_fern","star_clover","rain_bell","wood_aster","frost_sorrel");
    private static ResourceLocation id(String key){return ResourceLocation.fromNamespaceAndPath("uwudimension",key);}
    private static TagKey<Block> tag(String key){return TagKey.create(Registries.BLOCK,id(key));}
    private static Block block(String key){return BuiltInRegistries.BLOCK.getOptional(id(key)).orElseThrow();}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError("Understory: "+why);}

    public static void run(ServerLevel level){
        for(var side:List.of("petal","dusk"))sample(level,side);
        BlockPos base=new BlockPos(3616,level.getMaxBuildHeight()-28,-3168);
        try(var visitor=new ChestProof.Visitor(level,base,"GroveBotanist",UUID.randomUUID())){
            for(var side:List.of("petal","dusk")){
                Block turf=block(side.equals("petal")?"sakura_grass_block":"dusk_grass_block");
                check(turf.defaultBlockState().is(tag("grove_turf")),"untagged turf "+turf);
                for(var cut:CUTS)testPlant(level,visitor.player,base,turf,side,cut);
            }
        }
        System.out.println("NATIVE UNDERSTORY PASS both land communities, fourteen natural plants, dry placement, both tall halves, ordinary/shear drops and live dye recipes");
    }

    private static void sample(ServerLevel level,String side){
        var herbs=tag(side+"_understory");
        TagKey<Biome> biome=TagKey.create(Registries.BIOME,id(side+"_understory_land"));
        var totals=new TreeMap<String,Integer>();
        var wanted=new HashSet<String>();
        CUTS.forEach(cut->wanted.add(side+"_"+cut));
        var visited=new HashSet<Long>();
        var source=level.getChunkSource();
        var gen=source.getGenerator();
        var random=source.randomState();
        int patches=0;
        // Start outside all legacy test districts. Locate the actual surface biome through its generator,
        // then survey contiguous real chunks so this still exercises native decoration on an upgrade.
        for(int ring=0;ring<=28&&!wanted.isEmpty()&&visited.size()<96;ring++){
            for(int rx=-ring;rx<=ring&&!wanted.isEmpty()&&visited.size()<96;rx++)for(int rz=-ring;rz<=ring&&!wanted.isEmpty()&&visited.size()<96;rz++){
                if(Math.max(Math.abs(rx),Math.abs(rz))!=ring)continue;
                int cx=235+rx*8,cz=-255+rz*8,x=(cx<<4)+8,z=(cz<<4)+8;
                if(!gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(78),QuartPos.fromBlock(z),random.sampler()).is(biome))continue;
                int y=gen.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,random)-1;
                if(y<level.getSeaLevel()||!gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(y+1),QuartPos.fromBlock(z),random.sampler()).is(biome))continue;
                patches++;
                for(int dx=-1;dx<=1&&!wanted.isEmpty();dx++)for(int dz=-1;dz<=1&&!wanted.isEmpty();dz++){
                    if(!visited.add(ChunkPos.asLong(cx+dx,cz+dz)))continue;
                    var chunk=level.getChunk(cx+dx,cz+dz);
                    surveyChunk(level,chunk,herbs,biome,totals,wanted);
                }
            }
        }
        System.out.println("UNDERSTORY NATURAL COUNTS "+side+" seed="+level.getSeed()+" patches="+patches+" chunks="+visited.size()+" rooted="+totals);
        check(wanted.isEmpty(),"missing naturally generated "+wanted+" in "+visited.size()+" sampled "+side+" chunks");
    }
    private static void surveyChunk(ServerLevel level,LevelChunk chunk,TagKey<Block> herbs,TagKey<Biome> biome,
            Map<String,Integer> totals,Set<String> wanted){
        var sections=chunk.getSections();
        for(int i=0;i<sections.length;i++){
            var section=sections[i];
            if(!section.maybeHas(s->s.is(herbs)))continue;
            int bottom=chunk.getSectionYFromSectionIndex(i)<<4;
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                var plant=section.getBlockState(x,y,z);
                if(!plant.is(herbs))continue;
                var at=new BlockPos(chunk.getPos().getMinBlockX()+x,bottom+y,chunk.getPos().getMinBlockZ()+z);
                if(!level.getBiome(at).is(biome))continue;
                if(plant.hasProperty(DoublePlantBlock.HALF)&&plant.getValue(DoublePlantBlock.HALF)==DoubleBlockHalf.UPPER){
                    check(level.getBlockState(at.below()).is(plant.getBlock()),"orphan natural upper at "+at);continue;
                }
                var soil=level.getBlockState(at.below());
                check(soil.is(tag("understory_soil"))&&soil.getFluidState().isEmpty()&&plant.getFluidState().isEmpty()
                        &&plant.canSurvive(level,at),"unsupported/wet natural "+plant+" on "+soil+" at "+at);
                if(plant.hasProperty(DoublePlantBlock.HALF))check(level.getBlockState(at.above()).is(plant.getBlock()),"orphan natural lower at "+at);
                String name=BuiltInRegistries.BLOCK.getKey(plant.getBlock()).getPath();
                totals.merge(name,1,Integer::sum);wanted.remove(name);
            }
        }
    }

    private static void testPlant(ServerLevel level,ServerPlayer player,BlockPos foot,Block turf,String side,String cut){
        String name=side+"_"+cut;
        Block herb=block(name);
        boolean tall=cut.equals("tall_grass");
        boolean grass=tall||cut.equals("short_grass")||cut.equals("lace_fern");
        check(herb.defaultBlockState().is(tag(side+"_understory")),"missing flora tag for "+name);
        for(int i=0;i<3;i++)level.removeBlock(foot.above(i),false);
        level.setBlockAndUpdate(foot.below(),Blocks.STONE.defaultBlockState());
        check(!place(level,player,foot,herb),"planted on stone "+name);
        level.setBlockAndUpdate(foot.below(),turf.defaultBlockState());
        level.setBlockAndUpdate(foot,Blocks.WATER.defaultBlockState());
        check(!place(level,player,foot,herb)&&level.getBlockState(foot).is(Blocks.WATER),"displaced water "+name);
        level.removeBlock(foot,false);
        for(int half=0;half<(tall?2:1);half++)for(boolean sheared:new boolean[]{false,true}){
            Block support=sheared?turf:Blocks.DIRT;
            level.setBlockAndUpdate(foot.below(),support.defaultBlockState());
            check(place(level,player,foot,herb),"dry "+support+" placement failed for "+name);
            if(tall)check(level.getBlockState(foot.above()).is(herb)&&level.getBlockState(foot.above()).getValue(DoublePlantBlock.HALF)==DoubleBlockHalf.UPPER,"upper missing "+name);
            var bounds=new AABB(foot).inflate(2);
            var before=new HashSet<>(level.getEntitiesOfClass(ItemEntity.class,bounds));
            player.setItemInHand(InteractionHand.MAIN_HAND,sheared?new ItemStack(Items.SHEARS):ItemStack.EMPTY);
            check(player.gameMode.destroyBlock(foot.above(half)),"survival harvest rejected "+name);
            check(!level.getBlockState(foot).is(herb)&&!level.getBlockState(foot.above()).is(herb),"surviving half "+name);
            var drops=level.getEntitiesOfClass(ItemEntity.class,bounds,e->!before.contains(e));
            int n=0;
            for(var entity:drops){
                check(entity.getItem().is(herb.asItem()),"unexpected "+entity.getItem()+" from "+name);
                n+=entity.getItem().getCount();entity.discard();
            }
            check(n==(grass&&!sheared?0:1),"actual survival drops "+name+" upper="+(half==1)+" shears="+sheared+" count="+n);
        }
        if(!grass){
            var grid=CraftingInput.of(1,1,List.of(new ItemStack(herb)));
            var recipe=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).orElseThrow();
            ItemStack dye=recipe.value().assemble(grid,level.registryAccess());
            check(recipe.id().equals(id(name+"_dye"))&&dye.getItem() instanceof DyeItem&&dye.getCount()>0,"wrong dye recipe "+name+" -> "+dye);
        }
    }
    private static boolean place(ServerLevel level,ServerPlayer player,BlockPos foot,Block herb){
        var stack=new ItemStack(herb);
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3.atBottomCenterOf(foot),Direction.UP,foot.below(),false);
        var result=stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
        return result.consumesAction()&&level.getBlockState(foot).is(herb);
    }
}
