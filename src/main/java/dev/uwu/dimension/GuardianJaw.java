package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/** A visual-only replacement for the living female Guardian's single warm_face cube. */
public final class GuardianJaw {
    private final ModelPart root, head, face;
    private final List<Vertex> skin = new ArrayList<>();

    private record Point(float x, float y, float z) {
        Vector3f vector() { return new Vector3f(x,y,z); }
    }
    private record Vertex(Point p, float u, float v, Vector3f normal) {}
    private record Chart(Vertex a, Vertex b, Vertex d) {
        Vertex sample(Point p, Vector3f n) {
            Vector3f ab=b.p.vector().sub(a.p.vector()), ad=d.p.vector().sub(a.p.vector());
            Vector3f at=p.vector().sub(a.p.vector());
            float s=at.dot(ab)/ab.lengthSquared(), t=at.dot(ad)/ad.lengthSquared();
            return new Vertex(p, a.u+s*(b.u-a.u)+t*(d.u-a.u), a.v+s*(b.v-a.v)+t*(d.v-a.v), n);
        }
    }

    // Fraction from original top, then fraction of original half-width.
    // Cheeks stay broad beside the eyes. The mouth paints through row 28 of 32.
    private static final float[][] PROFILE={
        {0,.984f},{.22f,1},{.52f,1},{.66f,.990f},{.755f,.952f},{.824f,.875f},
        {.876f,.758f},{.912f,.585f},{.938f,.372f},{.949f,.195f}
    };
    private static final int FRONT=0, BACK=1, LEFT=2, RIGHT=3, TOP=4, BOTTOM=5;

    public GuardianJaw(ModelPart root) {
        this.root=root;
        head=root.getChild("head");
        face=head.getChild("warm_face");
        var source=new Recorder();
        face.getRandomCube(RandomSource.create(0)).compile(new PoseStack().last(),source,0,0,-1);
        if(source.corners.size()!=24) throw new IllegalArgumentException("Expected one six-faced living Guardian cube");
        Chart[] charts=new Chart[6];
        float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;
        for(int i=0;i<24;i+=4) {
            var a=source.corners.get(i);
            var n=a.normal;
            int side=n.z<-.5f?FRONT:n.z>.5f?BACK:n.x<-.5f?LEFT:n.x>.5f?RIGHT:n.y<-.5f?TOP:BOTTOM;
            charts[side]=new Chart(a,source.corners.get(i+1),source.corners.get(i+3));
            lo=Math.min(lo, a.p.x); hi=Math.max(hi,a.p.x);
        }
        if(Math.abs(lo+1)>.0001f || Math.abs(hi-1)>.0001f)
            throw new IllegalArgumentException("Unexpected native face chart unit");
        for(int row=0;row<PROFILE.length-1;row++) {
            var a=ring(row);var b=ring(row+1);
            // Back and front contours share a softly rounded horizontal section.
            for(int k=0;k<8;k++) {
                int next=(k+1)%8;
                int side=switch(k) {case 0->FRONT;case 1,2,3->RIGHT;case 4->BACK;default->LEFT;};
                patch(charts[side],a[k],b[k],b[next],a[next],a[k].x+a[next].x,0,a[k].z+a[next].z);
            }
        }
        for(int row : new int[]{0,PROFILE.length-1}) {
            var ring=ring(row);
            float sign=row==0?-1:1;
            var center=new Point(0,ring[0].y,0);
            for(int i=0;i<8;i++) {
                // Paired last positions create one triangle in the renderer's quad stream.
                patch(charts[row==0?TOP:BOTTOM],center,ring[i],ring[(i+1)%8],center,0,sign,0);
            }
        }
    }

    private static Point[] ring(int i) {
        float y=2*PROFILE[i][0]-1, w=PROFILE[i][1];
        float corner=Math.min(.072f,w*.14f);
        return new Point[]{
                new Point(-w+corner,y,-1),new Point(w-corner,y,-1),
                new Point(w,y,-.88f),new Point(w,y,.90f),new Point(w-corner,y,1),
                new Point(-w+corner,y,1),new Point(-w,y,.90f),new Point(-w,y,-.88f)};
    }

    private void patch(Chart chart, Point a, Point b, Point c, Point d, float ox,float oy,float oz) {
        Vector3f n=b.vector().sub(a.vector()).cross(c.vector().sub(a.vector())).normalize();
        Point[] vertices={a,b,c,d};
        if(n.dot(ox,oy,oz)<0) { n.negate(); vertices=new Point[]{a,d,c,b}; }
        for(Point point:vertices) skin.add(chart.sample(point,n));
    }

    /** Call in place of root.render with the same pose, entity skin, tint, light and overlay. */
    public void render(PoseStack poses, VertexConsumer output, int light, int overlay, int color) {
        boolean drawOriginal=face.skipDraw;
        face.skipDraw=true;
        try { root.render(poses,output,light,overlay,color); }
        finally { face.skipDraw=drawOriginal; }
        if(drawOriginal || !root.visible || !head.visible || !face.visible) return;
        poses.pushPose();
        try {
            root.translateAndRotate(poses); head.translateAndRotate(poses); face.translateAndRotate(poses);
            var matrix=poses.last();var position=new Vector3f();var normal=new Vector3f();
            for(var v:skin) {
                matrix.pose().transformPosition(v.p.x,v.p.y,v.p.z,position);
                matrix.transformNormal(v.normal,normal);
                output.addVertex(position.x,position.y,position.z,color,v.u,v.v,overlay,light,normal.x,normal.y,normal.z);
            }
        } finally { poses.popPose(); }
    }

    private static final class Recorder implements VertexConsumer {
        final List<Vertex> corners=new ArrayList<>();
        @Override public void addVertex(float x,float y,float z,int c,float u,float v,int o,int l,float nx,float ny,float nz) {
            corners.add(new Vertex(new Point(x,y,z),u,v,new Vector3f(nx,ny,nz)));
        }
        @Override public VertexConsumer addVertex(float x,float y,float z){throw new UnsupportedOperationException();}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){throw new UnsupportedOperationException();}
        @Override public VertexConsumer setUv(float u,float v){throw new UnsupportedOperationException();}
        @Override public VertexConsumer setUv1(int u,int v){throw new UnsupportedOperationException();}
        @Override public VertexConsumer setUv2(int u,int v){throw new UnsupportedOperationException();}
        @Override public VertexConsumer setNormal(float x,float y,float z){throw new UnsupportedOperationException();}
    }
}
