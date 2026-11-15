package com.example.engine;

/*
 * ============================ ГРАБЛИ — НЕ ПОВТОРЯТЬ ============================
 * 1. МЕШ НЕ РЕЗАТЬ НА КУСКИ. Скининг (LBS): один VAO, у вершины до 4 костей
 *    с весами. Вершина на шве получает ~0.5/0.5 -> шов сварен при любом угле.
 *    Как только вернёшь жёсткие части — вернутся дырки на плечах и тазу.
 * 2. Веса — разбиение единицы: wTorso = 1 - wHead - wArm - wLeg. Всегда.
 *    Иначе меш либо раздувает, либо схлопывает.
 * 3. Руки только ВЫШЕ подмышки (armBandLow), иначе пузо толстяка уезжает в руку.
 * 4. Линии реза (шея/подмышка/промежность) ищутся ПО ПРОФИЛЮ МЕША, не константой.
 * 5. Длина руки и отступ наружу запекаются в rest-позицию, УМНОЖЕННЫЕ НА ВЕС
 *    руки. Без умножения на вес шов разъезжается.
 * 6. Кости в ЛОКАЛЬНОМ пространстве, world применяется один раз в uModel.
 * 7. JOML: повороты к вершинам в обратном порядке вызовов.
 *    .rotateX(swing).rotateZ(drop) => сначала опустить, потом мах вперёд-назад.
 * 8. Пивот ноги = линия реза ног. rotateX не меняет x -> кромка не ползёт.
 * ===========================================================================
 */

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class RiggedModel {

    private static final String CFG_PATH = "mesh/MrH/rig.txt";
    private static final String STEP_SFX = "mp3/steps-10.mp3";

    // индексы костей
    private static final int B_TORSO = 0, B_HEAD = 1, B_ARM_L = 2, B_ARM_R = 3, B_LEG_L = 4, B_LEG_R = 5;
    private static final int NUM_BONES = 6;

    // ---------------- конфиг ----------------
    private static boolean AUTO_RIG       = true;
    private static float NECK_FRAC        = 0.85f;
    private static float HIP_FRAC         = 0.45f;
    private static float SHOULDER_FRAC    = 0.80f;
    private static float ARM_CUT_FRAC     = 0.13f;
    private static float FINGERTIP_FRAC   = 0.38f;
    private static float ARM_STRETCH_CFG  = 0f;      // 0 = авто
    private static float ARM_BAND_RATIO   = 0.55f;
    private static float ARM_MARGIN       = 1.02f;
    private static float ARM_BAND_PAD     = 0.02f;
    private static float LEG_GAP          = 0.015f;
    private static float ARM_OUT_CFG      = -1f;     // -1 = авто-зазор, иначе доля ШИРИНЫ
    private static float ARM_OUT_MARGIN   = 0.012f;  // зазор до тела, доля ширины
    private static float ARM_OUT_MAX      = 0.08f;   // потолок отступа, доля ширины
    private static float BLEND_X          = 0.030f;  // мягкость шва плеча, доля ширины
    private static float BLEND_Y          = 0.030f;  // мягкость шва шеи/таза, доля роста
    private static float LEG_AMP          = 0.45f;
    private static float ARM_AMP          = 0.28f;
    private static float BOB_AMP          = 0.018f;
    private static float STRIDE_FRAC      = 0.45f;
    private static float WALK_SPEED_CFG   = 1.5f;
    private static float LEAN_WALK        = 0.06f;
    private static float SWAY_AMP         = 0.02f;
    private static float TURN_RATE        = 4.0f;
    private static float IDLE_MIN         = 1.0f;
    private static float IDLE_MAX         = 3.5f;
    private static float PELVIS_MARGIN    = 0.08f;
    private static float FACING_OFFSET    = 0f;
    private static boolean STEP_SOUND     = true;

    /** оверрайды отступа по имени файла: out_womento=0.03 */
    private static final Map<String, Float> OUT_OVERRIDES = new LinkedHashMap<>();

    private static boolean cfgLoaded = false;
    private static long lastStepMs = 0L;

    // ---------------- общие шейдеры ----------------
    private static ShaderProgram skinShader, skinShadowShader;
    private static int shaderUsers = 0;

    private static final String SKIN_VERT = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            layout (location = 2) in vec4 aBoneIds;
            layout (location = 3) in vec4 aWeights;

            uniform mat4 uBones[6];
            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProjection;

            out vec2 vTexCoord;
            out vec3 vWorld;

            void main() {
                mat4 skin = aWeights.x * uBones[int(aBoneIds.x)]
                          + aWeights.y * uBones[int(aBoneIds.y)]
                          + aWeights.z * uBones[int(aBoneIds.z)]
                          + aWeights.w * uBones[int(aBoneIds.w)];
                vec4 wp = uModel * skin * vec4(aPos, 1.0);
                vWorld = wp.xyz;
                vTexCoord = aTexCoord;
                gl_Position = uProjection * uView * wp;
            }
            """;

    private static final String SKIN_FRAG = """
            #version 330 core
            in vec2 vTexCoord;
            in vec3 vWorld;
            out vec4 FragColor;

            uniform sampler2D uTexture;
            uniform sampler2D uShadowMap;
            uniform mat4 uLightVP;
            uniform vec3 uSunDir;
            uniform vec3 uSunColor;
            uniform vec3 uAmbient;
            uniform vec3 uCamPos;
            uniform vec3 uFogColor;
            uniform float uFogDensity;

            float shadowCalc(vec4 lsp, vec3 n) {
                vec3 p = lsp.xyz / lsp.w;
                p = p * 0.5 + 0.5;
                if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
                float bias = max(0.006 * (1.0 - dot(n, uSunDir)), 0.0015);
                float current = p.z - bias;
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
                vec4 tex = texture(uTexture, vTexCoord);
                if (tex.a < 0.1) discard;

                vec3 n = normalize(cross(dFdx(vWorld), dFdy(vWorld)));
                if (!gl_FrontFacing) n = -n;
                float diff = max(dot(n, uSunDir), 0.0);

                float shadow = 1.0;
                if (diff > 0.0) shadow = shadowCalc(uLightVP * vec4(vWorld, 1.0), n);

                vec3 lit = tex.rgb * (uAmbient + uSunColor * diff * shadow);
                float dist = length(vWorld - uCamPos);
                float fog = 1.0 - exp(-pow(dist * uFogDensity, 2.0));
                lit = mix(lit, uFogColor, fog);
                FragColor = vec4(lit, tex.a);
            }
            """;

    private static final String SKIN_SHADOW_VERT = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            layout (location = 2) in vec4 aBoneIds;
            layout (location = 3) in vec4 aWeights;

            uniform mat4 uBones[6];
            uniform mat4 uModel;
            uniform mat4 uLightVP;

            out vec2 vTexCoord;

            void main() {
                mat4 skin = aWeights.x * uBones[int(aBoneIds.x)]
                          + aWeights.y * uBones[int(aBoneIds.y)]
                          + aWeights.z * uBones[int(aBoneIds.z)]
                          + aWeights.w * uBones[int(aBoneIds.w)];
                vTexCoord = aTexCoord;
                gl_Position = uLightVP * uModel * skin * vec4(aPos, 1.0);
            }
            """;

    private static final String SKIN_SHADOW_FRAG = """
            #version 330 core
            in vec2 vTexCoord;
            out vec4 FragColor;
            uniform sampler2D uTexture;
            void main() {
                if (texture(uTexture, vTexCoord).a < 0.5) discard;
            }
            """;

    // ---------------- состояние ----------------
    private int vao, vbo, vertexCount;
    private final Texture texture;
    private final Vector3f pos;
    private final float armCutArg, armDropAngle, shoulderArg, armStretchArg, heightWorld, seed;

    private float scale = 1f;
    private float armStretch = 1f;
    private float armOut = 0f;
    private float walkSpeed;

    private final Random rng = new Random();
    private final Vector3f target = new Vector3f();
    private float currentYaw = 0f, time = 0f, phase, walkAmount = 0f, idleTimer;
    private int lastStepIdx = 0;

    private final Matrix4f world = new Matrix4f();
    private final Matrix4f[] bones = new Matrix4f[NUM_BONES];
    private final Vector3f hipP = new Vector3f(), neckP = new Vector3f();
    private final Vector3f shLP = new Vector3f(), shRP = new Vector3f();
    private final Vector3f hipLP = new Vector3f(), hipRP = new Vector3f();

    // ---------------- конструкторы (сигнатуры не менять) ----------------

    public RiggedModel(String objPath, String texPath, float x, float z, float targetHeight) {
        this(objPath, texPath, x, z, targetHeight, -1f, 1.57f, 0.80f, 1.0f, false);
    }

    public RiggedModel(String objPath, String texPath, float x, float z, float targetHeight, float armCutFrac) {
        this(objPath, texPath, x, z, targetHeight, armCutFrac, 1.57f, 0.80f, 1.0f, false);
    }

    public RiggedModel(String objPath, String texPath, float x, float z, float targetHeight,
                       float armCutFrac, float armDropAngle) {
        this(objPath, texPath, x, z, targetHeight, armCutFrac, armDropAngle, 0.80f, 1.0f, false);
    }

    public RiggedModel(String objPath, String texPath, float x, float z, float targetHeight,
                       float armCutFrac, float armDropAngle, float shoulderFrac) {
        this(objPath, texPath, x, z, targetHeight, armCutFrac, armDropAngle, shoulderFrac, 1.0f, false);
    }

    public RiggedModel(String objPath, String texPath, float x, float z, float targetHeight,
                       float armCutFrac, float armDropAngle, float shoulderFrac, float armStretch) {
        this(objPath, texPath, x, z, targetHeight, armCutFrac, armDropAngle, shoulderFrac, armStretch, false);
    }

    /** dropShelf оставлен для совместимости — игнорируется. */
    public RiggedModel(String objPath, String texPath, float x, float z, float targetHeight,
                       float armCutFrac, float armDropAngle, float shoulderFrac,
                       float armStretch, boolean dropShelf) {
        loadConfig();
        if (skinShader == null) {
            skinShader = new ShaderProgram(SKIN_VERT, SKIN_FRAG);
            skinShadowShader = new ShaderProgram(SKIN_SHADOW_VERT, SKIN_SHADOW_FRAG);
        }
        shaderUsers++;

        for (int i = 0; i < NUM_BONES; i++) bones[i] = new Matrix4f();

        this.pos = new Vector3f(x, Ground.getHeightAt(x, z), z);
        this.texture = texPath != null ? new Texture(texPath, false, false) : null;
        this.armCutArg     = armCutFrac;
        this.armDropAngle  = armDropAngle;
        this.shoulderArg   = shoulderFrac;
        this.armStretchArg = armStretch;
        this.heightWorld   = targetHeight;
        this.walkSpeed     = WALK_SPEED_CFG;
        this.seed  = rng.nextFloat() * 10f;
        this.phase = rng.nextFloat() * (float) (Math.PI * 2);
        this.idleTimer = 0.5f + rng.nextFloat() * 2f;

        float modelHeight = loadObj(objPath);
        this.scale = modelHeight > 0.01f ? targetHeight / modelHeight : 1f;

        System.out.println("[RIG] " + new File(objPath).getName()
                + " h=" + modelHeight + " scale=" + scale
                + " stretch=" + armStretch + " out=" + armOut);

        pickNewTarget();
        updateBones();
    }

    // ---------------- конфиг ----------------

    private static void loadConfig() {
        if (cfgLoaded) return;
        cfgLoaded = true;
        File f = new File(CFG_PATH);
        if (!f.exists()) { System.out.println("[RIG] " + CFG_PATH + " нет — defaults"); return; }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                int hash = line.indexOf('#');
                if (hash >= 0) line = line.substring(0, hash);
                line = line.trim();
                if (line.isEmpty()) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String k = line.substring(0, eq).trim();
                String raw = line.substring(eq + 1).trim();
                if (k.startsWith("out_")) {
                    try { OUT_OVERRIDES.put(k.substring(4).toLowerCase(), Float.parseFloat(raw)); }
                    catch (NumberFormatException ignored) { }
                    continue;
                }
                float v;
                try { v = Float.parseFloat(raw); } catch (NumberFormatException e) { continue; }
                switch (k) {
                    case "auto_rig"       -> AUTO_RIG = v > 0.5f;
                    case "neck_y"         -> NECK_FRAC = v;
                    case "hip_y"          -> HIP_FRAC = v;
                    case "shoulder_y"     -> SHOULDER_FRAC = v;
                    case "arm_x"          -> ARM_CUT_FRAC = v;
                    case "fingertip_y"    -> FINGERTIP_FRAC = v;
                    case "arm_stretch"    -> ARM_STRETCH_CFG = v;
                    case "arm_band_ratio" -> ARM_BAND_RATIO = v;
                    case "arm_margin"     -> ARM_MARGIN = v;
                    case "arm_band_pad"   -> ARM_BAND_PAD = v;
                    case "leg_gap"        -> LEG_GAP = v;
                    case "arm_out"        -> ARM_OUT_CFG = v;
                    case "arm_out_margin" -> ARM_OUT_MARGIN = v;
                    case "arm_out_max"    -> ARM_OUT_MAX = v;
                    case "blend_x"        -> BLEND_X = v;
                    case "blend_y"        -> BLEND_Y = v;
                    case "leg_swing"      -> LEG_AMP = v;
                    case "arm_swing"      -> ARM_AMP = v;
                    case "bob"            -> BOB_AMP = v;
                    case "stride"         -> STRIDE_FRAC = v;
                    case "walk_speed"     -> WALK_SPEED_CFG = v;
                    case "lean"           -> LEAN_WALK = v;
                    case "sway"           -> SWAY_AMP = v;
                    case "turn_rate"      -> TURN_RATE = v;
                    case "idle_min"       -> IDLE_MIN = v;
                    case "idle_max"       -> IDLE_MAX = v;
                    case "pelvis_margin"  -> PELVIS_MARGIN = v;
                    case "facing_offset"  -> FACING_OFFSET = v;
                    case "step_sound"     -> STEP_SOUND = v > 0.5f;
                    default -> { }
                }
            }
            System.out.println("[RIG] config: auto_rig=" + AUTO_RIG
                    + " fingertip_y=" + FINGERTIP_FRAC + " arm_out=" + ARM_OUT_CFG);
        } catch (Exception e) {
            System.err.println("[RIG] ошибка чтения " + CFG_PATH + ": " + e.getMessage());
        }
    }

    private static float outOverrideFor(String path) {
        String n = new File(path).getName().toLowerCase();
        for (Map.Entry<String, Float> e : OUT_OVERRIDES.entrySet())
            if (n.contains(e.getKey())) return e.getValue();
        return Float.NaN;
    }

    // ---------------- утилиты ----------------

    private static int idx(String s, int total) {
        int i = Integer.parseInt(s);
        return i < 0 ? total + i : i - 1;
    }

    /** smoothstep, работает и при e1 < e0 (обратный фронт). */
    private static float sstep(float e0, float e1, float v) {
        if (Math.abs(e1 - e0) < 1e-9f) return v >= e1 ? 1f : 0f;
        float t = (v - e0) / (e1 - e0);
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    // ---------------- загрузка + скининг ----------------

    private float loadObj(String path) {
        List<Float> verts = new ArrayList<>();
        List<Float> uvs   = new ArrayList<>();
        List<Integer> fvAll = new ArrayList<>();
        List<Integer> fuAll = new ArrayList<>();

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] t = line.split("\\s+");
                if (t[0].equals("v") && t.length >= 4) {
                    float vx = Float.parseFloat(t[1]);
                    float vy = Float.parseFloat(t[2]);
                    float vz = Float.parseFloat(t[3]);
                    verts.add(vx); verts.add(vy); verts.add(vz);
                    minX = Math.min(minX, vx); maxX = Math.max(maxX, vx);
                    minY = Math.min(minY, vy); maxY = Math.max(maxY, vy);
                    minZ = Math.min(minZ, vz); maxZ = Math.max(maxZ, vz);
                } else if (t[0].equals("vt") && t.length >= 3) {
                    uvs.add(Float.parseFloat(t[1]));
                    uvs.add(Float.parseFloat(t[2]));
                } else if (t[0].equals("f") && t.length >= 4) {
                    List<Integer> fv = new ArrayList<>(), fu = new ArrayList<>();
                    for (int i = 1; i < t.length; i++) {
                        String[] sub = t[i].split("/");
                        fv.add(idx(sub[0], verts.size() / 3));
                        fu.add(sub.length > 1 && !sub[1].isEmpty() ? idx(sub[1], uvs.size() / 2) : 0);
                    }
                    for (int i = 1; i < fv.size() - 1; i++) {
                        fvAll.add(fv.get(0));   fuAll.add(fu.get(0));
                        fvAll.add(fv.get(i));   fuAll.add(fu.get(i));
                        fvAll.add(fv.get(i+1)); fuAll.add(fu.get(i+1));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[RIG] Failed to load OBJ: " + e.getMessage());
            return 1f;
        }
        if (verts.isEmpty() || fvAll.isEmpty()) return 1f;

        float height  = Math.max(maxY - minY, 0.01f);
        float width   = Math.max(maxX - minX, 0.01f);
        float centerX = (minX + maxX) / 2f;
        float centerZ = (minZ + maxZ) / 2f;

        // ---------- профиль меша ----------
        final int NB = 160;
        float[] halfW  = new float[NB];
        float[] minAbs = new float[NB];
        int[]   cnt    = new int[NB];
        Arrays.fill(minAbs, Float.MAX_VALUE);
        for (int i = 0; i < verts.size(); i += 3) {
            float ax = Math.abs(verts.get(i) - centerX);
            float y  = verts.get(i + 1) - minY;
            int b = Math.max(0, Math.min(NB - 1, (int) (y / height * NB)));
            halfW[b] = Math.max(halfW[b], ax);
            minAbs[b] = Math.min(minAbs[b], ax);
            cnt[b]++;
        }

        float neckY, shoulderY, armpitY, armTopY, armCutX, legCutY;

        if (AUTO_RIG) {
            int peak = NB / 2;
            for (int b = NB / 2; b < NB; b++) if (cnt[b] > 0 && halfW[b] > halfW[peak]) peak = b;
            float thr = ARM_BAND_RATIO * Math.max(halfW[peak], 1e-4f);

            int down = peak;
            while (down > 0 && (cnt[down] == 0 || halfW[down] >= thr)) down--;
            int up = peak;
            while (up < NB - 1 && (cnt[up] == 0 || halfW[up] >= thr)) up++;

            armpitY = (down + 0.5f) / NB * height;
            armTopY = (up + 0.5f) / NB * height;
            shoulderY = (armpitY + armTopY) * 0.5f;

            float torsoHalf = 0f;
            int from = Math.max(0, down - (int) (0.10f * NB));
            for (int b = from; b <= down; b++) torsoHalf = Math.max(torsoHalf, halfW[b]);
            armCutX = torsoHalf * ARM_MARGIN;
            if (armCutX < 0.04f * width || armCutX > 0.40f * width) {
                armCutX   = width * (armCutArg > 0f ? armCutArg : ARM_CUT_FRAC);
                shoulderY = (shoulderArg > 0f ? shoulderArg : SHOULDER_FRAC) * height;
                armpitY   = shoulderY - 0.10f * height;
                armTopY   = shoulderY + 0.05f * height;
                System.out.println("[RIG] auto-detect плеча не удался — fallback");
            }

            int neckBin = -1; float best = Float.MAX_VALUE;
            for (int b = (int) (0.72f * NB); b < (int) (0.94f * NB); b++)
                if (cnt[b] > 0 && halfW[b] < best) { best = halfW[b]; neckBin = b; }
            neckY = neckBin >= 0 ? (neckBin + 0.5f) / NB * height : NECK_FRAC * height;
            if (neckY <= shoulderY) neckY = Math.min(0.93f * height, shoulderY + 0.06f * height);

            int hipBin = (int) (HIP_FRAC * NB), gapBin = -1;
            for (int b = 1; b <= hipBin; b++)
                if (cnt[b] > 0 && minAbs[b] > LEG_GAP * width) gapBin = b;
            legCutY = gapBin >= 0 ? (gapBin + 1.0f) / NB * height
                                  : (HIP_FRAC - PELVIS_MARGIN) * height;
            legCutY = Math.max(0.22f * height, Math.min(legCutY, HIP_FRAC * height));
        } else {
            neckY     = NECK_FRAC * height;
            shoulderY = (shoulderArg > 0f ? shoulderArg : SHOULDER_FRAC) * height;
            armCutX   = width * (armCutArg > 0f ? armCutArg : ARM_CUT_FRAC);
            armpitY   = shoulderY - 0.10f * height;
            armTopY   = shoulderY + 0.05f * height;
            legCutY   = (HIP_FRAC - PELVIS_MARGIN) * height;
        }

        float armBandLow = armpitY - ARM_BAND_PAD * height;
        float bandX = BLEND_X * width;
        float bandY = BLEND_Y * height;

        // ---------- длина руки ----------
        float tip = 0f;
        for (int i = 0; i < verts.size(); i += 3) {
            float y = verts.get(i + 1) - minY;
            if (y > armBandLow) tip = Math.max(tip, Math.abs(verts.get(i) - centerX));
        }
        float armLen = tip - armCutX;
        if (armLen < 1e-4f) armLen = (maxX - centerX) - armCutX;

        float dropSin = (float) Math.abs(Math.sin(armDropAngle));
        if (dropSin < 0.2f) dropSin = 1f;
        float needLen = (shoulderY - FINGERTIP_FRAC * height) / dropSin;

        float manual = (armStretchArg > 0.01f && Math.abs(armStretchArg - 1f) > 1e-3f)
                ? armStretchArg : ARM_STRETCH_CFG;
        armStretch = manual > 0.01f ? manual : (armLen > 1e-4f ? needLen / armLen : 1f);
        armStretch = Math.max(0.6f, Math.min(2.2f, armStretch));

        // ---------- отступ руки наружу (чтобы не лезла в трусы) ----------
        float ov = outOverrideFor(path);
        if (!Float.isNaN(ov)) {
            armOut = ov * width;
        } else if (ARM_OUT_CFG >= 0f) {
            armOut = ARM_OUT_CFG * width;
        } else {
            float handY = FINGERTIP_FRAC * height;
            float bodyHalf = 0f;
            for (int i = 0; i < verts.size(); i += 3) {
                float y = verts.get(i + 1) - minY;
                if (y >= handY - 0.02f * height && y <= handY + 0.14f * height)
                    bodyHalf = Math.max(bodyHalf, Math.abs(verts.get(i) - centerX));
            }
            float armThick = Math.max(armTopY - armpitY, 0.02f * height);
            float innerEdge = armCutX - armThick * 0.5f;
            armOut = bodyHalf + ARM_OUT_MARGIN * width - innerEdge;
            armOut = Math.max(0f, Math.min(armOut, ARM_OUT_MAX * width));
        }

        System.out.printf("[RIG] auto: neck=%.3f shoulder=%.3f armpit=%.3f armCut=%.3fW legCut=%.3f%n",
                neckY / height, shoulderY / height, armpitY / height, armCutX / width, legCutY / height);
        System.out.println("[RIG] armLen=" + armLen + " need=" + needLen
                + " stretch=" + armStretch + " out=" + (armOut / width) + "W");

        hipP.set(0, legCutY, 0);
        neckP.set(0, neckY, 0);
        shLP.set(-armCutX, shoulderY, 0);
        shRP.set( armCutX, shoulderY, 0);
        hipLP.set(0, legCutY, 0);
        hipRP.set(0, legCutY, 0);

        // ---------- сборка вершин: rest-поза + веса ----------
        vertexCount = fvAll.size();
        float[] data = new float[vertexCount * 13];
        int o = 0;
        for (int i = 0; i < vertexCount; i++) {
            int v  = fvAll.get(i) * 3;
            int uv = fuAll.get(i) * 2;

            float x = verts.get(v)     - centerX;
            float y = verts.get(v + 1) - minY;
            float z = verts.get(v + 2) - centerZ;
            float ax = Math.abs(x);
            boolean right = x >= 0f;

            // --- веса: разбиение единицы ---
            float wHead = sstep(neckY - bandY, neckY + bandY, y);
            float wArm  = sstep(armCutX - bandX, armCutX + bandX, ax)
                        * sstep(armBandLow - bandY, armBandLow + bandY, y)
                        * (1f - wHead);
            float wLeg  = sstep(legCutY + bandY, legCutY - bandY, y) * (1f - wHead) * (1f - wArm);
            float wTorso = Math.max(0f, 1f - wHead - wArm - wLeg);

            // --- rest-поза: длина и отступ руки, ОБЯЗАТЕЛЬНО * вес ---
            if (wArm > 0f) {
                float pivotX = right ? armCutX : -armCutX;
                float d = x - pivotX;
                boolean outward = right ? d > 0f : d < 0f;
                if (outward) x = pivotX + d * (1f + (armStretch - 1f) * wArm);
                x += (right ? armOut : -armOut) * wArm;
            }

            data[o++] = x;
            data[o++] = y;
            data[o++] = z;
            data[o++] = uv + 1 < uvs.size() ? uvs.get(uv)     : 0f;
            data[o++] = uv + 1 < uvs.size() ? uvs.get(uv + 1) : 0f;
            data[o++] = B_TORSO;
            data[o++] = B_HEAD;
            data[o++] = right ? B_ARM_R : B_ARM_L;
            data[o++] = right ? B_LEG_R : B_LEG_L;
            data[o++] = wTorso;
            data[o++] = wHead;
            data[o++] = wArm;
            data[o++] = wLeg;
        }

        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(data.length);
        try {
            buf.put(data).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(buf);
        }
        int stride = 13 * Float.BYTES;
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 3L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, stride, 5L * Float.BYTES);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(3, 4, GL11.GL_FLOAT, false, stride, 9L * Float.BYTES);
        GL20.glEnableVertexAttribArray(3);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        System.out.println("[RIG] skinned verts=" + vertexCount + " (меш цельный, швов нет)");
        return height;
    }

    // ---------------- логика ----------------

    private void pickNewTarget() {
        float a = rng.nextFloat() * (float) (Math.PI * 2);
        float r = 5f + rng.nextFloat() * 10f;
        target.set(pos.x + (float) Math.cos(a) * r, 0f, pos.z + (float) Math.sin(a) * r);
    }

    public void update(float dt) {
        time += dt;

        float dx = target.x - pos.x;
        float dz = target.z - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        boolean moving = false;

        if (idleTimer > 0f) {
            idleTimer -= dt;
            if (idleTimer <= 0f) pickNewTarget();
        } else if (dist < 0.35f) {
            idleTimer = IDLE_MIN + rng.nextFloat() * Math.max(0f, IDLE_MAX - IDLE_MIN);
        } else {
            moving = true;

            float targetYaw = (float) Math.atan2(dx, dz) + FACING_OFFSET;
            float yawDiff = targetYaw - currentYaw;
            while (yawDiff >  Math.PI) yawDiff -= (float) (Math.PI * 2);
            while (yawDiff < -Math.PI) yawDiff += (float) (Math.PI * 2);
            float turn = Math.max(-TURN_RATE * dt,
                    Math.min(TURN_RATE * dt, yawDiff * Math.min(1f, dt * 6f)));
            currentYaw += turn;

            float nearSlow = Math.max(0.25f, Math.min(1f, dist / 1.2f));
            float turnSlow = Math.max(0.25f, 1f - Math.abs(yawDiff) / (float) Math.PI);
            float ease = 0.35f + 0.65f * walkAmount;
            float step = Math.min(walkSpeed * nearSlow * turnSlow * ease * dt, dist);

            pos.x += dx / dist * step;
            pos.z += dz / dist * step;

            float cycleLen = Math.max(0.5f, heightWorld * STRIDE_FRAC * 2f);
            phase += step / cycleLen * (float) (Math.PI * 2);

            int stepIdx = (int) Math.floor((phase - Math.PI / 2) / Math.PI);
            if (stepIdx != lastStepIdx) {
                lastStepIdx = stepIdx;
                long now = System.currentTimeMillis();
                if (STEP_SOUND && walkAmount > 0.5f && now - lastStepMs > 140L) {
                    lastStepMs = now;
                    Audio.playOnce(STEP_SFX, 0.3f);
                }
            }
        }

        walkAmount += ((moving ? 1f : 0f) - walkAmount) * Math.min(1f, dt * 5f);
        pos.y += (Ground.getHeightAt(pos.x, pos.z) - pos.y) * Math.min(1f, dt * 12f);

        updateBones();
    }

    private static void around(Matrix4f m, Vector3f p, float rx, float ry, float rz) {
        m.translate(p);
        if (ry != 0f) m.rotateY(ry);
        if (rx != 0f) m.rotateX(rx);
        if (rz != 0f) m.rotateZ(rz);
        m.translate(-p.x, -p.y, -p.z);
    }

    private void updateBones() {
        float w = walkAmount;
        float s = (float) Math.sin(phase);

        float bob  = (1f + (float) Math.cos(phase * 2f)) * 0.5f * BOB_AMP * heightWorld * w;
        float sway = s * SWAY_AMP * w;
        float lean = LEAN_WALK * w + 0.012f * (float) Math.sin(time * 1.6f + seed);

        world.identity()
             .translate(pos.x, pos.y + bob, pos.z)
             .rotateY(currentYaw)
             .rotateZ(sway)
             .scale(scale);

        // торс
        bones[B_TORSO].identity();
        around(bones[B_TORSO], hipP, lean, 0f, 0f);

        // голова — наследует торс
        float lookYaw   = (float) Math.sin(time * 0.40f + seed) * 0.45f * (1f - 0.7f * w);
        float lookPitch = (float) Math.sin(time * 0.27f + seed * 1.7f) * 0.08f * (1f - 0.5f * w) - lean * 0.7f;
        bones[B_HEAD].set(bones[B_TORSO]);
        around(bones[B_HEAD], neckP, lookPitch, lookYaw, 0f);

        // руки — наследуют торс
        float armL = -s * ARM_AMP * w;
        float armR =  s * ARM_AMP * w;
        float drop = Math.max(0f, armDropAngle);
        bones[B_ARM_L].set(bones[B_TORSO]);
        around(bones[B_ARM_L], shLP, -armL, 0f,  drop);
        bones[B_ARM_R].set(bones[B_TORSO]);
        around(bones[B_ARM_R], shRP, -armR, 0f, -drop);

        // ноги — без наклона корпуса
        float legL =  s * LEG_AMP * w;
        float legR = -s * LEG_AMP * w;
        bones[B_LEG_L].identity();
        around(bones[B_LEG_L], hipLP, -legL, 0f, 0f);
        bones[B_LEG_R].identity();
        around(bones[B_LEG_R], hipRP, -legR, 0f, 0f);
    }

    // ---------------- рендер ----------------

    public void render(Matrix4f view, Matrix4f proj, Matrix4f lightVP, int shadowTex,
                       Vector3f sunDir, Vector3f sunColor, Vector3f ambient,
                       Vector3f camPos, Vector3f fogColor, float fogDensity) {
        if (vao == 0) return;

        skinShader.bind();
        skinShader.setUniform("uBones", bones);
        skinShader.setUniform("uModel", world);
        skinShader.setUniform("uView", view);
        skinShader.setUniform("uProjection", proj);
        skinShader.setUniform("uLightVP", lightVP);
        skinShader.setUniform1i("uTexture", 0);
        skinShader.setUniform1i("uShadowMap", 1);
        skinShader.setUniform3f("uSunDir", sunDir.x, sunDir.y, sunDir.z);
        skinShader.setUniform3f("uSunColor", sunColor.x, sunColor.y, sunColor.z);
        skinShader.setUniform3f("uAmbient", ambient.x, ambient.y, ambient.z);
        skinShader.setUniform3f("uCamPos", camPos.x, camPos.y, camPos.z);
        skinShader.setUniform3f("uFogColor", fogColor.x, fogColor.y, fogColor.z);
        skinShader.setUniform1f("uFogDensity", fogDensity);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        if (texture != null) texture.bind();

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        GL30.glBindVertexArray(0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        skinShader.unbind();
    }

    /** Вызывать внутри shadow-прохода. */
    public void renderShadow(Matrix4f lightVP) {
        if (vao == 0) return;

        skinShadowShader.bind();
        skinShadowShader.setUniform("uBones", bones);
        skinShadowShader.setUniform("uModel", world);
        skinShadowShader.setUniform("uLightVP", lightVP);
        skinShadowShader.setUniform1i("uTexture", 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        if (texture != null) texture.bind();

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        GL30.glBindVertexArray(0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        skinShadowShader.unbind();
    }

    public Vector3f position() { return pos; }
    public float height()      { return heightWorld; }
    public void setWalkSpeed(float v) { walkSpeed = v; }

    public void destroy() {
        if (vao != 0) {
            GL15.glDeleteBuffers(vbo);
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (texture != null) texture.destroy();
        if (--shaderUsers <= 0 && skinShader != null) {
            skinShader.destroy();
            skinShadowShader.destroy();
            skinShader = null;
            skinShadowShader = null;
            shaderUsers = 0;
        }
    }
}
