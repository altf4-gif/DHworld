package com.example.firewater;

import com.example.engine.ShaderProgram;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/** Цветные кубы: полы, стены, лужи, кнопки, ворота, ящики, алмазы. */
public final class BoxRenderer {
    private final ShaderProgram shader;
    private final int vao, vbo;
    private final Matrix4f model = new Matrix4f();

    private static final String VERT = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec3 aNormal;
            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProjection;
            out vec3 vN;
            void main() {
                vN = mat3(uModel) * aNormal;
                gl_Position = uProjection * uView * uModel * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAG = """
            #version 330 core
            in vec3 vN;
            uniform vec3 uColor;
            out vec4 FragColor;
            void main() {
                vec3 n = normalize(vN);
                float d = max(dot(n, normalize(vec3(0.4, 0.9, 0.3))), 0.0);
                FragColor = vec4(uColor * (0.45 + 0.55 * d), 1.0);
            }
            """;

    public BoxRenderer() {
        shader = new ShaderProgram(VERT, FRAG);
        float[] v = cube();
        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(v.length);
        try { buf.put(v).flip(); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW); }
        finally { MemoryUtil.memFree(buf); }
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 6 * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 6 * Float.BYTES, 3L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    private static float[] cube() {
        float[][] n = {{0,0,1},{0,0,-1},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}};
        float[][][] c = {
            {{-.5f,-.5f,.5f},{.5f,-.5f,.5f},{.5f,.5f,.5f},{-.5f,.5f,.5f}},
            {{.5f,-.5f,-.5f},{-.5f,-.5f,-.5f},{-.5f,.5f,-.5f},{.5f,.5f,-.5f}},
            {{.5f,-.5f,.5f},{.5f,-.5f,-.5f},{.5f,.5f,-.5f},{.5f,.5f,.5f}},
            {{-.5f,-.5f,-.5f},{-.5f,-.5f,.5f},{-.5f,.5f,.5f},{-.5f,.5f,-.5f}},
            {{-.5f,.5f,.5f},{.5f,.5f,.5f},{.5f,.5f,-.5f},{-.5f,.5f,-.5f}},
            {{-.5f,-.5f,-.5f},{.5f,-.5f,-.5f},{.5f,-.5f,.5f},{-.5f,-.5f,.5f}}
        };
        int[] tri = {0,1,2,0,2,3};
        float[] p = new float[36 * 6];
        int o = 0;
        for (int f = 0; f < 6; f++)
            for (int t : tri) {
                for (int k = 0; k < 3; k++) p[o++] = c[f][t][k];
                for (int k = 0; k < 3; k++) p[o++] = n[f][k];
            }
        return p;
    }

    public void begin(Matrix4f view, Matrix4f proj) {
        shader.bind();
        shader.setUniform("uView", view);
        shader.setUniform("uProjection", proj);
    }

    public void box(float cx, float cy, float cz, float sx, float sy, float sz, float r, float g, float b) {
        model.identity().translate(cx, cy, cz).scale(sx, sy, sz);
        draw(r, g, b);
    }

    public void gem(float cx, float cy, float cz, float s, float angle, float r, float g, float b) {
        model.identity().translate(cx, cy, cz).rotateY(angle).scale(s, s, s);
        draw(r, g, b);
    }

    private void draw(float r, float g, float b) {
        shader.setUniform("uModel", model);
        shader.setUniform3f("uColor", r, g, b);
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 36);
        GL30.glBindVertexArray(0);
    }

    public void end() { shader.unbind(); }

    public void destroy() {
        shader.destroy();
        GL15.glDeleteBuffers(vbo);
        GL30.glDeleteVertexArrays(vao);
    }
}
