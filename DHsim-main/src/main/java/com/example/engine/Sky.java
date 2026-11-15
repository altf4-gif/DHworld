package com.example.engine;

import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public final class Sky {
    private final int vao;
    private final ShaderProgram shader;

    private static final String VERT = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            uniform vec3 uCamRight;
            uniform vec3 uCamUp;
            uniform vec3 uCamForward;
            uniform float uFovTan;
            uniform float uAspect;
            out vec3 vRayDir;
            void main() {
                vRayDir = uCamForward + aPos.x * uAspect * uFovTan * uCamRight + aPos.y * uFovTan * uCamUp;
                gl_Position = vec4(aPos, 1.0, 1.0);
            }
            """;

    private static final String FRAG = """
            #version 330 core
            in vec3 vRayDir;
            out vec4 FragColor;

            uniform vec3 uTop;
            uniform vec3 uBottom;
            uniform vec3 uSunColor;
            uniform float uSunElevation;
            uniform vec3 uSunDir;
            uniform float uCloudiness;
            uniform float uTime;
            uniform vec3 uCloudColor;
            uniform vec3 uCamPos;

            float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
            float noise(vec2 p) {
                vec2 i = floor(p); vec2 f = fract(p);
                vec2 u = f*f*(3.0-2.0*f);
                return mix(mix(hash(i), hash(i+vec2(1,0)), u.x),
                           mix(hash(i+vec2(0,1)), hash(i+vec2(1,1)), u.x), u.y);
            }
            float fbm(vec2 p) {
                float v = 0.0; float a = 0.5;
                for (int i = 0; i < 5; i++) { v += a * noise(p); p *= 2.0; a *= 0.5; }
                return v;
            }

            void main() {
                vec3 dir = normalize(vRayDir);
                float vY = max(0.0, dir.y);
                vec3 col = mix(uBottom, uTop, pow(vY, 0.6));
                
                float sunDot = max(dot(dir, uSunDir), 0.0);
                col += uSunColor * pow(sunDot, 64.0) * 2.0;
                col += uSunColor * pow(sunDot, 8.0) * 0.3;

                // Stars at night
                if (uSunElevation < 0.1) {
                    float starIntensity = smoothstep(0.1, -0.1, uSunElevation) * (1.0 - uCloudiness);
                    if (starIntensity > 0.0) {
                        vec2 starUV = dir.xz / dir.y * 0.1;
                        float star = hash(floor(starUV * 100.0));
                        if (star > 0.995) {
                            col += vec3(1.0, 1.0, 0.9) * starIntensity * 2.0;
                        }
                    }
                }

                if (uCloudiness > 0.01 && dir.y > 0.0) {
                    float planeHeight = 80.0;
                    float t = (planeHeight - uCamPos.y) / dir.y;
                    if (t > 0.0) {
                        vec2 uv = uCamPos.xz + dir.xz * t;
                        uv += uTime * vec2(2.0, 1.0);
                        float n1 = fbm(uv * 0.003);
                        float n2 = fbm(uv * 0.008 + uTime * 0.5);
                        float n = n1 * 0.7 + n2 * 0.3;
                        float cloudAlpha = smoothstep(0.35, 0.65, n) * uCloudiness;
                        float horizonFade = smoothstep(0.0, 0.15, dir.y);
                        cloudAlpha *= horizonFade;
                        
                        vec3 cloudLit = uCloudColor;
                        float sunsetGlow = (1.0 - smoothstep(0.0, 0.4, abs(uSunElevation)));
                        cloudLit = mix(cloudLit, uSunColor * 1.2, sunsetGlow * 0.8);
                        cloudLit *= max(0.2, uSunElevation);
                        
                        col = mix(col, cloudLit, cloudAlpha);
                    }
                }

                FragColor = vec4(col, 1.0);
            }
            """;

    public Sky() {
        shader = new ShaderProgram(VERT, FRAG);
        float[] verts = { -1f, -1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f, -1f, -1f };
        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
        try { buf.put(verts).flip(); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW); }
        finally { MemoryUtil.memFree(buf); }
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2 * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    public void render(Camera camera, DayNight dayNight, Weather weather, float time) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        Vector3f top    = dayNight.skyTop();
        Vector3f bottom = dayNight.skyBottom();
        Vector3f sunCol = dayNight.sunColor();
        Vector3f sunDir = dayNight.sunDir();
        Vector3f cloudCol = new Vector3f(1.0f, 1.0f, 1.0f).lerp(new Vector3f(0.6f, 0.65f, 0.7f), weather.cloudiness());

        shader.bind();
        Vector3f right = camera.rightVector();
        Vector3f up = camera.upVector();
        Vector3f forward = camera.frontVector();
        
        shader.setUniform3f("uCamRight", right.x, right.y, right.z);
        shader.setUniform3f("uCamUp", up.x, up.y, up.z);
        shader.setUniform3f("uCamForward", forward.x, forward.y, forward.z);
        shader.setUniform1f("uFovTan", (float)Math.tan(Math.toRadians(camera.fov() / 2.0)));
        shader.setUniform1f("uAspect", camera.projection().m11() / camera.projection().m00());
        
        shader.setUniform3f("uTop",    top.x, top.y, top.z);
        shader.setUniform3f("uBottom", bottom.x, bottom.y, bottom.z);
        shader.setUniform3f("uSunColor", sunCol.x, sunCol.y, sunCol.z);
        shader.setUniform3f("uSunDir", sunDir.x, sunDir.y, sunDir.z);
        shader.setUniform1f("uSunElevation", dayNight.sunElevation());
        
        shader.setUniform1f("uCloudiness", weather.cloudiness());
        shader.setUniform1f("uTime", time);
        shader.setUniform3f("uCloudColor", cloudCol.x, cloudCol.y, cloudCol.z);
        
        Vector3f cp = camera.position();
        shader.setUniform3f("uCamPos", cp.x, cp.y, cp.z);

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
        shader.unbind();

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public void destroy() { shader.destroy(); }
}
