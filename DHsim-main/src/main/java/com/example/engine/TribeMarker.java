package com.example.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * Draws a small colored square on the ground to indicate a villager's tribe.
 */
public final class TribeMarker {
    private static int vao = -1;
    private static ShaderProgram shader;

    private static final String VERT = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProjection;
            void main() {
                gl_Position = uProjection * uView * uModel * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAG = """
            #version 330 core
            out vec4 FragColor;
            uniform vec3 uColor;
            void main() {
                FragColor = vec4(uColor, 0.85);
            }
            """;

    public static void init() {
        if (shader != null) return;
        shader = new ShaderProgram(VERT, FRAG);

        float[] verts = {
                -0.5f, 0.0f, -0.5f,
                 0.5f, 0.0f, -0.5f,
                 0.5f, 0.0f,  0.5f,
                 0.5f, 0.0f,  0.5f,
                -0.5f, 0.0f,  0.5f,
                -0.5f, 0.0f, -0.5f,
        };

        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
        try { buf.put(verts).flip(); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW); }
        finally { MemoryUtil.memFree(buf); }

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    public static void render(Matrix4f view, Matrix4f proj, float x, float y, float z, float size, Vector3f color) {
        if (shader == null) return;
        shader.bind();
        // Slightly above ground (y + 0.05) to avoid z-fighting with terrain
        Matrix4f model = new Matrix4f().translate(x, y + 0.05f, z).scale(size);
        shader.setUniform("uModel", model);
        shader.setUniform("uView", view);
        shader.setUniform("uProjection", proj);
        shader.setUniform3f("uColor", color.x, color.y, color.z);

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
        shader.unbind();
    }

    public static void destroy() {
        if (shader != null) { shader.destroy(); shader = null; }
    }
}
