package dev.uwu.test;

import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.HeartLanterns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;

final class WorkshopCamera {
    static SeaWatch.View view(ServerPlayer p,String name) {
        var level=p.serverLevel();
        var base=GroveSites.arrival(level).offset(42,28,22);
        Vec3 at=Vec3.atBottomCenterOf(base);
        if(name.startsWith("crop-")) {
            var plants=new String[]{"sakura_rice","heartberry","glowginger","sweetbean","frostpetal","starfruit","vanilla_bean"};
            for(int row=0;row<plants.length;row++){
                var crop=(CropBlock)block(plants[row]+"_crop");
                for(int age=0;age<8;age++){
                    var soil=base.offset(age-4,0,row-3);
                    level.setBlockAndUpdate(soil,Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
                    level.setBlockAndUpdate(soil.above(),crop.getStateForAge(age));
                }
            }
            if(name.endsWith("detail"))return new SeaWatch.View(at.add(4.9,3,-5.4),at.add(2,1.3,-1),name,false);
            return new SeaWatch.View(at.add(-1,9,-5),at.add(0,1,0),name,false);
        }
        if(name.startsWith("lantern-")) {
            var source=base.offset(0,0,18);
            for(int x=-4;x<=4;x++)for(int z=-2;z<=3;z++) {
                level.setBlockAndUpdate(source.offset(x,0,z),block("sakura_deepslate").defaultBlockState());
                if(z==-2) for(int y=1;y<=6;y++)level.setBlockAndUpdate(source.offset(x,y,z),block("sakura_stone").defaultBlockState());
            }
            for(int side:new int[]{-1,1}){
                BlockPos hook=source.offset(side,5,0);
                level.setBlockAndUpdate(hook,block("sakura_deepslate").defaultBlockState());
                level.setBlockAndUpdate(hook.below(),block("silver_chain").defaultBlockState());
                level.setBlockAndUpdate(hook.below(2),(side<0?HeartLanterns.HEART:HeartLanterns.DUSK).get()
                    .defaultBlockState().setValue(LanternBlock.HANGING,true));
            }
            Vec3 focus=Vec3.atCenterOf(source.above(3));
            return new SeaWatch.View(focus.add(name.endsWith("side")?2.25:.12,.03,3.2),focus,name,false);
        }
        String[] rack = {"blossom_greatsword","ribbonhare_scepter","bellheart_axe","lockkeeper_axe","ripple_bow","vesper_scythe","axolotl_whip","frost_whip",
                "rift_whip","satin_whip","lantern_spear","tidelace_trident","rose_dagger","blue_hour_wand","counter_iron_longsword","counter_iron_twinblade"};
        var wall=base.offset(0,3,32);
        for(int i=0;i<rack.length;i++){
            BlockPos back=wall.offset(i%8-4,-i/8,0);
            level.setBlockAndUpdate(back,block("sakura_deepslate").defaultBlockState());
            var pos=back.south();
            var frame=new ItemFrame(level,pos,Direction.SOUTH);
            frame.setItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("uwudimension:"+rack[i]))));
            frame.setInvisible(true);level.addFreshEntity(frame);
        }
        Vec3 focus=Vec3.atCenterOf(wall).add(-.5,-.5,.6);
        return new SeaWatch.View(focus.add(0,.2,5),focus,name,false);
    }
    private static Block block(String id){
        var key=ResourceLocation.parse("uwudimension:"+id);
        return BuiltInRegistries.BLOCK.getOptional(key).orElseThrow(()->new AssertionError("Missing "+key));
    }
}

