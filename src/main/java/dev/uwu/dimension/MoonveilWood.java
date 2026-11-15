package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Giant Moonveil trees and the joinery that can be cut from their heartwood. */
public final class MoonveilWood {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("uwudimension");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("uwudimension");
    private static final List<DeferredBlock<?>> FAMILY=new ArrayList<>();

    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_LOG=add("stripped_moonveil_log",()->new RotatedPillarBlock(like(Blocks.STRIPPED_CHERRY_LOG)));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_WOOD=add("stripped_moonveil_wood",()->new RotatedPillarBlock(like(Blocks.STRIPPED_CHERRY_WOOD)));
    public static final DeferredBlock<RotatedPillarBlock> LOG=bark("moonveil_log",Blocks.CHERRY_LOG,STRIPPED_LOG);
    public static final DeferredBlock<RotatedPillarBlock> WOOD=bark("moonveil_wood",Blocks.CHERRY_WOOD,STRIPPED_WOOD);
    public static final DeferredBlock<Block> PLANKS=add("moonveil_planks",()->new Block(like(Blocks.CHERRY_PLANKS)));
    public static final DeferredBlock<LeavesBlock> LEAVES=add("moonveil_leaves",()->new LeavesBlock(like(Blocks.CHERRY_LEAVES)));
    public static final DeferredBlock<StairBlock> STAIRS=add("moonveil_stairs",()->new StairBlock(PLANKS.get().defaultBlockState(),like(Blocks.CHERRY_STAIRS)));
    public static final DeferredBlock<SlabBlock> SLAB=add("moonveil_slab",()->new SlabBlock(like(Blocks.CHERRY_SLAB)));
    public static final DeferredBlock<FenceBlock> FENCE=add("moonveil_fence",()->new FenceBlock(like(Blocks.CHERRY_FENCE)));
    public static final DeferredBlock<FenceGateBlock> GATE=add("moonveil_fence_gate",()->new FenceGateBlock(WoodType.CHERRY,like(Blocks.CHERRY_FENCE_GATE)));
    public static final DeferredBlock<DoorBlock> DOOR=add("moonveil_door",()->new DoorBlock(BlockSetType.CHERRY,like(Blocks.CHERRY_DOOR)) {
        @Override public void appendHoverText(ItemStack stack,Item.TooltipContext ctx,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("tip.uwudimension.moonveil_door").withStyle(ChatFormatting.GRAY));
        }
    });
    public static final DeferredBlock<TrapDoorBlock> TRAPDOOR=add("moonveil_trapdoor",()->new TrapDoorBlock(BlockSetType.CHERRY,like(Blocks.CHERRY_TRAPDOOR)) {});
    public static final DeferredBlock<ButtonBlock> BUTTON=add("moonveil_button",()->new ButtonBlock(BlockSetType.CHERRY,30,like(Blocks.CHERRY_BUTTON)) {});
    public static final DeferredBlock<PressurePlateBlock> PLATE=add("moonveil_pressure_plate",()->new PressurePlateBlock(BlockSetType.CHERRY,like(Blocks.CHERRY_PRESSURE_PLATE)) {});
    private static final VoxelShape TABLE_SHAPE=furnitureShape(2,14,14,1), STOOL_SHAPE=furnitureShape(4,12,7,3);
    public static final DeferredBlock<Block> TABLE=add("moonveil_table",()->new Block(like(Blocks.CHERRY_PLANKS).noOcclusion()){
        @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext ctx){return TABLE_SHAPE;}
    });
    public static final DeferredBlock<Block> STOOL=add("moonveil_stool",()->new com.starfish_studios.another_furniture.block.SeatBlock(like(Blocks.CHERRY_PLANKS).noOcclusion()){
        @Override public float seatHeight(BlockState state){return .4F;}
        @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext ctx){return STOOL_SHAPE;}
    });
    public static final DeferredBlock<Block> BOOKSHELF=add("moonveil_bookshelf",()->new Block(like(Blocks.BOOKSHELF)));
    private MoonveilWood(){}

    private static BlockBehaviour.Properties like(Block block){return BlockBehaviour.Properties.ofFullCopy(block);}
    private static <B extends Block> DeferredBlock<B> add(String name,Supplier<B> make){
        var block=BLOCKS.register(name,make); FAMILY.add(block);
        ITEMS.register(name,()->block.get() instanceof DoorBlock ? new DoubleHighBlockItem(block.get(),new Item.Properties()) : new BlockItem(block.get(),new Item.Properties()){
            @Override public void appendHoverText(ItemStack stack,TooltipContext ctx,List<Component> lines,TooltipFlag flag){
                super.appendHoverText(stack,ctx,lines,flag);
                lines.add(Component.translatable("tip.uwudimension."+name).withStyle(ChatFormatting.GRAY));
            }
        });
        return block;
    }
    private static DeferredBlock<RotatedPillarBlock> bark(String name,Block source,Supplier<RotatedPillarBlock> stripped){
        return add(name,()->new RotatedPillarBlock(like(source)){
            @Override public BlockState getToolModifiedState(BlockState state,UseOnContext ctx,ItemAbility action,boolean simulate){
                if(action==ItemAbilities.AXE_STRIP && ctx.getItemInHand().canPerformAction(action))
                    return stripped.get().defaultBlockState().setValue(AXIS,state.getValue(AXIS));
                return super.getToolModifiedState(state,ctx,action,simulate);
            }
        });
    }
    private static VoxelShape furnitureShape(int inset,int far,int cap,int edge){
        VoxelShape result=Block.box(edge,cap,edge,16-edge,cap+2,16-edge);
        for(int x:new int[]{inset,far-2})for(int z:new int[]{inset,far-2})
            result=Shapes.or(result,Block.box(x,0,z,x+2,cap,z+2));
        return result.optimize();
    }
    public static void register(IEventBus bus){
        BLOCKS.register(bus);ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{
            if(e.getTabKey()==CreativeModeTabs.BUILDING_BLOCKS)FAMILY.forEach(b->e.accept(b.get()));
        });
        bus.addListener((FMLCommonSetupEvent e)->e.enqueueWork(()->{
            FireBlock fire=(FireBlock)Blocks.FIRE;
            for(var b:FAMILY){
                boolean bark=b==LOG||b==WOOD||b==STRIPPED_LOG||b==STRIPPED_WOOD;
                fire.setFlammable(b.get(),b==LEAVES?30:5,b==LEAVES?60:bark?5:20);
            }
        }));
    }
}
