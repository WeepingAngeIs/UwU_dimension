package dev.uwu.test;

import java.util.*;
import dev.uwu.dimension.SakuraMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/** Hosted, disposable-world acceptance for land tree roots, native geology and conventional material uses. */
public final class ReachMaterialsProof {
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("uwudimension",path);}
    private static Block block(String path){return BuiltInRegistries.BLOCK.get(id(path));}
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void run(ServerLevel level){
        // These chunks are outside the old-save fixture and the settlement test sites.
        scan(level);
        BlockPos sky=new BlockPos(3632,level.getMaxBuildHeight()-36,-3192);
        int index=0;
        for(String tree:List.of("minecraft:cherry","uwudimension:starpetal_tree","uwudimension:moonberry_tree","uwudimension:heartbloom_tree")){
            var feature=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(ResourceLocation.parse(tree));
            check(feature!=null,"Missing tree "+tree);
            for(String kind:List.of("dry","shore","shallow","deep","waterlogged")){
                BlockPos at=sky.offset(index++*14,0,0);
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)
                    level.setBlock(at.offset(dx,-1,dz),SakuraMaterials.GRASS.get().defaultBlockState(),2);
                if(kind.equals("shore"))level.setBlock(at.offset(1,-1,0),Blocks.WATER.defaultBlockState(),2);
                if(kind.equals("shallow"))level.setBlock(at,Blocks.WATER.defaultBlockState(),2);
                if(kind.equals("deep"))for(int h=-4;h<=1;h++)level.setBlock(at.above(h),Blocks.WATER.defaultBlockState(),2);
                if(kind.equals("waterlogged"))level.setBlock(at.offset(-1,0,1),Blocks.OAK_SLAB.defaultBlockState()
                        .setValue(BlockStateProperties.WATERLOGGED,true),2);
                boolean placed=feature.place(level,level.getChunkSource().getGenerator(),RandomSource.create(8471L),at);
                check(placed==kind.equals("dry"),tree+" "+kind+" unexpected placement="+placed+" at "+at);
                if(placed)check(level.getBlockState(at).is(BlockTags.LOGS),"Dry tree left no trunk for "+tree);
                if(kind.equals("shallow")||kind.equals("deep"))check(level.getFluidState(at).is(FluidTags.WATER),"Aquatic negative control lost water");
            }
        }
        materials(level,sky.north(14));
        overworld(level.getServer().overworld(),sky.north(96));
        System.out.println("SAKURA NATIVE MATERIALS AND DRY ROOTS PASS seed="+level.getSeed()
                +" trees=4 wet-controls=16 dry-controls=4 functional-gravel=true ore-drops=true silver-chain=true");
    }
    private static void overworld(ServerLevel level,BlockPos sky){
        var chunk=level.getChunkAt(sky);
        for(var section:chunk.getSections())if(!section.hasOnlyAir())section.getStates().count((state,count)->{
            if(count>0)check(!state.is(SakuraMaterials.STONE.get())&&!state.is(SakuraMaterials.GRASS.get())
                    &&!state.is(SakuraMaterials.GRAVEL.get()),"Overworld generated native Sakura terrain");
        });
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)level.setBlock(sky.offset(dx,-1,dz),Blocks.GRASS_BLOCK.defaultBlockState(),2);
        level.setBlock(sky.offset(1,-1,0),Blocks.WATER.defaultBlockState(),2);
        var feature=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(ResourceLocation.withDefaultNamespace("cherry"));
        check(feature!=null && feature.place(level,level.getChunkSource().getGenerator(),RandomSource.create(8471L),sky)
                && level.getBlockState(sky).is(Blocks.CHERRY_LOG),"Native Sakura water restriction leaked into Overworld");
    }
    private static void scan(ServerLevel level){
        var required=new LinkedHashSet<>(List.of("sakura_stone","sakura_deepslate","rainbow_gravel","silver","iron","diamond"));
        Map<String,Integer> totals=new TreeMap<>();
        int visited=0;
        for(int radius=0;radius<=4&&!required.isEmpty();radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            var chunk=level.getChunk(184+dx,-197+dz);visited++;
            for(var section:chunk.getSections()){
                if(section.hasOnlyAir())continue;
                section.getStates().count((state,count)->{
                    if(count<=0)return;
                    check(!state.is(Blocks.GRAVEL)&&!state.is(Blocks.IRON_ORE)&&!state.is(Blocks.DEEPSLATE_IRON_ORE)
                            &&!state.is(Blocks.DIAMOND_ORE)&&!state.is(Blocks.DEEPSLATE_DIAMOND_ORE),"Fresh native palette left "+state+" in "+chunk.getPos());
                    var key=BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if(!key.getNamespace().equals("uwudimension"))return;
                    String name=key.getPath();
                    if(name.endsWith("_ore")){
                        for(String ore:List.of("silver","iron","diamond"))if(name.endsWith(ore+"_ore")){name=ore;break;}
                    }
                    totals.merge(name,count,Integer::sum);
                    required.remove(name);
                });
            }
        }
        check(required.isEmpty(),"Natural geology survey missing "+required+" in "+visited+" fresh chunks");
        System.out.println("SAKURA NATIVE STRATA seed="+level.getSeed()+" chunks="+visited+" surveyed="+totals);
    }
    private static void materials(ServerLevel level,BlockPos at){
        var tool=new ItemStack(Items.DIAMOND_PICKAXE);
        var silk=tool.copy();
        silk.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH),1);
        for(String depth:List.of("","deepslate_")){
            for(String ore:List.of("iron","diamond","silver")){
                String name=depth+(ore.equals("silver")?"":"sakura_")+ore+"_ore";
                var mineral=block(name);var state=mineral.defaultBlockState();level.setBlock(at,state,2);
                var expected=switch(ore){case "iron"->Items.RAW_IRON;case "diamond"->Items.DIAMOND;default->SakuraMaterials.RAW.get();};
                check(tool.isCorrectToolForDrops(state),"Diamond pick cannot harvest "+name);
                check(Block.getDrops(state,level,at,null,null,tool).stream().anyMatch(s->s.is(expected)),"Wrong normal drop "+name);
                check(Block.getDrops(state,level,at,null,null,silk).stream().anyMatch(s->s.is(mineral.asItem())),"Wrong silk drop "+name);
                var input=new SingleRecipeInput(new ItemStack(mineral));
                var smelt=level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,input,level).orElseThrow();
                var result=smelt.value().assemble(input,level.registryAccess());
                var product=switch(ore){case "iron"->Items.IRON_INGOT;case "diamond"->Items.DIAMOND;default->SakuraMaterials.INGOT.get();};
                check(result.is(product),"Wrong smelting output for "+name+": "+result);
            }
        }
        var input=CraftingInput.of(1,3,List.of(new ItemStack(SakuraMaterials.NUGGET.get()),new ItemStack(SakuraMaterials.INGOT.get()),new ItemStack(SakuraMaterials.NUGGET.get())));
        var chain=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow().value().assemble(input,level.registryAccess());
        check(chain.is(SakuraMaterials.CHAIN.get().asItem()),"Chain recipe output="+chain);
        var chainState=SakuraMaterials.CHAIN.get().defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X)
                .setValue(BlockStateProperties.WATERLOGGED,true);
        check(chainState.getFluidState().isSource()&&chainState.getFluidState().is(FluidTags.WATER),"Native chain loses water when axis X");
        var gravel=SakuraMaterials.GRAVEL.get();
        var shovel=new ItemStack(Items.DIAMOND_SHOVEL);
        shovel.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FORTUNE),3);
        check(Block.getDrops(gravel.defaultBlockState(),level,at,null,null,shovel).stream().anyMatch(s->s.is(Items.FLINT)),"Fortune III gravel must drop flint");
        var conversion=CraftingInput.of(1,1,List.of(new ItemStack(gravel)));
        check(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,conversion,level).orElseThrow().value().assemble(conversion,level.registryAccess()).is(Blocks.GRAVEL.asItem()),"Gravel compatibility recipe failed");
        level.setBlock(at,Blocks.STONE.defaultBlockState(),2);
        var top=at.above(5);
        level.setBlock(top,gravel.defaultBlockState(),2);
        level.getBlockState(top).tick(level,top,RandomSource.create(65L));
        var falling=level.getEntitiesOfClass(FallingBlockEntity.class,new AABB(top).inflate(1));
        check(falling.size()==1,"Rainbow gravel did not become a falling block");
        for(int i=0;i<60&&falling.getFirst().isAlive();i++)falling.getFirst().tick();
        check(level.getBlockState(at.above()).is(gravel),"Falling rainbow gravel lost its material when landing");
    }
}
