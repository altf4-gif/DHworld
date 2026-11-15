package com.example.engine;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * Renders dynamic 3D lines (used for Tribe View mode).
 */
public final class LineRenderer {
    private int vao, vbo;
    private ShaderProgram shader;

    private static final String VERT = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            uniform mat4 uView;
            uniform mat4 uProjection;
            void main() {
                gl_Position = uProjection * uView * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAG = """
            #version 330 core
            out vec4 FragColor;
            uniform vec3 uColor;
            void main() {
                FragColor = vec4(uColor, 0.8);
            }
            """;

    public LineRenderer() {
        shader = new ShaderProgram(VERT, FRAG);
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    public void render(Matrix4f view, Matrix4f proj, List<Float> vertices, float r, float g, float b) {
        if (vertices.isEmpty()) return;
        
        float[] data = new float[vertices.size()];
        for (int i = 0; i < data.length; i++) data[i] = vertices.get(i);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(data.length);
        try { buf.put(data).flip(); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STREAM_DRAW); }
        finally { MemoryUtil.memFree(buf); }

        shader.bind();
        shader.setUniform("uView", view);
        shader.setUniform("uProjection", proj);
        shader.setUniform3f("uColor", r, g, b);

        GL11.glLineWidth(2.0f);
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_LINES, 0, data.length / 3);
        GL30.glBindVertexArray(0);
        shader.unbind();
    }

    public void destroy() {
        shader.destroy();
        GL15.glDeleteBuffers(vbo);
        GL30.glDeleteVertexArrays(vao);
    }
}
