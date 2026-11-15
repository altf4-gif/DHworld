package com.example.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedural poop. Now supports fog (distance blending) and global tint.
 */
public final class Poop {

    private static final String VERT = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec3 aColor;
            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProjection;
            out vec3 vColor;
            out vec3 vWorld;
            void main() {
                vColor = aColor;
                vec4 world = uModel * vec4(aPos, 1.0);
                vWorld = world.xyz;
                gl_Position = uProjection * uView * world;
            }
            """;

    private static final String FRAG = """
            #version 330 core
            in vec3 vColor;
            in vec3 vWorld;
            out vec4 FragColor;

            uniform float uGold;
            uniform vec3 uLightTint;
            uniform vec3 uCamPos;
            uniform vec3 uFogColor;
            uniform float uFogDensity;

            void main() {
                vec3 gold = vec3(1.0, 0.85, 0.15);
                vec3 col = mix(vColor, gold, uGold) * uLightTint;
                
                float dist = length(vWorld - uCamPos);
                float fog = 1.0 - exp(-pow(dist * uFogDensity, 2.0));
                col = mix(col, uFogColor, fog);
                
                FragColor = vec4(col, 1.0);
            }
            """;

    private static final int NUM_VARIANTS = 5;
    private static final float BRIGHT_CHANCE = 0.05f;
    private static final float BIG_THRESHOLD = 1.5f;

    private static ShaderProgram shader;
    private static Variant[] variants;
    private static Vector3f sCamPos = new Vector3f();
    private static Vector3f sFogColor = new Vector3f();
    private static float sFogDensity = 0f;

    public final float x;
    public float y;
    public final float z;
    public final boolean bright;
    public final boolean big;
    public boolean eaten;

    private final Variant variant;
    private final Matrix4f model = new Matrix4f();
    private final float yaw;
    private final float scale;

    private Poop(Variant variant, float x, float y, float z, float yaw, float scale,
                 boolean bright, boolean big) {
        this.variant = variant;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.scale = scale;
        this.bright = bright; this.big = big;
        model.translate(x, y, z).rotateY(yaw).scale(scale);
    }

    public static void init() {
        if (shader != null) return;
        shader = new ShaderProgram(VERT, FRAG);
        variants = new Variant[NUM_VARIANTS];
        for (int i = 0; i < NUM_VARIANTS; i++) {
            variants[i] = Variant.build(new Random(0xC0FFEE + i * 7919L));
        }
    }

    public static Poop random(float x, float z, Random rng) {
        return make(x, z, Ground.getHeightAt(x, z), rng, rng.nextFloat() < BRIGHT_CHANCE);
    }

    public static Poop bright(float x, float z, Random rng) {
        return make(x, z, Ground.getHeightAt(x, z), rng, true);
    }

    public static Poop normal(float x, float z, Random rng) {
        return make(x, z, Ground.getHeightAt(x, z), rng, false);
    }

    public static Poop brightAt(float x, float y, float z, Random rng) {
        return make(x, z, y, rng, true);
    }

    public static Poop normalAt(float x, float y, float z, Random rng) {
        return make(x, z, y, rng, false);
    }

    private static Poop make(float x, float z, float y, Random rng, boolean bright) {
        Variant v = variants[rng.nextInt(NUM_VARIANTS)];
        float yaw = rng.nextFloat() * (float) (Math.PI * 2);
        float scale = 0.6f + rng.nextFloat() * 0.7f;
        if (rng.nextFloat() < 0.08f) scale *= 2.1f;
        boolean big = scale >= BIG_THRESHOLD;
        return new Poop(v, x, y, z, yaw, scale, bright, big);
    }

    public void repositionY() {
        this.y = Ground.getHeightAt(x, z);
        model.identity().translate(x, y, z).rotateY(yaw).scale(scale);
    }

    public static void begin(Matrix4f view, Matrix4f projection,
                             Vector3f camPos, Vector3f fogColor, float fogDensity) {
        shader.bind();
        shader.setUniform("uView", view);
        shader.setUniform("uProjection", projection);
        
        sCamPos.set(camPos);
        sFogColor.set(fogColor);
        sFogDensity = fogDensity;
        
        shader.setUniform3f("uCamPos", sCamPos.x, sCamPos.y, sCamPos.z);
        shader.setUniform3f("uFogColor", sFogColor.x, sFogColor.y, sFogColor.z);
        shader.setUniform1f("uFogDensity", sFogDensity);
    }

    public static void setLightTint(float r, float g, float b) {
        if (shader != null) shader.setUniform3f("uLightTint", r, g, b);
    }

    public void render() {
        shader.setUniform("uModel", model);
        shader.setUniform1f("uGold", bright ? 0.55f : 0.0f);
        GL30.glBindVertexArray(variant.vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, variant.count);
        GL30.glBindVertexArray(0);
    }

    public static void end() { shader.unbind(); }

    public static void destroyAll() {
        if (shader != null) { shader.destroy(); shader = null; }
        if (variants != null) {
            for (Variant v : variants) v.destroy();
            variants = null;
        }
    }

    private static final class Variant {
        final int vao, vbo, count;
        private Variant(int vao, int vbo, int count) {
            this.vao = vao; this.vbo = vbo; this.count = count;
        }
        void destroy() {
            GL15.glDeleteBuffers(vbo);
            GL30.glDeleteVertexArrays(vao);
        }

        static Variant build(Random rng) {
            final int RINGS = 28, SIDES = 32;
            float height = 1.0f;
            float baseRadius = 0.42f + rng.nextFloat() * 0.08f;
            float lean = 0.06f + rng.nextFloat() * 0.07f;
            float leanAngle = rng.nextFloat() * (float) (Math.PI * 2);
            float twist = (float) (Math.PI * (1.0f + rng.nextFloat() * 0.7f));
            float ridgeAmp = 0.10f + rng.nextFloat() * 0.07f;
            int ridgeCount = 5 + rng.nextInt(3);
            float phase = rng.nextFloat() * (float) (Math.PI * 2);
            float tipPinch = 0.55f + rng.nextFloat() * 0.20f;
            float cr = 0.32f + rng.nextFloat() * 0.06f;
            float cg = 0.20f + rng.nextFloat() * 0.05f;
            float cb = 0.10f + rng.nextFloat() * 0.03f;

            float[][] px = new float[RINGS + 1][SIDES];
            float[][] py = new float[RINGS + 1][SIDES];
            float[][] pz = new float[RINGS + 1][SIDES];

            for (int i = 0; i <= RINGS; i++) {
                float t = (float) i / RINGS;
                float rBase = baseRadius * (float) Math.pow(1.0f - t, 0.72f);
                rBase *= (1.0f - t * tipPinch);
                float ox = (float) Math.cos(leanAngle) * lean * t * t;
                float oz = (float) Math.sin(leanAngle) * lean * t * t;
                float y = t * height;
                for (int j = 0; j < SIDES; j++) {
                    float angle = (float) (2.0 * Math.PI * j / SIDES);
                    float spiral = angle * ridgeCount + twist * t + phase;
                    float r = rBase * (1.0f + ridgeAmp * (float) Math.sin(spiral));
                    px[i][j] = ox + r * (float) Math.cos(angle);
                    py[i][j] = y;
                    pz[i][j] = oz + r * (float) Math.sin(angle);
                }
            }

            float[][] vnx = new float[RINGS + 1][SIDES];
            float[][] vny = new float[RINGS + 1][SIDES];
            float[][] vnz = new float[RINGS + 1][SIDES];
            for (int i = 0; i <= RINGS; i++) {
                int im = Math.max(i - 1, 0), ip = Math.min(i + 1, RINGS);
                for (int j = 0; j < SIDES; j++) {
                    int jm = (j - 1 + SIDES) % SIDES, jp = (j + 1) % SIDES;
                    float ttx = px[ip][j] - px[im][j];
                    float tty = py[ip][j] - py[im][j];
                    float ttz = pz[ip][j] - pz[im][j];
                    float atx = px[i][jp] - px[i][jm];
                    float aty = py[i][jp] - py[i][jm];
                    float atz = pz[i][jp] - pz[i][jm];
                    float nx = tty * atz - ttz * aty;
                    float ny = ttz * atx - ttx * atz;
                    float nz = ttx * aty - tty * atx;
                    if (nx * px[i][j] + nz * pz[i][j] < 0f) { nx = -nx; ny = -ny; nz = -nz; }
                    float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (nl < 1e-6f) { nx = 0f; ny = 1f; nz = 0f; nl = 1f; }
                    vnx[i][j] = nx / nl; vny[i][j] = ny / nl; vnz[i][j] = nz / nl;
                }
            }

            float lx = -0.35f, ly = 0.85f, lz = 0.35f;
            float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
            lx /= ll; ly /= ll; lz /= ll;

            List<Float> out = new ArrayList<>(RINGS * SIDES * 36);
            for (int i = 0; i < RINGS; i++) {
                for (int j = 0; j < SIDES; j++) {
                    int j2 = (j + 1) % SIDES;
                    addShaded(out, i, j, px, py, pz, vnx, vny, vnz, cr, cg, cb, lx, ly, lz);
                    addShaded(out, i, j2, px, py, pz, vnx, vny, vnz, cr, cg, cb, lx, ly, lz);
                    addShaded(out, i + 1, j, px, py, pz, vnx, vny, vnz, cr, cg, cb, lx, ly, lz);
                    addShaded(out, i, j2, px, py, pz, vnx, vny, vnz, cr, cg, cb, lx, ly, lz);
                    addShaded(out, i + 1, j2, px, py, pz, vnx, vny, vnz, cr, cg, cb, lx, ly, lz);
                    addShaded(out, i + 1, j, px, py, pz, vnx, vny, vnz, cr, cg, cb, lx, ly, lz);
                }
            }
            float capShade = 0.35f;
            for (int j = 0; j < SIDES; j++) {
                int j2 = (j + 1) % SIDES;
                addFlat(out, 0f, 0f, 0f, cr * capShade, cg * capShade, cb * capShade);
                addFlat(out, px[0][j2], py[0][j2], pz[0][j2], cr * capShade, cg * capShade, cb * capShade);
                addFlat(out, px[0][j], py[0][j], pz[0][j], cr * capShade, cg * capShade, cb * capShade);
            }

            float[] data = new float[out.size()];
            for (int i = 0; i < data.length; i++) data[i] = out.get(i);

            int vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            int vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            FloatBuffer buf = MemoryUtil.memAllocFloat(data.length);
            try { buf.put(data).flip(); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW); }
            finally { MemoryUtil.memFree(buf); }

            final int stride = 6 * Float.BYTES;
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 3L * Float.BYTES);
            GL20.glEnableVertexAttribArray(1);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);
            return new Variant(vao, vbo, data.length / 6);
        }

        private static void addShaded(List<Float> out, int i, int j,
                                      float[][] px, float[][] py, float[][] pz,
                                      float[][] vnx, float[][] vny, float[][] vnz,
                                      float cr, float cg, float cb,
                                      float lx, float ly, float lz) {
            float x = px[i][j], y = py[i][j], z = pz[i][j];
            float nx = vnx[i][j], ny = vny[i][j], nz = vnz[i][j];
            float lambert = Math.max(0f, nx * lx + ny * ly + nz * lz);
            float shade = 0.40f + 0.75f * lambert;
            float tint = 1.0f + 0.25f * y;
            out.add(x); out.add(y); out.add(z);
            out.add(clamp(cr * shade * tint));
            out.add(clamp(cg * shade * tint));
            out.add(clamp(cb * shade * tint));
        }

        private static void addFlat(List<Float> out, float x, float y, float z,
                                    float r, float g, float b) {
            out.add(x); out.add(y); out.add(z);
            out.add(r); out.add(g); out.add(b);
        }

        private static float clamp(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    }
}
