package dev.uwu.dimension.traversal.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/** The skin/sleeve/armor normally emit before the held-item layer has its final matrix.
 * Delay ONLY the real main arm's vertices for this actor during a Vesper plant. No
 * model, layer or animation callback is evaluated twice. Other body parts draw as usual. */
public final class TraversalWrist implements MultiBufferSource {
    private static final Map<ModelPart,HumanoidArm> arms=new WeakHashMap<>();
    private static TraversalWrist current;
    private final Player actor;
    private final MultiBufferSource target;
    private final Map<RenderType,List<Emission>> commands=new LinkedHashMap<>();
    private final Vector3f delta=new Vector3f();
    private int armDepth, vertices;

    private interface Emission { void emit(VertexConsumer out,Vector3f shift); }
    private TraversalWrist(Player actor,MultiBufferSource target) { this.actor=actor; this.target=target; }
    public static void register(ModelPart right,ModelPart left) { arms.put(right,HumanoidArm.RIGHT); arms.put(left,HumanoidArm.LEFT); }
    public static boolean enter(ModelPart part) {
        if(current==null || arms.get(part)!=current.actor.getMainArm()) return false;
        current.armDepth++; return true;
    }
    public static void leave() { current.armDepth--; }
    public static boolean enterFigura(Object part,Object renderer) {
        if(current==null) return false;
        try {
            if(renderer.getClass().getField("bufferSource").get(renderer)!=current) return false;
            var kind=part.getClass().getField("parentType");
            var parent=part.getClass().getField("parent");
            for(Object bone=part; bone!=null; bone=parent.get(bone)) {
                String name=((Enum<?>)kind.get(bone)).name();
                if(name.equals(current.actor.getMainArm()==HumanoidArm.RIGHT?"RightArm":"LeftArm")) {
                    current.armDepth++; return true;
                }
                // A separately attached child (held item, wing, opposing limb...) has its own anchor.
                if(!name.equals("None")) break;
            }
        } catch(ReflectiveOperationException ignored) { /* Unknown avatar pipeline retains its original pose. */ }
        return false;
    }

    public static Consumer<VertexConsumer> figuraSubmission(Consumer<VertexConsumer> submit) {
        var owner=current;
        if(owner==null || owner.armDepth==0) return submit;
        return out->{
            if(current!=owner) { submit.accept(out); return; }
            owner.armDepth++;
            try { submit.accept(out); } finally { owner.armDepth--; }
        };
    }
    public static boolean hasVertices(Player actor) { return current!=null && current.actor==actor && current.vertices>0; }
    public static void shift(Player actor,Vector3f offset) { if(current!=null && current.actor==actor) current.delta.set(offset); }

    public static void render(Player actor,MultiBufferSource buffers,java.util.function.Consumer<MultiBufferSource> renderer) {
        var previous=current;
        var wrist=new TraversalWrist(actor,buffers);
        current=wrist;
        try {
            renderer.accept(wrist);
            for(var entry:wrist.commands.entrySet()) {
                var out=buffers.getBuffer(entry.getKey());
                entry.getValue().forEach(command->command.emit(out,wrist.delta));
            }
        } finally { current=previous; }
    }

    @Override public VertexConsumer getBuffer(RenderType type) {
        var original=target.getBuffer(type);
        return new VertexConsumer() {
            private List<Emission> deferred;
            @Override public VertexConsumer addVertex(float x,float y,float z) {
                deferred=armDepth>0 && current==TraversalWrist.this ? commands.computeIfAbsent(type,k->new ArrayList<>()) : null;
                if(deferred!=null) { vertices++; deferred.add((out,d)->out.addVertex(x+d.x,y+d.y,z+d.z)); }
                else original.addVertex(x,y,z);
                return this;
            }
            @Override public VertexConsumer setColor(int r,int g,int b,int a) {
                if(deferred!=null) deferred.add((out,d)->out.setColor(r,g,b,a)); else original.setColor(r,g,b,a); return this;
            }
            @Override public VertexConsumer setUv(float u,float v) {
                if(deferred!=null) deferred.add((out,d)->out.setUv(u,v)); else original.setUv(u,v); return this;
            }
            @Override public VertexConsumer setUv1(int u,int v) {
                if(deferred!=null) deferred.add((out,d)->out.setUv1(u,v)); else original.setUv1(u,v); return this;
            }
            @Override public VertexConsumer setUv2(int u,int v) {
                if(deferred!=null) deferred.add((out,d)->out.setUv2(u,v)); else original.setUv2(u,v); return this;
            }
            @Override public VertexConsumer setNormal(float x,float y,float z) {
                if(deferred!=null) deferred.add((out,d)->out.setNormal(x,y,z)); else original.setNormal(x,y,z); return this;
            }
            @Override public VertexConsumer misc(VertexFormatElement field,int... values) {
                if(deferred!=null) { int[] copy=values.clone(); deferred.add((out,d)->out.misc(field,copy)); }
                else original.misc(field,values); return this;
            }
        };
    }
}
