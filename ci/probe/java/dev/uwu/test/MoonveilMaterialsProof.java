package dev.uwu.test;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Exercises resolved recipes, drops, tags and the same axe interaction used by a Survival player. */
final class MoonveilMaterialsProof {
    private static final String[] CUTS={"planks","stairs","slab","fence","fence_gate","door","trapdoor","button","pressure_plate","table","stool","bookshelf"};
    private static ResourceLocation id(String s){return ResourceLocation.fromNamespaceAndPath("uwudimension",s);}
    private static Block block(String name){return BuiltInRegistries.BLOCK.getOptional(id(name)).orElseThrow();}
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError("Moonveil wood: "+text);}
    private static void tag(String name,String tag){
        var key=ResourceLocation.withDefaultNamespace(tag);
        var b=block(name);
        check(b.defaultBlockState().is(TagKey.create(Registries.BLOCK,key)),name+" missing block tag "+tag);
        check(b.asItem().builtInRegistryHolder().is(TagKey.create(Registries.ITEM,key)),name+" missing item tag "+tag);
    }
    private static int count(ServerLevel level,BlockPos pos,net.minecraft.world.level.block.state.BlockState state,ItemStack tool,net.minecraft.world.item.Item item){
        return Block.getDrops(state,level,pos,null,null,tool).stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    static void run(ServerLevel level){
        var pos=new BlockPos(11, level.getMaxBuildHeight()-17,14);
        var saved=level.getBlockState(pos);
        var axe=new ItemStack(Items.DIAMOND_AXE);
        try(var test=new ChestProof.Visitor(level,pos,"MoonveilJoiner",UUID.randomUUID())){
            for(String name:List.of("moonveil_log","moonveil_wood","stripped_moonveil_log","stripped_moonveil_wood")){
                tag(name,"logs_that_burn");
                check(count(level,pos,block(name).defaultBlockState(),axe,block(name).asItem())==1,name+" regular drop");
            }
            for(String name:List.of("moonveil_log","moonveil_wood"))for(var axis:Direction.Axis.values()){
                var state=block(name).defaultBlockState().setValue(BlockStateProperties.AXIS,axis);
                level.setBlock(pos,state,3);
                test.player.setItemInHand(InteractionHand.MAIN_HAND,axe);
                var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false);
                var used=axe.useOn(new UseOnContext(test.player,InteractionHand.MAIN_HAND,hit));
                var stripped=level.getBlockState(pos);
                check(used.consumesAction()&&stripped.is(block("stripped_"+name))&&stripped.getValue(BlockStateProperties.AXIS)==axis,
                        "could not strip "+name+" along "+axis+": "+stripped);
            }
            var recipes=level.getRecipeManager();
            for(String name:CUTS){
                String key="moonveil_"+name;
                var b=block(key);
                var recipe=recipes.byKey(id("moonveil/"+key)).orElseThrow();
                check(recipe.value().getResultItem(level.registryAccess()).is(b.asItem()),key+" incorrect crafting result");
                if(name.equals("bookshelf"))check(count(level,pos,b.defaultBlockState(),axe,Items.BOOK)==3,"natural bookshelf drops");
                else check(count(level,pos,b.defaultBlockState(),axe,b.asItem())==1,key+" single block drop");
                check(b.defaultBlockState().is(TagKey.create(Registries.BLOCK,ResourceLocation.withDefaultNamespace("mineable/axe"))),key+" missing axe tag");
            }
            for(String key:List.of("moonveil_wood","stripped_moonveil_wood")){
                check(recipes.byKey(id("moonveil/"+key)).orElseThrow().value().getResultItem(level.registryAccess()).is(block(key).asItem()),key+" crafting");
            }
            String[][] cuts={{"planks","planks"},{"stairs","wooden_stairs"},{"slab","wooden_slabs"},{"fence","wooden_fences"},{"fence_gate","fence_gates"},
                    {"door","wooden_doors"},{"trapdoor","wooden_trapdoors"},{"button","wooden_buttons"},{"pressure_plate","wooden_pressure_plates"},{"leaves","leaves"}};
            for(var pair:cuts)tag("moonveil_"+pair[0],pair[1]);
            var planks=CraftingInput.of(1,1,List.of(new ItemStack(block("moonveil_log"))));
            var result=recipes.getRecipeFor(RecipeType.CRAFTING,planks,level).orElseThrow().value().assemble(planks,level.registryAccess());
            check(result.is(block("moonveil_planks").asItem())&&result.getCount()==4,"actual log to boards recipe");
            var slab=block("moonveil_slab");
            check(count(level,pos,slab.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,SlabType.DOUBLE),axe,slab.asItem())==2,"double slab drops");
            for(String wood:List.of("slab","stairs","fence","leaves")){
                var water=block("moonveil_"+wood).defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true);
                check(water.getFluidState().is(net.minecraft.tags.FluidTags.WATER)&&water.getFluidState().isSource(),wood+" waterlogging");
            }
            var leaves=block("moonveil_leaves");
            check(count(level,pos,leaves.defaultBlockState(),new ItemStack(Items.SHEARS),leaves.asItem())==1,"sheared leaves");
            var silk=axe.copy();
            silk.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH),1);
            var shelf=block("moonveil_bookshelf");
            check(count(level,pos,shelf.defaultBlockState(),silk,shelf.asItem())==1,"silk bookshelf");
            check(count(level,pos,leaves.defaultBlockState(),silk,leaves.asItem())==1,"silk foliage");
            var board=new ItemStack(block("moonveil_planks"));
            var stick=new ItemStack(Items.STICK);var book=new ItemStack(Items.BOOK);
            var seatTop=new ItemStack(slab);var space=ItemStack.EMPTY;
            var furniture=java.util.Map.of(
                    "table",CraftingInput.of(3,3,List.of(board,board,board,stick,space,stick,stick,space,stick)),
                    "stool",CraftingInput.of(3,2,List.of(space,seatTop,space,stick,space,stick)),
                    "bookshelf",CraftingInput.of(3,3,List.of(board,seatTop,board,book,book,book,board,board,board)));
            for(var entry:furniture.entrySet()){
                var expected=block("moonveil_"+entry.getKey());
                var craft=recipes.getRecipeFor(RecipeType.CRAFTING,entry.getValue(),level).orElseThrow().value();
                check(craft.assemble(entry.getValue(),level.registryAccess()).is(expected.asItem()),"ambiguous or missing ordinary furniture recipe "+entry.getKey());
            }
            var stool=block("moonveil_stool").defaultBlockState();level.setBlock(pos,stool,3);
            test.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            stool.useWithoutItem(level,test.player,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
            var seat=test.player.getVehicle();
            check(seat!=null&&BuiltInRegistries.ENTITY_TYPE.getKey(seat.getType()).getNamespace().equals("another_furniture"),"right click did not seat Survival player");
            test.player.stopRiding();seat.discard();
            System.out.println("MOONVEIL MATERIALS PASS native recipes, material tags, six axe stripping directions, waterlogging, normal/Shears/Silk Touch drops");
        }finally{level.setBlock(pos,saved,3);}
    }
}
