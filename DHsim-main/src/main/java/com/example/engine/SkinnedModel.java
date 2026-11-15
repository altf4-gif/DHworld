package com.example.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * True skeletal animation model using Linear Blend Skinning (LBS).
 * No geometry cutting. Vertices are assigned to 7 bones based on distance.
 */
public final class SkinnedModel {

    private final int vao;
    private final int vbo;
    private final int vertexCount;
    private final Texture texture;
    private final ShaderProgram shader;
    
    // World transform
    private final Vector3f pos;
    private float scale;
    private float currentYaw = 0f;
    private float time = 0f;
    private float walkTimer = 0f;
    private final Random rng = new Random();
    private final Vector3f target = new Vector3f();
    private final float walkSpeed = 1.5f;

    // Skeleton data
    private static final int NUM_BONES = 7;
    private final Matrix4f[] boneMatrices = new Matrix4f[NUM_BONES];
    private final Matrix4f worldMatrix = new Matrix4f();

    // Bone local positions (relative to feet, centered X/Z)
    private float boneNeckY, boneHipY, boneShoulderY, boneArmX, boneLegX;

    private static final String VERT = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            layout (location = 2) in vec4 aBoneIDs;
            layout (location = 3) in vec4 aWeights;

            uniform mat4 uBones[7];
            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProjection;

            out vec2 vTexCoord;
            out vec3 vWorld;

            void main() {
                mat4 skinMat = 
                    aWeights.x * uBones[int(aBoneIDs.x)] +
                    aWeights.y * uBones[int(aBoneIDs.y)] +
                    aWeights.z * uBones[int(aBoneIDs.z)] +
                    aWeights.w * uBones[int(aBoneIDs.w)];

                vec4 localPos = skinMat * vec4(aPos, 1.0);
                vec4 world = uModel * localPos;
                
                vWorld = world.xyz;
                vTexCoord = aTexCoord;
                gl_Position = uProjection * uView * world;
            }
            """;

    private static final String FRAG = """
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
                
                float bias = max(0.05 * (1.0 - dot(n, uSunDir)), 0.005);
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
                if (tex.a < 0.5) discard;
                if (tex.r > 0.93 && tex.g > 0.93 && tex.b > 0.93) discard;

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

    public SkinnedModel(String objPath, String texPath, float x, float z, float targetHeight) {
        this.pos = new Vector3f(x, Ground.getHeightAt(x, z), z);
        this.texture = texPath != null ? new Texture(texPath, false, false) : null;
        this.shader = new ShaderProgram(VERT, FRAG);
        
        for (int i = 0; i < NUM_BONES; i++) {
            boneMatrices[i] = new Matrix4f();
        }

        // Parse OBJ and generate skin data
        List<Float> verts = new ArrayList<>();
        List<Float> uvs = new ArrayList<>();
        List<Integer> faceV = new ArrayList<>();
        List<Integer> faceUV = new ArrayList<>();

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        try (BufferedReader br = new BufferedReader(new FileReader(objPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] tokens = line.split("\\s+");
                if (tokens[0].equals("v")) {
                    float vx = Float.parseFloat(tokens[1]);
                    float vy = Float.parseFloat(tokens[2]);
                    float vz = Float.parseFloat(tokens[3]);
                    verts.add(vx); verts.add(vy); verts.add(vz);
                    minX = Math.min(minX, vx); maxX = Math.max(maxX, vx);
                    minY = Math.min(minY, vy); maxY = Math.max(maxY, vy);
                    minZ = Math.min(minZ, vz); maxZ = Math.max(maxZ, vz);
                } else if (tokens[0].equals("vt")) {
                    uvs.add(Float.parseFloat(tokens[1]));
                    uvs.add(Float.parseFloat(tokens[2]));
                } else if (tokens[0].equals("f")) {
                    for (int i = 1; i < tokens.length; i++) {
                        String[] sub = tokens[i].split("/");
                        faceV.add(Integer.parseInt(sub[0]) - 1);
                        faceUV.add(sub.length > 1 && !sub[1].isEmpty() ? Integer.parseInt(sub[1]) - 1 : 0);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SKIN] Failed to load OBJ: " + e.getMessage());
            this.vao = 0; this.vbo = 0; this.vertexCount = 0;
            return;
        }

        float height = maxY - minY;
        if (height <= 0.01f) height = 1.0f;
        this.scale = targetHeight / height;
        
        float centerX = (minX + maxX) / 2f;
        float centerZ = (minZ + maxZ) / 2f;

        // Setup skeleton proportions
        boneNeckY = 0.85f * height;
        boneHipY = 0.45f * height;
        boneShoulderY = 0.80f * height;
        boneArmX = 0.20f * height;
        boneLegX = 0.10f * height;

        // Bone points in local space (X, Y, Z)
        float[][] pts = {
            {0, boneHipY, 0},                // 0: Root (Hips)
            {0, (boneHipY + boneNeckY) / 2, 0}, // 1: Torso (Mid)
            {0, boneNeckY, 0},               // 2: Head
            {-boneArmX, boneShoulderY, 0},   // 3: Arm L
            {boneArmX, boneShoulderY, 0},    // 4: Arm R
            {-boneLegX, boneHipY / 2, 0},    // 5: Leg L
            {boneLegX, boneHipY / 2, 0}      // 6: Leg R
        };

        // Triangulate
        int triCount = faceV.size() / 3;
        float[] data = new float[triCount * 3 * 13]; // 3 pos, 2 uv, 4 ids, 4 weights = 13
        int outIdx = 0;

        for (int i = 0; i < faceV.size(); i++) {
            int vIdx = faceV.get(i) * 3;
            int uvIdx = faceUV.get(i) * 2;
            
            float vx = verts.get(vIdx) - centerX;
            float vy = verts.get(vIdx + 1) - minY;
            float vz = verts.get(vIdx + 2) - centerZ;

            // Calculate weights based on inverse distance to bone points
            float[] w = new float[NUM_BONES];
            float sum = 0;
            for (int b = 0; b < NUM_BONES; b++) {
                float dx = vx - pts[b][0];
                float dy = vy - pts[b][1];
                float dz = vz - pts[b][2];
                float d2 = dx*dx + dy*dy + dz*dz;
                // Power of 4 falloff for sharper cuts
                w[b] = 1.0f / (d2*d2 + 0.001f);
                sum += w[b];
            }

            // Find top 4 weights
            int[] bestBones = new int[4];
            float[] bestWeights = new float[4];
            for (int j = 0; j < 4; j++) {
                int maxIdx = -1;
                float maxW = -1;
                for (int b = 0; b < NUM_BONES; b++) {
                    if (w[b] > maxW) { maxW = w[b]; maxIdx = b; }
                }
                bestBones[j] = maxIdx;
                bestWeights[j] = maxW;
                w[maxIdx] = -1; // mark as used
            }

            // Normalize top 4
            float wSum = bestWeights[0] + bestWeights[1] + bestWeights[2] + bestWeights[3];
            if (wSum < 1e-6f) wSum = 1f;
            
            data[outIdx++] = vx;
            data[outIdx++] = vy;
            data[outIdx++] = vz;
            data[outIdx++] = uvs.get(uvIdx);
            data[outIdx++] = uvs.get(uvIdx + 1);
            data[outIdx++] = bestBones[0];
            data[outIdx++] = bestBones[1];
            data[outIdx++] = bestBones[2];
            data[outIdx++] = bestBones[3];
            data[outIdx++] = bestWeights[0] / wSum;
            data[outIdx++] = bestWeights[1] / wSum;
            data[outIdx++] = bestWeights[2] / wSum;
            data[outIdx++] = bestWeights[3] / wSum;
        }

        this.vertexCount = triCount * 3;
        this.vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        this.vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(data.length);
        buf.put(data).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(buf);

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

        pickNewTarget();
    }

    private void pickNewTarget() {
        float a = rng.nextFloat() * (float) (Math.PI * 2);
        float r = 5f + rng.nextFloat() * 10f;
        target.set(pos.x + (float) Math.cos(a) * r, 0, pos.z + (float) Math.sin(a) * r);
    }

    public void update(float dt) {
        time += dt;
        float dx = target.x - pos.x;
        float dz = target.z - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.5f) {
            pickNewTarget();
            walkTimer += dt * 3f; 
        } else {
            float step = Math.min(walkSpeed * dt, dist);
            pos.x += (dx / dist) * step;
            pos.z += (dz / dist) * step;
            
            float targetYaw = (float) Math.atan2(dz, dx) - (float) (Math.PI / 2);
            float yawDiff = targetYaw - currentYaw;
            while (yawDiff > Math.PI) yawDiff -= Math.PI * 2;
            while (yawDiff < -Math.PI) yawDiff += Math.PI * 2;
            currentYaw += yawDiff * Math.min(1f, dt * 5f);

            walkTimer += dt * 8f;
        }
        pos.y = Ground.getHeightAt(pos.x, pos.z);
    }

    public void render(Matrix4f view, Matrix4f proj, int shadowMapTex,
                       Vector3f sunDir, Vector3f sunColor, Vector3f ambient,
                       Vector3f camPos, Vector3f fogColor, float fogDensity) {
        if (vao == 0) return;

        float bob = (float) Math.abs(Math.sin(walkTimer)) * 0.08f;
        worldMatrix.identity().translate(pos.x, pos.y + bob, pos.z).rotateY(currentYaw).scale(scale);

        float swing = (float) Math.sin(walkTimer) * 0.8f;
        
        // Update bone matrices (Local rotations around joints)
        boneMatrices[0].identity(); // Root
        boneMatrices[1].identity(); // Torso
        
        boneMatrices[2].identity()
            .translate(0, boneNeckY, 0)
            .rotateY((float) Math.sin(time * 1.5f) * 0.3f)
            .rotateX((float) Math.cos(time * 1.2f) * 0.1f)
            .translate(0, -boneNeckY, 0);

        boneMatrices[3].identity()
            .translate(-boneArmX, boneShoulderY, 0)
            .rotateX(swing)
            .rotateZ(1.5f)
            .translate(boneArmX, -boneShoulderY, 0);

        boneMatrices[4].identity()
            .translate(boneArmX, boneShoulderY, 0)
            .rotateX(-swing)
            .rotateZ(-1.5f)
            .translate(-boneArmX, -boneShoulderY, 0);

        boneMatrices[5].identity()
            .translate(-boneLegX, boneHipY, 0)
            .rotateX(-swing)
            .translate(boneLegX, -boneHipY, 0);

        boneMatrices[6].identity()
            .translate(boneLegX, boneHipY, 0)
            .rotateX(swing)
            .translate(-boneLegX, -boneHipY, 0);

        shader.bind();
        shader.setUniform("uModel", worldMatrix);
        shader.setUniform("uView", view);
        shader.setUniform("uProjection", proj);
        
        // Upload bone matrices using the new convenient method
        shader.setUniform("uBones", boneMatrices);

        shader.setUniform1i("uShadowMap", 1);
        shader.setUniform1i("uTexture", 0);
        shader.setUniform3f("uSunDir", sunDir.x, sunDir.y, sunDir.z);
        shader.setUniform3f("uSunColor", sunColor.x, sunColor.y, sunColor.z);
        shader.setUniform3f("uAmbient", ambient.x, ambient.y, ambient.z);
        shader.setUniform3f("uCamPos", camPos.x, camPos.y, camPos.z);
        shader.setUniform3f("uFogColor", fogColor.x, fogColor.y, fogColor.z);
        shader.setUniform1f("uFogDensity", fogDensity);

        if (texture != null) texture.bind();
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        GL30.glBindVertexArray(0);
        shader.unbind();
    }

    public void destroy() {
        if (vao != 0) {
            GL15.glDeleteBuffers(vbo);
            GL30.glDeleteVertexArrays(vao);
        }
        if (texture != null) texture.destroy();
        shader.destroy();
    }
}
