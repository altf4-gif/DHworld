package com.example.engine;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/** Ровная плита SIZE×SIZE на высоте 0. */
public final class Ground {
    public static final float SIZE = 60f;

    public static float getHeightAt(float x, float z) { return 0f; }

    private static final String VERT = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            uniform mat4 uView;
            uniform mat4 uProjection;
            out vec3 vWorld;
            void main() {
                vWorld = aPos;
                gl_Position = uProjection * uView * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAG = """
            #version 330 core
            in vec3 vWorld;
            out vec4 FragColor;
            void main() {
                float cell = mod(floor(vWorld.x / 2.0) + floor(vWorld.z / 2.0), 2.0);
                vec3 base = mix(vec3(0.62, 0.62, 0.64), vec3(0.52, 0.52, 0.54), cell);
                FragColor = vec4(base, 1.0);
            }
            """;

    private final int vao, vbo;
    private final ShaderProgram shader;

    public Ground() {
        shader = new ShaderProgram(VERT, FRAG);
        float h = SIZE / 2f;
        float[] v = {
            -h, 0, -h,  -h, 0,  h,   h, 0,  h,
            -h, 0, -h,   h, 0,  h,   h, 0, -h
        };
        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(v.length);
        try { buf.put(v).flip(); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW); }
        finally { MemoryUtil.memFree(buf); }
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    public void render(Matrix4f view, Matrix4f projection) {
        shader.bind();
        shader.setUniform("uView", view);
        shader.setUniform("uProjection", projection);
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
        shader.unbind();
    }

    public void destroy() {
        shader.destroy();
        GL15.glDeleteBuffers(vbo);
        GL30.glDeleteVertexArrays(vao);
    }
}
