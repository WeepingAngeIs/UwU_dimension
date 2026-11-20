package dev.uwu.dimension.traversal.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.Arsenal;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Render-thread state, scoped to one physical world render. No pose survives the end of that render. */
@EventBusSubscriber(modid="uwudimension", value=Dist.CLIENT)
public final class TraversalRenderPass {
    private record Held(ItemStack stack, ItemDisplayContext context, BakedModel model, Matrix4f relative) {}
    private static final Map<Player,Held> held = new IdentityHashMap<>();
    private static final InteractionHand MAIN = InteractionHand.MAIN_HAND;
    private static final Vec3 WRAP = new Vec3(10.5/32., 1-22.5/32., .5);
    private static TraversalAttachmentFrame frame;
    private static Matrix4f worldView, projection;
    private static Camera camera;
    private static float partial;
    private static boolean active, physicalHand, savedDepth;
    private static boolean irisHand, shadow;
    private static Held earlyHand;
    private static Matrix4f earlyProjection;
    private static Vec3 localTip;
    private static LivingEntity subject;

    private TraversalRenderPass() {}
    public static void begin() { end(); active=true; }
    public static void end() {
        active=false; frame=null; worldView=null; projection=null; camera=null; subject=null;
        held.clear(); localTip=null; savedDepth=false; physicalHand=false; irisHand=false; shadow=false;
        earlyHand=null; earlyProjection=null;
    }
    public static void shaderHand(boolean value) { irisHand=value; }
    public static void shadow(boolean value) { shadow=value; }

    @SubscribeEvent
    public static void stage(RenderLevelStageEvent event) {
        if (!active || shadow) return;
        var stage=event.getStage();
        if (stage==RenderLevelStageEvent.Stage.AFTER_SKY) {
            var mc=Minecraft.getInstance();
            camera=event.getCamera();
            if (mc.level==null || camera.getEntity()==null) return;
            worldView=new Matrix4f(event.getModelViewMatrix()).mul(event.getPoseStack().last().pose());
            projection=new Matrix4f(event.getProjectionMatrix());
            partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
            frame=new TraversalAttachmentFrame(mc.level,camera.getEntity(),!camera.isDetached(),camera.getPosition(),worldView,projection);
            held.clear();
        }
        if (stage==RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            TraversalHeldRendering.render(TraversalClientSession.view(),event,(actor,t,draw)->{
                if (earlyHand!=null && frame.viewsHandOf(actor))
                    return draw.firstPerson(MAIN,earlyHand.context,earlyHand.stack,earlyHand.model,earlyHand.relative,earlyProjection);
                var h=held.get(actor);
                return h==null?null:draw.thirdPerson(MAIN,h.context,h.stack,h.model,h.relative);
            });
        }
    }

    public static LivingEntity subject(LivingEntity next) {
        LivingEntity before=subject;
        subject=next;
        return before;
    }

    public static boolean planting(Player actor) {
        return frame!=null && !shadow && actor.level()==Minecraft.getInstance().level &&
            actor.getMainHandItem().is(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get()) &&
            TraversalClientSession.view().bladeContact(actor.getId(),partial)!=null;
    }

    public static void beforeHand() {
        if (frame==null) return;
        physicalHand=true;
        var mc=Minecraft.getInstance();
        if (earlyHand==null && camera.getEntity() instanceof Player p && frame.viewsHandOf(p) && !mc.options.hideGui &&
            TraversalClientSession.view().hasLash(p,partial)) savedDepth=TraversalHandDepth.capture();
    }

    public static void afterHand() {
        if (savedDepth && localTip!=null && camera.getEntity() instanceof Player p)
            TraversalHandDepth.draw(worldView,projection,()->TraversalClientSession.view().handLash(p,partial,localTip,camera.getPosition()));
        savedDepth=false; localTip=null;
    }

    public static void mesh(ItemStack stack, ItemDisplayContext context, BakedModel model, PoseStack pose) {
        if (frame==null || shadow || !(subject instanceof Player actor) ||
            !(context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context.firstPerson())) return;
        var view=TraversalClientSession.view();
        boolean snow=stack.is(Arsenal.WEAPONS.get(Arsenal.Relic.FROST_WHIP).get()) && view.hasLash(actor,partial);
        if (!snow && !(stack.is(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get()) && planting(actor))) return;
        boolean first=context.firstPerson();
        if (first && (!(physicalHand || irisHand) || !frame.viewsHandOf(actor))) return;
        if (!first && physicalHand) return;
        Matrix4f gpu=new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f eye=new Matrix4f(gpu).mul(pose.last().pose());
        if (!eye.isFinite() || !eye.isAffine() || Math.abs(eye.determinant())<1e-12) return;
        Matrix4f out=first?eye:new Matrix4f(worldView).invert().mul(eye);
        if (snow) {
            if (first && irisHand && TraversalLandmarks.snowkiss(actor,MAIN,context,stack,model)!=null) {
                // Iris's actual cutout-hand pass precedes world translucency. No depth transfer is needed.
                earlyHand=new Held(stack,context,model,eye);
                earlyProjection=new Matrix4f(RenderSystem.getProjectionMatrix());
            } else if (first && savedDepth) {
                Vec3 tip=TraversalLandmarks.snowkiss(actor,MAIN,context,stack,model);
                localTip=tip==null?null:frame.resolve(actor,frame.firstPerson(actor,MAIN,context,stack,true,tip,eye,RenderSystem.getProjectionMatrix()));
            } else if (!first && TraversalLandmarks.snowkiss(actor,MAIN,context,stack,model)!=null) {
                held.put(actor,new Held(stack,context,model,out));
            }
            return;
        }
        // The vanilla first-person item draw has no separate arm. An observed third-person arm
        // must be paired with this actor's item before applying any shift.
        if (!first && !TraversalWrist.hasVertices(actor)) return;
        var draw=new TraversalHeldRendering.VesperDraw(actor,MAIN,context,stack,model,WRAP);
        var plant=first?TraversalHeldRendering.firstPersonVesper(view,draw,partial,frame,eye,RenderSystem.getProjectionMatrix())
                       :TraversalHeldRendering.thirdPersonVesper(view,draw,partial,camera.getPosition(),out);
        if (plant==null) return;
        Matrix4f outputToBuffer=gpu.invert();
        if (!first) outputToBuffer.mul(worldView);
        Matrix4f corrected=outputToBuffer.mul(plant.correctedMesh(),new Matrix4f());
        if (!corrected.isFinite()) return;
        Vec3 delta=plant.gripDelta();
        TraversalWrist.shift(actor,outputToBuffer.transformDirection((float)delta.x,(float)delta.y,(float)delta.z,new Vector3f()));
        // PoseStack.mulPose updates the normal matrix as well as positions, retaining its current parent.
        Matrix4f localCorrection=pose.last().pose().invert(new Matrix4f()).mul(corrected);
        pose.mulPose(localCorrection);
    }
}
