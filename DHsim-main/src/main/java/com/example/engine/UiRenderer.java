package com.example.engine;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * Screen-space 2D overlay renderer. Draws solid-color rectangles and
 * textured quads on top of the 3D scene using an orthographic projection
 * with the origin at top-left.
 */
public final class UiRenderer {
    private final int vao;
    private final ShaderProgram shader;
    private Matrix4f projection;

    private static final String VERT = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aTexCoord;
            uniform mat4 uModel;
            uniform mat4 uProjection;
            out vec2 vTexCoord;
            void main() {
                gl_Position = uProjection * uModel * vec4(aPos, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
            """;

    private static final String FRAG = """
            #version 330 core
            in vec2 vTexCoord;
            out vec4 FragColor;
            uniform sampler2D uTexture;
            uniform vec4 uTint;
            uniform int uUseTexture;
            uniform vec2 uUvOffset;
            uniform vec2 uUvScale;
            void main() {
                vec2 uv = uUvOffset + vTexCoord * uUvScale;
                vec4 tex = uUseTexture == 1 ? texture(uTexture, uv) : vec4(1.0);
                FragColor = tex * uTint;
            }
            """;

    public UiRenderer() {
        shader = new ShaderProgram(VERT, FRAG);

        // UVs mapped so that v=0 is the BOTTOM of the screen quad, v=1 is TOP.
        // This aligns with standard OpenGL texture orientation.
        float[] verts = {
                // x,     y,    u,   v
                -0.5f, -0.5f, 0.0f, 1.0f, // Top-Left
                 0.5f, -0.5f, 1.0f, 1.0f, // Top-Right
                 0.5f,  0.5f, 1.0f, 0.0f, // Bottom-Right
                 0.5f,  0.5f, 1.0f, 0.0f, // Bottom-Right
                -0.5f,  0.5f, 0.0f, 0.0f, // Bottom-Left
                -0.5f, -0.5f, 0.0f, 1.0f, // Top-Left
        };

        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
        try {
            buf.put(verts).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(buf);
        }
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    public void begin(int screenWidth, int screenHeight) {
        projection = new Matrix4f().ortho2D(0, screenWidth, screenHeight, 0);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public void end() {
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public void drawSolid(float cx, float cy, float w, float h,
                          float r, float g, float b, float a) {
        draw(cx, cy, w, h, 0, r, g, b, a, false, 0f, 0f, 1f, 1f);
    }

    public void drawTextured(float cx, float cy, float w, float h, int textureId) {
        draw(cx, cy, w, h, textureId, 1f, 1f, 1f, 1f, true, 0f, 0f, 1f, 1f);
    }

    /**
     * Draws a sub-region of a texture. Used to show the "content only" part of
     * auto-cropped sprites in the menu without stretching.
     */
    public void drawTexturedUV(float cx, float cy, float w, float h, int textureId,
                               float uMin, float vMin, float uMax, float vMax) {
        draw(cx, cy, w, h, textureId, 1f, 1f, 1f, 1f, true, uMin, vMin, uMax, vMax);
    }

    private void draw(float cx, float cy, float w, float h, int textureId,
                      float r, float g, float b, float a, boolean useTexture,
                      float uMin, float vMin, float uMax, float vMax) {
        Matrix4f model = new Matrix4f().translate(cx, cy, 0f).scale(w, h, 1f);
        shader.bind();
        shader.setUniform("uModel", model);
        shader.setUniform("uProjection", projection);
        shader.setUniform4f("uTint", r, g, b, a);
        shader.setUniform1i("uUseTexture", useTexture ? 1 : 0);
        shader.setUniform1i("uTexture", 0);
        shader.setUniform2f("uUvOffset", uMin, vMin);
        shader.setUniform2f("uUvScale", uMax - uMin, vMax - vMin);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        shader.unbind();
    }

    public void destroy() {
        shader.destroy();
    }
}
