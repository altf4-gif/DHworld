package com.example.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Procedural terrain with mountains, a lake basin, and flattenable building pads.
 *
 * Every registered "flatten pad" (from houses, trees) is smoothed into the
 * terrain so buildings sit perfectly flat on the ground.
 */
public final class Ground {
    // --- NOT final anymore, because rebuild() swaps these GPU handles ---
    private int vao;
    private int vbo;
    private int ebo;
    private final ShaderProgram shader;

    public static final float SIZE = 300f;
    public static final int RES = 256;
    public static final float LAKE_X = -60f;
    public static final float LAKE_Z = -60f;
    public static final float LAKE_RADIUS = 35f;
    public static final float WATER_LEVEL = -2.5f;

    // Building pads (x, z, radius, blend, target height)
    private static final List<Float> PADS = new ArrayList<>();

    public static void addPad(float x, float z, float radius, float blend) {
        float h = naturalHeightAt(x, z);
        PADS.add(x);
        PADS.add(z);
        PADS.add(radius);
        PADS.add(blend);
        PADS.add(h);
    }

    public static void clearPads() {
        PADS.clear();
    }

    public static float naturalHeightAt(float x, float z) {
        float h = 0.0f;
        float dist = (float) Math.sqrt(x * x + z * z);

        if (dist > 90f) {
            float t = (dist - 90f) / 50f;
            h += t * t * 30f;
            h += (float) Math.sin(x * 0.1f) * (float) Math.cos(z * 0.1f) * 3f;
        }

        float lakeDist = (float) Math.sqrt((x - LAKE_X) * (x - LAKE_X) + (z - LAKE_Z) * (z - LAKE_Z));
        if (lakeDist < LAKE_RADIUS) {
            float t = 1.0f - (lakeDist / LAKE_RADIUS);
            float smoothT = t * t * (3f - 2f * t);
            h -= smoothT * 8.0f;
        }

        h += (float) Math.sin(x * 0.05f) * 1.5f + (float) Math.cos(z * 0.05f) * 1.5f;

        return h;
    }

    public static float getHeightAt(float x, float z) {
        float naturalH = naturalHeightAt(x, z);
        if (PADS.isEmpty()) return naturalH;

        float result = naturalH;
        for (int i = 0; i < PADS.size(); i += 5) {
            float px = PADS.get(i);
            float pz = PADS.get(i + 1);
            float radius = PADS.get(i + 2);
            float blend = PADS.get(i + 3);
            float padH = PADS.get(i + 4);

            float d = (float) Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
            if (d > radius + blend) continue;

            float w;
            if (d <= radius) {
                w = 1f;
            } else {
                float t = (d - radius) / blend;
                w = 1f - t * t * (3f - 2f * t);
            }

            result = result * (1f - w) + padH * w;
        }
        return result;
    }

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

            uniform sampler2D uShadowMap;
            uniform mat4 uLightVP;
            uniform vec3 uSunDir;
            uniform vec3 uSunColor;
            uniform vec3 uAmbient;
            uniform vec3 uCamPos;
            uniform vec3 uFogColor;
            uniform float uFogDensity;

            float shadowCalc(vec4 lsp) {
                vec3 p = lsp.xyz / lsp.w;
                p = p * 0.5 + 0.5;
                if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
                float current = p.z - 0.0025;
                vec2 ts = 1.0 / textureSize(uShadowMap, 0);
                float sh = 0.0;
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        float d = texture(uShadowMap, p.xy + vec2(x, y) * ts).r;
                        sh += current > d ? 0.0 : 1.0;
                    }
                }
                return sh / 9.0;
            }

            void main() {
                vec3 n = normalize(cross(dFdx(vWorld), dFdy(vWorld)));
                if (n.y < 0.0) n = -n;

                float diff = max(dot(n, uSunDir), 0.0);
                float shadow = 1.0;
                if (diff > 0.0) shadow = shadowCalc(uLightVP * vec4(vWorld, 1.0));

                vec3 grass = mix(vec3(0.30, 0.52, 0.27), vec3(0.40, 0.63, 0.35), mod(floor(vWorld.x) + floor(vWorld.z), 2.0));
                vec3 rock = vec3(0.45, 0.45, 0.45);
                vec3 snow = vec3(0.95, 0.95, 0.95);
                vec3 sand = vec3(0.76, 0.70, 0.50);

                vec3 baseColor = grass;
                float h = vWorld.y;

                if (h < -1.5) {
                    baseColor = sand;
                } else {
                    float dist = length(vWorld.xz);
                    if (dist > 100.0) {
                        float mtnFactor = smoothstep(100.0, 140.0, dist);
                        baseColor = mix(baseColor, rock, mtnFactor);
                        if (h > 15.0) {
                            baseColor = mix(baseColor, snow, smoothstep(15.0, 25.0, h));
                        }
                    }
                }

                vec3 lit = baseColor * (uAmbient + uSunColor * diff * shadow);

                float dist = length(vWorld - uCamPos);
                float fog = 1.0 - exp(-pow(dist * uFogDensity, 2.0));
                lit = mix(lit, uFogColor, fog);

                FragColor = vec4(lit, 1.0);
            }
            """;

    public Ground() {
        shader = new ShaderProgram(VERT, FRAG);
        rebuild();
    }

    /** Rebuilds the mesh from the current pad list. */
    public void rebuild() {
        // Delete old VAO/VBOs if they exist
        if (vao != 0) {
            GL15.glDeleteBuffers(vbo);
            GL15.glDeleteBuffers(ebo);
            GL30.glDeleteVertexArrays(vao);
        }

        float[] verts = new float[(RES + 1) * (RES + 1) * 3];
        int idx = 0;
        float halfSize = SIZE / 2f;
        float step = SIZE / RES;

        for (int z = 0; z <= RES; z++) {
            for (int x = 0; x <= RES; x++) {
                float px = -halfSize + x * step;
                float pz = -halfSize + z * step;
                float py = getHeightAt(px, pz);

                verts[idx++] = px;
                verts[idx++] = py;
                verts[idx++] = pz;
            }
        }

        int[] indices = new int[RES * RES * 6];
        int i = 0;
        for (int z = 0; z < RES; z++) {
            for (int x = 0; x < RES; x++) {
                int topLeft = z * (RES + 1) + x;
                int topRight = topLeft + 1;
                int bottomLeft = (z + 1) * (RES + 1) + x;
                int bottomRight = bottomLeft + 1;

                indices[i++] = topLeft;
                indices[i++] = bottomLeft;
                indices[i++] = topRight;

                indices[i++] = topRight;
                indices[i++] = bottomLeft;
                indices[i++] = bottomRight;
            }
        }

        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer vBuf = MemoryUtil.memAllocFloat(verts.length);
        try {
            vBuf.put(verts).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vBuf, GL15.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(vBuf);
        }

        ebo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        IntBuffer iBuf = MemoryUtil.memAllocInt(indices.length);
        try {
            iBuf.put(indices).flip();
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, iBuf, GL15.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(iBuf);
        }

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    public void render(Matrix4f view, Matrix4f projection,
                       Matrix4f lightVP, int shadowMapTex,
                       Vector3f sunDir, Vector3f sunColor, Vector3f ambient,
                       Vector3f camPos, Vector3f fogColor, float fogDensity) {
        shader.bind();
        shader.setUniform("uView", view);
        shader.setUniform("uProjection", projection);
        shader.setUniform("uLightVP", lightVP);
        shader.setUniform3f("uSunDir", sunDir.x, sunDir.y, sunDir.z);
        shader.setUniform3f("uSunColor", sunColor.x, sunColor.y, sunColor.z);
        shader.setUniform3f("uAmbient", ambient.x, ambient.y, ambient.z);
        shader.setUniform3f("uCamPos", camPos.x, camPos.y, camPos.z);
        shader.setUniform3f("uFogColor", fogColor.x, fogColor.y, fogColor.z);
        shader.setUniform1f("uFogDensity", fogDensity);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowMapTex);
        shader.setUniform1i("uShadowMap", 1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        GL30.glBindVertexArray(vao);
        GL11.glDrawElements(GL11.GL_TRIANGLES, RES * RES * 6, GL11.GL_UNSIGNED_INT, 0);
        GL30.glBindVertexArray(0);
        shader.unbind();
    }

    public void destroy() {
        shader.destroy();
        GL15.glDeleteBuffers(vbo);
        GL15.glDeleteBuffers(ebo);
        GL30.glDeleteVertexArrays(vao);
    }
}
