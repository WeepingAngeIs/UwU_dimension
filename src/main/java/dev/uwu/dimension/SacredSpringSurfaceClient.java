package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Client-only color on the surface above a nearby sacred spring bed. */
@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class SacredSpringSurfaceClient {
    private static final ResourceLocation TEXTURE = UwUDimension.id("textures/misc/spring_surface.png");
    private static final RenderType SURFACE = RenderType.entityTranslucent(TEXTURE);
    private static final List<BlockPos> NEARBY = new ArrayList<>();
    private static net.minecraft.client.multiplayer.ClientLevel scannedLevel;
    private SacredSpringSurfaceClient() {}

    @SubscribeEvent public static void scan(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        var level=mc.level;
        var player=mc.player;
        if(level==null || player==null || !level.dimension().equals(ReefLife.REACH)) {
            NEARBY.clear(); scannedLevel=null; return;
        }
        if (scannedLevel!=level) { NEARBY.clear(); scannedLevel=level; }
        if (player.tickCount%20!=0) return;
        NEARBY.clear();
        BlockPos center=player.blockPosition();
        for (int dx=-10;dx<=10;dx++) for(int dz=-10;dz<=10;dz++) {
            BlockPos column=center.offset(dx,0,dz);
            if(!level.hasChunkAt(column)) continue;
            for(int dy=-8;dy<=4;dy++) {
                BlockPos pos=column.offset(0,dy,0);
                if(level.getBlockState(pos).is(SacredSpring.BED.get())) NEARBY.add(pos);
            }
        }
    }

    @SubscribeEvent public static void draw(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || NEARBY.isEmpty()) return;
        var mc=Minecraft.getInstance();
        var level=mc.level;
        if(level==null || level!=scannedLevel) return;
        Vec3 camera=event.getCamera().getPosition();
        PoseStack poses=event.getPoseStack();
        var buffers=mc.renderBuffers().bufferSource();
        VertexConsumer out=buffers.getBuffer(SURFACE);
        for(BlockPos pos:NEARBY) {
            if(!level.hasChunkAt(pos) || !level.getBlockState(pos).is(SacredSpring.BED.get())
                    || !level.getFluidState(pos.above()).is(FluidTags.WATER)
                    || !level.getFluidState(pos.above(2)).isEmpty()) continue;
            double surface=pos.getY()+1+level.getFluidState(pos.above()).getHeight(level,pos.above())+.003;
            if(camera.y<surface) continue;
            float x=(float)(pos.getX()-camera.x), y=(float)(surface-camera.y),
                    z=(float)(pos.getZ()-camera.z);
            vertex(out,poses,x,y,z,0,0); vertex(out,poses,x,y,z+1,0,1);
            vertex(out,poses,x+1,y,z+1,1,1); vertex(out,poses,x+1,y,z,1,0);
        }
        buffers.endBatch(SURFACE);
    }

    private static void vertex(VertexConsumer out,PoseStack poses,float x,float y,float z,float u,float v) {
        var pose=poses.last();
        out.addVertex(pose,x,y,z).setColor(0xffffffff).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose,0,1,0);
    }
}

