package nayomy.visuals.render;

import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33C.*;

/** Запоминает реальное состояние GL и возвращает его при close(), чтобы кэш состояния Minecraft остался валидным. */
final class GlStateGuard implements AutoCloseable {
    private final int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
    private final int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
    private final int active = glGetInteger(GL_ACTIVE_TEXTURE);
    private final int[] texture = new int[2], sampler = new int[2], viewport = new int[4];
    private final int polygon;
    private final int[] capabilities = {GL_DEPTH_TEST, GL_BLEND, GL_CULL_FACE, GL_SCISSOR_TEST,
            GL_STENCIL_TEST, GL_FRAMEBUFFER_SRGB, GL_RASTERIZER_DISCARD};
    private final boolean[] enabled = new boolean[capabilities.length];
    private final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
    private final boolean[] colorMask = new boolean[4];

    GlStateGuard() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer v = stack.mallocInt(4); glGetIntegerv(GL_VIEWPORT, v); v.get(viewport);
            IntBuffer p = stack.mallocInt(2); glGetIntegerv(GL_POLYGON_MODE, p); polygon = p.get(0);
            ByteBuffer c = stack.malloc(4); glGetBooleanv(GL_COLOR_WRITEMASK, c);
            for (int i = 0; i < 4; i++) colorMask[i] = c.get(i) != 0;
        }
        for (int i = 0; i < capabilities.length; i++) enabled[i] = glIsEnabled(capabilities[i]);
        for (int i = 0; i < 2; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            texture[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
            sampler[i] = glGetInteger(GL_SAMPLER_BINDING);
        }
        glActiveTexture(active);
    }

    @Override
    public void close() {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
        glUseProgram(program);
        glBindVertexArray(vao);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glPolygonMode(GL_FRONT_AND_BACK, polygon);
        glDepthMask(depthMask);
        glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        for (int i = 0; i < capabilities.length; i++) {
            if (enabled[i]) glEnable(capabilities[i]); else glDisable(capabilities[i]);
        }
        for (int i = 0; i < 2; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            glBindTexture(GL_TEXTURE_2D, texture[i]);
            glBindSampler(i, sampler[i]);
        }
        glActiveTexture(active);
    }
}
