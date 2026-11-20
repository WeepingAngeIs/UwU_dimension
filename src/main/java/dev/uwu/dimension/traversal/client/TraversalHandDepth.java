package dev.uwu.dimension.traversal.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Depth test the late, current-frame first-person lash against terrain, then put it behind hand pixels. */
final class TraversalHandDepth {
    private static TextureTarget ribbon;
    private TraversalHandDepth() {}
    static boolean capture() {
        var main=Minecraft.getInstance().getMainRenderTarget();
        // A shader-owned framebuffer may use another sample count, viewport or depth convention.
        // Never blit that framebuffer or silently use an unrelated depth texture.
        if (!main.useDepth || GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId ||
                main.viewWidth!=main.width || main.viewHeight!=main.height) return false;
        if (ribbon==null || ribbon.width!=main.width || ribbon.height!=main.height || ribbon.isStencilEnabled()!=main.isStencilEnabled()) {
            if (ribbon!=null) ribbon.destroyBuffers();
            ribbon=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);
            if (main.isStencilEnabled()) ribbon.enableStencil();
            ribbon.setClearColor(0,0,0,0);
        }
        try {
            ribbon.clear(Minecraft.ON_OSX);
            ribbon.copyDepthFrom(main);
        } finally { main.bindWrite(true); }
        return true;
    }

    static void draw(Matrix4f worldView, Matrix4f projection,Runnable geometry) {
        var main=Minecraft.getInstance().getMainRenderTarget();
        if (ribbon==null || ribbon.width!=main.width || ribbon.height!=main.height ||
            GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId) return;
        var oldProjection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting=RenderSystem.getVertexSorting();
        var matrices=RenderSystem.getModelViewStack();
        var shader=RenderSystem.getShader();
        int texture=RenderSystem.getShaderTexture(0);
        float[] color=RenderSystem.getShaderColor().clone();
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND), depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE), write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int compare=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int src=GL11.glGetInteger(0x80C9), dst=GL11.glGetInteger(0x80C8);
        int srcAlpha=GL11.glGetInteger(0x80CB), dstAlpha=GL11.glGetInteger(0x80CA);
        matrices.pushMatrix();
        try {
            ribbon.bindWrite(true);
            matrices.set(worldView); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection,sorting);
            geometry.run();
            main.bindWrite(true);
            matrices.identity(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f(),sorting);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0,ribbon.getColorTextureId());
            RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.enableDepthTest(); RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false); RenderSystem.disableCull(); RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            // Far-depth fragments pass only on pixels still clear after the normal hand pass.
            var q=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            q.addVertex(-1,-1,1).setUv(0,0); q.addVertex(1,-1,1).setUv(1,0);
            q.addVertex(1,1,1).setUv(1,1); q.addVertex(-1,1,1).setUv(0,1);
            BufferUploader.drawWithShader(q.buildOrThrow());
        } finally {
            main.bindWrite(true);
            matrices.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(oldProjection,sorting);
            RenderSystem.setShader(()->shader); RenderSystem.setShaderTexture(0,texture);
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            RenderSystem.blendFuncSeparate(src,dst,srcAlpha,dstAlpha);
            if(blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if(depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if(cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(write); RenderSystem.depthFunc(compare);
        }
    }
}
