package dev.uwu.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.uwu.dimension.Arsenal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Vector3f;

/** Observes the actual rendered bow in the player's grip. Camera motion never changes the pose. */
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class CupidAngles {
    private static final String[] ANGLES={"front","side","edge"};
    private static final BakedModel[] MEASURED=new BakedModel[4];
    private static final double[] ORBIT={0,45,84};
    private static boolean active, restoring, hiddenGui, readyFrame;
    private static int shot, settledFrames, ticks;
    private static CameraType previousCamera;
    private static volatile ItemStack oldMain;
    private static volatile boolean restored;

    static void begin(Minecraft mc) {
        if (active) throw new IllegalStateException("Cupid angle session already active");
        previousCamera=mc.options.getCameraType();
        hiddenGui=mc.options.hideGui;
        active=true;
        restoring=false;
        restored=false;
        oldMain=null;
        shot=settledFrames=ticks=0;
        readyFrame=false;
        mc.options.hideGui=true;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        var server=mc.getSingleplayerServer();
        var id=mc.player.getUUID();
        server.execute(()->{
            var player=server.getPlayerList().getPlayer(id);
            oldMain=player.getMainHandItem().copy();
            player.stopUsingItem();
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get()));
            player.containerMenu.broadcastChanges();
        });
    }

    static boolean tick(Minecraft mc) {
        if (!active) return true;
        if (++ticks>750) throw new AssertionError("Timed out photographing Cupid angle "+shot);
        if (shot==6) {
            if (!restoring) {
                restoring=true;
                mc.player.stopUsingItem();
                mc.options.keyUse.setDown(false);
                mc.options.setCameraType(previousCamera);
                mc.options.hideGui=hiddenGui;
                var server=mc.getSingleplayerServer();
                var id=mc.player.getUUID();
                server.execute(()->{
                    var player=server.getPlayerList().getPlayer(id);
                    player.stopUsingItem();
                    player.setItemInHand(InteractionHand.MAIN_HAND,oldMain);
                    player.containerMenu.broadcastChanges();
                    restored=true;
                });
            }
            if (!restored || !ItemStack.matches(mc.player.getMainHandItem(),oldMain)) return false;
            active=false;
            System.out.println("CUPID THIRD-PERSON ANGLES PASS rest and full, front, side and 84-degree edge");
            return true;
        }
        if (detached()) {
            mc.options.hideGui=true;
            if (shot>=3) {
                mc.options.keyUse.setDown(true);
                if (!mc.player.isUsingItem()) mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            }
        }
        return false;
    }

    static boolean detached() {
        if (!active || shot>=6) return false;
        var mc=Minecraft.getInstance();
        return mc.player!=null && mc.player.getMainHandItem().is(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get());
    }

    static RelicPortraits.Lens lens(float partial) {
        if (!detached()) return null;
        var mc=Minecraft.getInstance();
        var player=mc.player;
        PlayerRenderer renderer=(PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(player);
        var held=mc.getItemRenderer().getModel(player.getMainHandItem(),mc.level,player,0);
        // Replicate the standing PlayerRenderer and ItemInHandLayer transforms, using the
        // animated arm last drawn by the game. This places the camera relative to the bow itself.
        var pose=new PoseStack();
        float scale=player.getScale();
        pose.scale(scale,scale,scale);
        pose.mulPose(Axis.YP.rotationDegrees(180-Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot)));
        pose.scale(-.9375F,-.9375F,.9375F);
        pose.translate(0,-1.501,0);
        var arm=player.getMainArm();
        boolean left=arm==HumanoidArm.LEFT;
        renderer.getModel().translateToHand(arm,pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.translate((left?-1:1)/16F,.125,-.625);
        held.applyTransform(left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,pose,left);
        var transform=pose.last().pose();
        var feet=player.getPosition(partial);
        var focus=feet.add(new Vec3(transform.transformPosition(new Vector3f())));
        var normal=new Vec3(transform.transformDirection(new Vector3f(0,0,1))).normalize();
        var side=new Vec3(transform.transformDirection(new Vector3f(1,0,0))).normalize();
        Vec3 away=focus.subtract(feet.add(0,1.2,0));
        if (normal.dot(away)<0) normal=normal.scale(-1);
        if (side.dot(away)<0) side=side.scale(-1);
        double angle=Math.toRadians(ORBIT[shot%3]);
        var direction=normal.scale(Math.cos(angle)).add(side.scale(Math.sin(angle)));
        var eye=focus.add(direction.scale(1.42));
        String stage=held.getParticleIcon().contents().name().getPath();
        readyFrame=shot<3 ? !player.isUsingItem() && stage.equals("item/cupid_bow")
                : player.isUsingItem() && player.getTicksUsingItem()>=20 && stage.equals("item/cupid_bow_draw2");
        return new RelicPortraits.Lens(eye,focus);
    }

    @SubscribeEvent public static void photo(RenderFrameEvent.Post event) {
        if (!active || shot>=6 || !readyFrame) return;
        if (++settledFrames<4) return;
        var mc=Minecraft.getInstance();
        String filename="cupid-third-"+(shot<3?"rest":"full")+"-"+ANGLES[shot%3]+".png";
        Screenshot.grab(mc.gameDirectory,filename,mc.getMainRenderTarget(),msg->System.out.println(msg.getString()));
        shot++;
        settledFrames=0;
        readyFrame=false;
    }

    static void measure(BakedModel model,int stage) {
        if (MEASURED[stage]==model) return;
        String stem="uwudimension:item/cupid_bow"+(stage==0?"":"_draw"+(stage-1));
        List<BakedQuad> quads=new ArrayList<>(model.getQuads(null,null,RandomSource.create(42)));
        for (Direction face: Direction.values()) quads.addAll(model.getQuads(null,face,RandomSource.create(42)));
        for (var quad: quads) {
            String sprite=quad.getSprite().contents().name().toString();
            if (!sprite.equals(stem+"_body") && !sprite.equals(stem+"_string"))
                throw new AssertionError("Unexpected Cupid baked geometry in stage "+stage+": "+sprite);
        }
        double body=depth(quads,stem+"_body",7.75,8.25);
        double string=depth(quads,stem+"_string",7.9375,8.0625);
        for (ItemDisplayContext hand: List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
            var pose=new PoseStack();
            boolean left=hand==ItemDisplayContext.FIRST_PERSON_LEFT_HAND || hand==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            model.applyTransform(hand,pose,left);
            float scale=pose.last().pose().transformDirection(new Vector3f(0,0,1)).length();
            double expected=hand.firstPerson()?.28:.9;
            if (Math.abs(scale-expected)>.0001) throw new AssertionError(hand+" changed depth scale to "+scale);
            System.out.println("CUPID BAKED DEPTH stage="+stage+" hand="+hand+" baseline="+scale+
                    " body="+(body*scale)+" string="+(string*scale)+" units per 16");
        }
        MEASURED[stage]=model;
    }

    private static double depth(List<BakedQuad> quads,String sprite,double low,double high) {
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        int count=0;
        for (var quad:quads) {
            if (!quad.getSprite().contents().name().toString().equals(sprite)) continue;
            int[] vertices=quad.getVertices();
            for (int offset=2;offset<vertices.length;offset+=vertices.length/4) {
                double z=Float.intBitsToFloat(vertices[offset])*16D;
                min=Math.min(min,z); max=Math.max(max,z);
            }
            count++;
        }
        if (count<3 || Math.abs(min-low)>.0001 || Math.abs(max-high)>.0001)
            throw new AssertionError(sprite+" has "+count+" baked quads, Z "+min+".."+max+" expected "+low+".."+high);
        return max-min;
    }
}
