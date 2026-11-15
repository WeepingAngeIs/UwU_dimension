package dev.uwu.test;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.*;
import java.util.*;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Actual isolated-server growth, preservation and naturally generated landmark checks. */
final class GiantSakuraProof {
    private static BlockPos origin;
    private static int stage;
    private static long deadline;
    private static final List<net.minecraft.world.level.ChunkPos> forced=new ArrayList<>();
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError("Giant sakura: "+why);}
    private static void force(ServerLevel level,BlockPos p){
        for(int x=(p.getX()-17)>>4;x<=(p.getX()+17)>>4;x++)for(int z=(p.getZ()-17)>>4;z<=(p.getZ()+17)>>4;z++){
            if(level.setChunkForced(x,z,true))forced.add(new net.minecraft.world.level.ChunkPos(x,z));
        }
    }
    private static boolean loaded(ServerLevel level,BlockPos p){
        for(int x=(p.getX()-17)>>4;x<=(p.getX()+17)>>4;x++)for(int z=(p.getZ()-17)>>4;z<=(p.getZ()+17)>>4;z++)
            if(!level.hasChunkAt(new BlockPos(x<<4,p.getY(),z<<4)))return false;
        return true;
    }
    private static void plant(ServerLevel level,BlockPos p){
        for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++)level.setBlockAndUpdate(p.offset(x,-1,z),Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(p,GiantSakuraSapling.SAPLING.get().defaultBlockState().setValue(SaplingBlock.STAGE,1));
    }
    private static void bonemeal(ServerLevel level,BlockPos p,int attempts){
        for(int i=0;i<attempts&&level.getBlockState(p).is(GiantSakuraSapling.SAPLING.get());i++)
            BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL,64),level,p);
    }
    private static void originalSapling(ServerLevel level,BlockPos p,String context){
        var state=level.getBlockState(p);
        check(state.is(GiantSakuraSapling.SAPLING.get())&&state.getValue(SaplingBlock.STAGE)==1,context+" lost original staged sapling");
    }
    private static void recipe(ServerLevel level){
        var grid=CraftingInput.of(3,2,List.of(new ItemStack(Items.CHERRY_SAPLING),new ItemStack(Items.CHERRY_SAPLING),new ItemStack(Items.BONE_MEAL),
                new ItemStack(Items.CHERRY_SAPLING),new ItemStack(Items.CHERRY_SAPLING),ItemStack.EMPTY));
        var id=ResourceLocation.fromNamespaceAndPath("uwudimension","giant_sakura_sapling");
        var recipe=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).orElseThrow();
        check(recipe.id().equals(id)&&recipe.value().assemble(grid,level.registryAccess()).is(GiantSakuraSapling.SAPLING.get().asItem()),"ordinary crafting recipe");
        var visitor=CounterProof.player(level,new GameProfile(UUID.fromString("4e1f55c9-f3e9-4d03-971d-13ab779beff1"),"SakuraGardener"));
        level.addNewPlayer(visitor);
        try {
            check(!visitor.getRecipeBook().contains(id),"recipe started unlocked");
            var stack=new ItemStack(Items.CHERRY_SAPLING);visitor.getInventory().setItem(0,stack);
            CriteriaTriggers.INVENTORY_CHANGED.trigger(visitor,visitor.getInventory(),stack);
            check(visitor.getRecipeBook().contains(id),"inventory criterion did not unlock recipe book");
            for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++)level.setBlockAndUpdate(origin.offset(x,-1,z),Blocks.DIRT.defaultBlockState());
            visitor.moveTo(origin.getX()+3.5,origin.getY(),origin.getZ()+.5,0,0);
            var sapling=new ItemStack(GiantSakuraSapling.SAPLING.get());
            visitor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,sapling);
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atBottomCenterOf(origin),
                    Direction.UP,origin.below(),false);
            check(sapling.useOn(new net.minecraft.world.item.context.UseOnContext(visitor,net.minecraft.world.InteractionHand.MAIN_HAND,hit)).consumesAction()
                    && level.getBlockState(origin).is(GiantSakuraSapling.SAPLING.get())
                    && level.getBlockState(origin).getValue(SaplingBlock.STAGE)==0,"survival sapling planting");
        } finally {visitor.discard();}
        var drops=Block.getDrops(GiantSakuraSapling.SAPLING.get().defaultBlockState(),level,origin,null,null,new ItemStack(Items.DIAMOND_AXE));
        check(drops.stream().anyMatch(s->s.is(GiantSakuraSapling.SAPLING.get().asItem())),"sapling self drop");
    }
    private static void preserved(ServerLevel level){
        plant(level,origin);
        var chestPos=origin.above(4);var blockPos=origin.offset(1,12,1);
        level.setBlockAndUpdate(chestPos,Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(blockPos,Blocks.OBSIDIAN.defaultBlockState());
        var chest=(ChestBlockEntity)level.getBlockEntity(chestPos);check(chest!=null,"chest block entity");
        chest.setItem(0,new ItemStack(Items.DIAMOND,3));
        bonemeal(level,origin,50);originalSapling(level,origin,"blocked growth");
        check(level.getBlockState(chestPos).is(Blocks.CHEST)&&level.getBlockEntity(chestPos) instanceof ChestBlockEntity c
                &&c.getItem(0).is(Items.DIAMOND)&&c.getItem(0).getCount()==3,"chest contents changed");
        check(level.getBlockState(blockPos).is(Blocks.OBSIDIAN),"player block overwritten");
        level.setBlockAndUpdate(chestPos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(blockPos,Blocks.AIR.defaultBlockState());
        var soil=origin.offset(-1,-1,-1);level.setBlockAndUpdate(soil,Blocks.STONE.defaultBlockState());
        bonemeal(level,origin,35);originalSapling(level,origin,"missing support");
        check(level.getBlockState(soil).is(Blocks.STONE),"unsupported soil changed");
        level.setBlockAndUpdate(soil,Blocks.DIRT.defaultBlockState());
        var border=level.getWorldBorder();double cx=border.getCenterX(),cz=border.getCenterZ(),size=border.getSize();
        try {border.setCenter(origin.getX(),origin.getZ());border.setSize(6);bonemeal(level,origin,35);originalSapling(level,origin,"border refusal");}
        finally {border.setCenter(cx,cz);border.setSize(size);}
        BlockPos high=origin.atY(level.getMaxBuildHeight()-1);plant(level,high);
        bonemeal(level,high,35);originalSapling(level,high,"build-height refusal");
        level.setBlockAndUpdate(high,Blocks.AIR.defaultBlockState());
        for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++)level.setBlockAndUpdate(high.offset(x,-1,z),Blocks.AIR.defaultBlockState());
    }
    private static void connectivity(ServerLevel level,BlockPos p,String label){
        for(int y=0;y<17;y++)for(int x=0;x<2;x++)for(int z=0;z<2;z++)
            check(level.getBlockState(p.offset(x,y,z)).is(Blocks.CHERRY_LOG),label+" missing 2x2 core y="+y);
        for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++){
            check(level.getBlockState(p.offset(x,0,z)).is(Blocks.CHERRY_LOG),label+" missing flared base");
            check(level.getBlockState(p.offset(x,-1,z)).is(net.minecraft.tags.BlockTags.DIRT),label+" unsupported base");
        }
        Set<BlockPos> logs=new HashSet<>();int leaves=0;
        for(int x=-17;x<=17;x++)for(int z=-17;z<=17;z++)for(int y=0;y<=42;y++){
            var at=p.offset(x,y,z);var state=level.getBlockState(at);
            if(state.is(Blocks.CHERRY_LOG))logs.add(at);
            if(state.is(Blocks.CHERRY_LEAVES))leaves++;
        }
        check(logs.size()>=90&&leaves>300,label+" sparse crown/logs "+logs.size()+"/"+leaves);
        Set<BlockPos> reached=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(p);reached.add(p);
        while(!queue.isEmpty()){
            var here=queue.removeFirst();
            for(Direction dir:Direction.values()){
                BlockPos next=here.relative(dir);
                if(logs.contains(next)&&reached.add(next))queue.add(next);
            }
        }
        check(reached.size()==logs.size(),label+" disconnected branches "+reached.size()+"/"+logs.size());
        System.out.println("GIANT SAKURA CONNECTIVITY PASS "+label+" logs="+logs.size()+" leaves="+leaves);
    }
    private static void clearTree(ServerLevel level,BlockPos p){
        for(int x=-17;x<=17;x++)for(int z=-17;z<=17;z++)for(int y=0;y<=42;y++){
            var at=p.offset(x,y,z);var s=level.getBlockState(at);
            if(s.is(Blocks.CHERRY_LOG)||s.is(Blocks.CHERRY_LEAVES))level.setBlock(at,Blocks.AIR.defaultBlockState(),2);
        }
    }
    private static void unloaded(ServerLevel level){
        BlockPos p=new BlockPos(16008,origin.getY(),16008);level.getChunkAt(p);plant(level,p);
        BlockPos missing=null;
        for(int x=(p.getX()-17)>>4;x<=(p.getX()+17)>>4;x++)for(int z=(p.getZ()-17)>>4;z<=(p.getZ()+17)>>4;z++){
            var q=new BlockPos(x<<4,p.getY(),z<<4);if(!level.hasChunkAt(q))missing=q;
        }
        check(missing!=null,"unloaded-chunk fixture unexpectedly loaded");
        bonemeal(level,p,20);originalSapling(level,p,"unloaded footprint");
        check(!level.hasChunkAt(missing),"sapling forced an unloaded neighbor chunk");
    }
    static boolean tick(ServerLevel level)throws Exception {
        if(stage==0){
            origin=new BlockPos(8,level.getMaxBuildHeight()-46,8);force(level,origin);
            level.setDayTime(6000);deadline=level.getGameTime()+400;stage=1;return false;
        }
        if(stage==1){
            if(!loaded(level,origin)){check(level.getGameTime()<deadline,"test chunks did not load");return false;}
            recipe(level);preserved(level);bonemeal(level,origin,100);
            check(level.getBlockState(origin).is(Blocks.CHERRY_LOG),"actual bone meal did not grow tree");
            connectivity(level,origin,"bonemeal");clearTree(level,origin);plant(level,origin);
            // Wait for the lighting engine to clear the previous canopy's shadow.
            level.setBlockAndUpdate(origin.offset(-2,0,0),Blocks.GLOWSTONE.defaultBlockState());
            deadline=level.getGameTime()+30;stage=2;return false;
        }
        if(stage==2){
            if(level.getGameTime()<deadline)return false;
            check(level.getMaxLocalRawBrightness(origin.above())>=9,"random-tick fixture is too dark");
            // Invoke vanilla's actual random-tick handler with a stable seed, not a fabricated feature call.
            var random=RandomSource.create(12345);
            for(int i=0;i<5000&&level.getBlockState(origin).is(GiantSakuraSapling.SAPLING.get());i++)
                level.getBlockState(origin).randomTick(level,origin,random);
            check(level.getBlockState(origin).is(Blocks.CHERRY_LOG),"random tick did not grow tree");
            connectivity(level,origin,"random tick");unloaded(level);
            System.out.println("GIANT SAKURA SAPLING PASS recipe-book/craft, drops, bonemeal/random, blockers, support, border, height and unloaded chunks");
            stage=3;return false;
        }
        if(stage==3){
            GiantSakuraNaturalProof.run(level);
            for(var pos:forced)level.setChunkForced(pos.x,pos.z,false);
            System.out.println("GIANT SAKURA ALL SERVER CHECKS PASSED");return true;
        }
        throw new AssertionError("Unexpected giant sakura test stage");
    }
}
