package com.example.engine;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ObjModel {

    private final List<Part> parts = new ArrayList<>();
    private float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
    private float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
    private float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

    private static class Part {
        int vao, vbo, vertexCount;
        Texture texture;
        Part(int vao, int vbo, int count, Texture tex) {
            this.vao = vao; this.vbo = vbo; this.vertexCount = count; this.texture = tex;
        }
    }

    public ObjModel(String objPath, String mtlPath, String defaultTexPath) {
        Map<String, String> mtlMap = new HashMap<>();
        if (mtlPath != null) parseMtl(mtlPath, mtlMap);
        parseObj(objPath, mtlPath, defaultTexPath, mtlMap);
    }

    public void applyFallbackTexture(Texture tex) {
        for (Part p : parts) if (p.texture == null) p.texture = tex;
    }

    public void setTexture(Texture tex) {
        for (Part p : parts) {
            if (p.texture != null) p.texture.destroy();
            p.texture = tex;
        }
    }

    private void parseMtl(String mtlPath, Map<String, String> map) {
        try (BufferedReader br = new BufferedReader(new FileReader(mtlPath))) {
            String line;
            String currentMtl = null;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("newmtl ")) {
                    currentMtl = line.substring(7).trim();
                } else if (line.startsWith("map_Kd ") && currentMtl != null) {
                    String filename = line.substring(7).trim();
                    String cleanName = new File(filename).getName();
                    int lastSlash = mtlPath.lastIndexOf('/');
                    String path = (lastSlash >= 0) ? mtlPath.substring(0, lastSlash + 1) + cleanName : cleanName;
                    map.put(currentMtl, path);
                }
            }
        } catch (Exception e) { }
    }

    private static int parseIndex(String s, int total) {
        int idx = Integer.parseInt(s);
        if (idx < 0) return total + idx;
        return idx - 1;
    }

    private void parseObj(String objPath, String mtlPath, String defaultTexPath, Map<String, String> mtlMap) {
        List<Float> verts = new ArrayList<>();
        List<Float> uvs = new ArrayList<>();
        Map<String, List<Integer>> vIdxMap = new LinkedHashMap<>();
        Map<String, List<Integer>> uvIdxMap = new LinkedHashMap<>();

        String currentMat = "default";

        try (BufferedReader br = new BufferedReader(new FileReader(objPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v" -> {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float z = Float.parseFloat(parts[3]);
                        verts.add(x); verts.add(y); verts.add(z);
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    }
                    case "vt" -> {
                        uvs.add(Float.parseFloat(parts[1]));
                        uvs.add(Float.parseFloat(parts[2]));
                    }
                    case "usemtl" -> currentMat = parts.length > 1 ? parts[1].trim() : "default";
                    case "f" -> {
                        List<Integer> faceV = new ArrayList<>();
                        List<Integer> faceUV = new ArrayList<>();
                        for (int i = 1; i < parts.length; i++) {
                            String[] sub = parts[i].split("/");
                            faceV.add(parseIndex(sub[0], verts.size() / 3));
                            faceUV.add(sub.length > 1 && !sub[1].isEmpty()
                                    ? parseIndex(sub[1], uvs.size() / 2) : -1);
                        }
                        vIdxMap.putIfAbsent(currentMat, new ArrayList<>());
                        uvIdxMap.putIfAbsent(currentMat, new ArrayList<>());
                        List<Integer> vList = vIdxMap.get(currentMat);
                        List<Integer> uvList = uvIdxMap.get(currentMat);
                        for (int i = 1; i < faceV.size() - 1; i++) {
                            vList.add(faceV.get(0)); uvList.add(faceUV.get(0));
                            vList.add(faceV.get(i)); uvList.add(faceUV.get(i));
                            vList.add(faceV.get(i + 1)); uvList.add(faceUV.get(i + 1));
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load OBJ: " + objPath, e);
        }

        for (String mat : vIdxMap.keySet()) {
            List<Integer> vIndices = vIdxMap.get(mat);
            List<Integer> uvIndices = uvIdxMap.get(mat);
            if (vIndices.isEmpty()) continue;

            int vertexCount = vIndices.size();
            float[] data = new float[vertexCount * 5];
            for (int i = 0; i < vertexCount; i++) {
                int vIdx = vIndices.get(i) * 3;
                int uvRaw = uvIndices.get(i);
                int uvIdx = uvRaw * 2;
                int offset = i * 5;
                data[offset]     = verts.get(vIdx);
                data[offset + 1] = verts.get(vIdx + 1);
                data[offset + 2] = verts.get(vIdx + 2);
                float u = 0, v = 0;
                if (uvRaw >= 0 && uvIdx + 1 < uvs.size()) {
                    u = uvs.get(uvIdx); v = uvs.get(uvIdx + 1);
                }
                data[offset + 3] = u;
                data[offset + 4] = v;
            }

            int vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            int vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            FloatBuffer buf = MemoryUtil.memAllocFloat(data.length);
            try { buf.put(data).flip(); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW); }
            finally { MemoryUtil.memFree(buf); }

            int stride = 5 * Float.BYTES;
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 3L * Float.BYTES);
            GL20.glEnableVertexAttribArray(1);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);

            String texPath = mtlMap.getOrDefault(mat, defaultTexPath);
            Texture tex = null;
            if (texPath != null) {
                try { tex = new Texture(texPath, true, false); } catch (Exception e) { }
            }
            this.parts.add(new Part(vao, vbo, vertexCount, tex));
        }
    }

    public void render() {
        for (Part p : parts) {
            if (p.texture != null) p.texture.bind();
            GL30.glBindVertexArray(p.vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, p.vertexCount);
            GL30.glBindVertexArray(0);
        }
    }

    public void destroy() {
        for (Part p : parts) {
            if (p.texture != null) p.texture.destroy();
            GL15.glDeleteBuffers(p.vbo);
            GL30.glDeleteVertexArrays(p.vao);
        }
    }

    public float getHeight() { return maxY - minY; }
    public float getWidth()  { return maxX - minX; }
    public float getDepth()  { return maxZ - minZ; }
}
