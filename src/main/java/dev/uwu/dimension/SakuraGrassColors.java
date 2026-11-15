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
        return color(world,pos,false);
    }
    public static int color(BlockAndTintGetter world,BlockPos pos,boolean dusk){
        if(world==null||pos==null)return dusk?0x625775:0x4C919A;
        double blush=(Math.sin(pos.getX()*.033+Math.sin(pos.getZ()*.034))*Math.cos(pos.getZ()*.027)+1)*.5;
        // Vanilla's grass-color cache also handles biome blending on chunk boundaries.
        int base=world.getBlockTint(pos,net.minecraft.client.renderer.BiomeColors.GRASS_COLOR_RESOLVER);
        int red=(base>>16)&255, green=(base>>8)&255, blue=base&255;
        double purple=blue>green ? Math.max(0,(red-green)/24.0) : 0;
        double t=Math.min(1,Math.max(0,(blush-.32)/.68)+purple*.32);
        int r=(int)((dusk?95:74)+(dusk?8:12)*t),g=(int)((dusk?90:148)-(dusk?6:7)*t),b=(int)((dusk?115:154)+6*t);
        return (r<<16)|(g<<8)|b;
    }
    @SubscribeEvent public static void blocks(RegisterColorHandlersEvent.Block event){
        event.register((state,world,pos,index)->color(world,pos),SakuraMaterials.GRASS.get());
        event.register((state,world,pos,index)->color(world,pos,true),SakuraMaterials.DUSK_GRASS.get());
    }
    @SubscribeEvent public static void items(RegisterColorHandlersEvent.Item event){
        event.register((stack,index)->color(null,null),SakuraMaterials.GRASS.get());
        event.register((stack,index)->color(null,null,true),SakuraMaterials.DUSK_GRASS.get());
    }
}
