package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid="uwudimension",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SakuraGrassColors {
    private SakuraGrassColors(){}
    public static int color(BlockAndTintGetter world,BlockPos pos){
        if(pos==null)return 0xB8DDE8;
        double blush=(Math.sin(pos.getX()*.033+Math.sin(pos.getZ()*.034))*Math.cos(pos.getZ()*.027)+1)*.5;
        // Vanilla's grass-color cache also handles biome blending on chunk boundaries.
        int base=world.getBlockTint(pos,net.minecraft.client.renderer.BiomeColors.GRASS_COLOR_RESOLVER);
        int red=(base>>16)&255, green=(base>>8)&255, blue=base&255;
        double purple=blue>green ? Math.max(0,(red-green)/24.0) : 0;
        double t=Math.min(1,Math.max(0,(blush-.32)/.68)+purple*.32);
        int r=(int)(173+35*t),g=(int)(219-21*t),b=(int)(231+8*t);
        return (r<<16)|(g<<8)|b;
    }
    @SubscribeEvent public static void blocks(RegisterColorHandlersEvent.Block event){
        event.register((state,world,pos,index)->color(world,pos),SakuraMaterials.GRASS.get());
    }
    @SubscribeEvent public static void items(RegisterColorHandlersEvent.Item event){
        event.register((stack,index)->color(null,null),SakuraMaterials.GRASS.get());
    }
}
