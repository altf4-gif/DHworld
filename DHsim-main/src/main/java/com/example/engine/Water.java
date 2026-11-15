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
 * Hyper-realistic water — clips to the circular lake, never writes to
 * the depth buffer, and accepts the weather-modified sun colour.
 */
public final class Water {
    private final int vao;
    private final ShaderProgram shader;

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

            uniform vec3 uSunDir;
            uniform vec3 uSunColor;
            uniform vec3 uCamPos;
            uniform vec3 uSkyTop;
            uniform vec3 uSkyBottom;
            uniform float uTime;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform vec2 uLakeCenter;
            uniform float uLakeRadius;

            float wave(vec2 p) {
                return sin(p.x * 0.5 + uTime) * cos(p.y * 0.4 + uTime * 0.8) * 0.2;
            }

            void main() {
                if (distance(vWorld.xz, uLakeCenter) > uLakeRadius) discard;

                float eps = 0.1;
                float h = wave(vWorld.xz);
                float hx = wave(vWorld.xz + vec2(eps, 0.0));
                float hz = wave(vWorld.xz + vec2(0.0, eps));
                vec3 n = normalize(vec3(h - hx, 1.0, h - hz));

                vec3 viewDir = normalize(uCamPos - vWorld);
                float fresnel = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0);
                vec3 skyColor = mix(uSkyBottom, uSkyTop, max(0.0, viewDir.y));
                vec3 halfVec = normalize(uSunDir + viewDir);
                float spec = pow(max(dot(n, halfVec), 0.0), 256.0);
                vec3 deepColor = vec3(0.05, 0.15, 0.25);

                vec3 col = mix(deepColor, skyColor, fresnel);
                col += uSunColor * spec * 2.0;

                float dist = length(vWorld - uCamPos);
                float fog = 1.0 - exp(-pow(dist * uFogDensity, 2.0));
                col = mix(col, uFogColor, fog);

                FragColor = vec4(col, 0.85);
            }
            """;

    public Water() {
        shader = new ShaderProgram(VERT, FRAG);

        float size = Ground.LAKE_RADIUS * 2.2f;
        float cx = Ground.LAKE_X, cz = Ground.LAKE_Z, y = Ground.WATER_LEVEL;

        float[] verts = {
                cx - size, y, cz - size,
                cx + size, y, cz - size,
                cx + size, y, cz + size,
                cx + size, y, cz + size,
                cx - size, y, cz + size,
                cx - size, y, cz - size,
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

    public void render(Matrix4f view, Matrix4f projection,
                       DayNight dayNight, Vector3f sunColor,
                       Vector3f camPos, float time,
                       Vector3f fogColor, float fogDensity) {
        shader.bind();
        shader.setUniform("uView", view);
        shader.setUniform("uProjection", projection);

        Vector3f sunDir = dayNight.sunDir();
        Vector3f skyTop = dayNight.skyTop();
        Vector3f skyBottom = dayNight.skyBottom();

        shader.setUniform3f("uSunDir", sunDir.x, sunDir.y, sunDir.z);
        shader.setUniform3f("uSunColor", sunColor.x, sunColor.y, sunColor.z);
        shader.setUniform3f("uCamPos", camPos.x, camPos.y, camPos.z);
        shader.setUniform3f("uSkyTop", skyTop.x, skyTop.y, skyTop.z);
        shader.setUniform3f("uSkyBottom", skyBottom.x, skyBottom.y, skyBottom.z);
        shader.setUniform1f("uTime", time);
        shader.setUniform3f("uFogColor", fogColor.x, fogColor.y, fogColor.z);
        shader.setUniform1f("uFogDensity", fogDensity);
        shader.setUniform2f("uLakeCenter", Ground.LAKE_X, Ground.LAKE_Z);
        shader.setUniform1f("uLakeRadius", Ground.LAKE_RADIUS);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        shader.unbind();
    }

    public void destroy() {
        shader.destroy();
    }
}
