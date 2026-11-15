package com.example.engine;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Building with a rectangular body and a pyramidal (hip) roof.
 *
 * All four walls share the tiled brick texture (Hos_L).
 * The entrance (Hos_V: doors + HOSPITAL sign) is drawn as a
 * separate decal quad at the bottom-center of the front wall.
 */
public final class House {
    private final int vaoWalls, vaoEntrance, vaoRoof;
    private final int wallsCount, entranceCount, roofCount;
    private final Texture texWalls, texEntrance, texRoof;
    private final Matrix4f model;

    private static final float OVERHANG = 0.4f;
    private static final float TILE     = 3.0f;
    /** How far in front of the wall to place the entrance decal. */
    private static final float DECAL_Z  = 0.02f;

    public House(float x, float z, float yaw,
                 float width, float depth, float height, float roofHeight,
                 Texture texWalls, Texture texEntrance, Texture texRoof) {
        this.texWalls = texWalls;
        this.texEntrance = texEntrance;
        this.texRoof = texRoof;
        this.model = new Matrix4f().translate(x, 0, z).rotateY(yaw);

        float hw = width / 2f;
        float hd = depth / 2f;

        // ---------- WALLS: all four faces use Hos_L, tiled ----------
        MeshBuilder w = new MeshBuilder();

        // Back (-Z)
        w.quad(v(-hw, 0, -hd), v(hw, 0, -hd), v(hw, height, -hd), v(-hw, height, -hd),
               uv(0, 0), uv(width / TILE, 0),
               uv(width / TILE, height / TILE), uv(0, height / TILE));

        // Left (-X)
        w.quad(v(-hw, 0, hd), v(-hw, 0, -hd), v(-hw, height, -hd), v(-hw, height, hd),
               uv(0, 0), uv(depth / TILE, 0),
               uv(depth / TILE, height / TILE), uv(0, height / TILE));

        // Right (+X)
        w.quad(v(hw, 0, -hd), v(hw, 0, hd), v(hw, height, hd), v(hw, height, -hd),
               uv(0, 0), uv(depth / TILE, 0),
               uv(depth / TILE, height / TILE), uv(0, height / TILE));

        // Front (+Z) — same brick tiling, the entrance goes on top of it
        w.quad(v(-hw, 0, hd), v(hw, 0, hd), v(hw, height, hd), v(-hw, height, hd),
               uv(0, 0), uv(width / TILE, 0),
               uv(width / TILE, height / TILE), uv(0, height / TILE));

        vaoWalls = createMesh(w);
        wallsCount = w.vertexCount();

        // ---------- ENTRANCE DECAL: bottom-center of the front wall ----------
        // Keep Hos_V's native aspect ratio, anchored to the ground.
        float entranceHeight = height * 0.55f;
        float entranceWidth  = entranceHeight * texEntrance.aspect();

        // Clamp to the wall width (with a small margin).
        float maxWidth = width * 0.85f;
        if (entranceWidth > maxWidth) {
            entranceWidth  = maxWidth;
            entranceHeight = entranceWidth / texEntrance.aspect();
        }

        float ex = entranceWidth / 2f;
        float ez = hd + DECAL_Z;

        MeshBuilder e = new MeshBuilder();
        e.quad(v(-ex, 0, ez), v(ex, 0, ez), v(ex, entranceHeight, ez), v(-ex, entranceHeight, ez),
               uv(0, 0), uv(1, 0), uv(1, 1), uv(0, 1));
        vaoEntrance = createMesh(e);
        entranceCount = e.vertexCount();

        // ---------- ROOF: 4-sided pyramid ----------
        float eaveX = hw + OVERHANG;
        float eaveZ = hd + OVERHANG;
        float apexY = height + roofHeight;
        float[] apex = v(0, apexY, 0);

        float slopeX = (float) Math.sqrt(eaveX * eaveX + roofHeight * roofHeight);
        float slopeZ = (float) Math.sqrt(eaveZ * eaveZ + roofHeight * roofHeight);

        MeshBuilder r = new MeshBuilder();

        // Front (+Z)
        r.tri(v(-eaveX, height, eaveZ), v(eaveX, height, eaveZ), apex,
              uv(0, 0), uv(2 * eaveX / TILE, 0), uv(eaveX / TILE, slopeX / TILE));

        // Back (-Z)
        r.tri(v(eaveX, height, -eaveZ), v(-eaveX, height, -eaveZ), apex,
              uv(0, 0), uv(2 * eaveX / TILE, 0), uv(eaveX / TILE, slopeX / TILE));

        // Right (+X)
        r.tri(v(eaveX, height, eaveZ), v(eaveX, height, -eaveZ), apex,
              uv(0, 0), uv(2 * eaveZ / TILE, 0), uv(eaveZ / TILE, slopeZ / TILE));

        // Left (-X)
        r.tri(v(-eaveX, height, -eaveZ), v(-eaveX, height, eaveZ), apex,
              uv(0, 0), uv(2 * eaveZ / TILE, 0), uv(eaveZ / TILE, slopeZ / TILE));

        vaoRoof = createMesh(r);
        roofCount = r.vertexCount();
    }

    public void render(ShaderProgram shader, Matrix4f view, Matrix4f projection) {
        shader.setUniform("uModel", model);
        shader.setUniform("uView", view);
        shader.setUniform("uProjection", projection);

        texWalls.bind();
        GL30.glBindVertexArray(vaoWalls);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, wallsCount);
        GL30.glBindVertexArray(0);

        texEntrance.bind();
        GL30.glBindVertexArray(vaoEntrance);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, entranceCount);
        GL30.glBindVertexArray(0);

        texRoof.bind();
        GL30.glBindVertexArray(vaoRoof);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, roofCount);
        GL30.glBindVertexArray(0);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private static float[] v(float x, float y, float z) { return new float[]{x, y, z}; }
    private static float[] uv(float u, float v)         { return new float[]{u, v}; }

    private static int createMesh(MeshBuilder mb) {
        float[] data = mb.toArray();
        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(data.length);
        try {
            buf.put(data).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(buf);
        }
        final int stride = 5 * Float.BYTES;
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 3L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
        return vao;
    }

    private static final class MeshBuilder {
        private final List<Float> data = new ArrayList<>(64);

        void quad(float[] p1, float[] p2, float[] p3, float[] p4,
                  float[] uv1, float[] uv2, float[] uv3, float[] uv4) {
            add(p1, uv1); add(p2, uv2); add(p3, uv3);
            add(p1, uv1); add(p3, uv3); add(p4, uv4);
        }

        void tri(float[] p1, float[] p2, float[] p3,
                 float[] uv1, float[] uv2, float[] uv3) {
            add(p1, uv1); add(p2, uv2); add(p3, uv3);
        }

        private void add(float[] p, float[] uv) {
            data.add(p[0]); data.add(p[1]); data.add(p[2]);
            data.add(uv[0]); data.add(uv[1]);
        }

        float[] toArray() {
            float[] arr = new float[data.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = data.get(i);
            return arr;
        }

        int vertexCount() { return data.size() / 5; }
    }
}
