package com.example.engine;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Random;

public final class Texture {
    private final int id;
    private final int pixelWidth;
    private final int pixelHeight;

    public final float uMin, vMin, uMax, vMax;

    public Texture(String filePath) {
        this(filePath, false, true);
    }

    public Texture(String filePath, boolean repeat, boolean cropContent) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            STBImage.stbi_set_flip_vertically_on_load(true);
            ByteBuffer image = STBImage.stbi_load(filePath, w, h, channels, 4);
            if (image == null) {
                throw new RuntimeException("Failed to load texture: " + filePath + "\n"
                        + STBImage.stbi_failure_reason());
            }

            pixelWidth = w.get(0);
            pixelHeight = h.get(0);

            if (cropContent) {
                int minX = pixelWidth, minY = pixelHeight, maxX = -1, maxY = -1;
                for (int y = 0; y < pixelHeight; y++) {
                    for (int x = 0; x < pixelWidth; x++) {
                        int i = (y * pixelWidth + x) * 4;
                        int r = image.get(i) & 0xFF;
                        int g = image.get(i + 1) & 0xFF;
                        int b = image.get(i + 2) & 0xFF;
                        int a = image.get(i + 3) & 0xFF;

                        boolean transparent = a < 16;
                        boolean nearWhite = r > 238 && g > 238 && b > 238;
                        if (transparent || nearWhite) continue;

                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
                if (maxX >= minX && maxY >= minY) {
                    uMin = (float) minX / pixelWidth;
                    uMax = (float) (maxX + 1) / pixelWidth;
                    vMin = (float) minY / pixelHeight;
                    vMax = (float) (maxY + 1) / pixelHeight;
                } else {
                    uMin = 0; vMin = 0; uMax = 1; vMax = 1;
                }
            } else {
                uMin = 0; vMin = 0; uMax = 1; vMax = 1;
            }

            id = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

            int wrap = repeat ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, pixelWidth, pixelHeight, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);

            STBImage.stbi_image_free(image);
        }
    }

    /** Procedural pixel-art texture constructor. */
    public Texture(int width, int height, int[] pixels, boolean pixelated) {
        this.pixelWidth = width;
        this.pixelHeight = height;
        this.uMin = 0; this.vMin = 0; this.uMax = 1; this.vMax = 1;

        id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);

        if (pixelated) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        } else {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);
        for (int y = height - 1; y >= 0; y--) { // flip Y for OpenGL
            for (int x = 0; x < width; x++) {
                int p = pixels[y * width + x];
                buffer.put((byte)((p >> 16) & 0xFF)); // R
                buffer.put((byte)((p >> 8) & 0xFF));  // G
                buffer.put((byte)(p & 0xFF));         // B
                buffer.put((byte)((p >> 24) & 0xFF)); // A
            }
        }
        buffer.flip();

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        MemoryUtil.memFree(buffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    /** Generates a Minecraft-style leaf texture with transparency holes. */
    public static Texture generateLeaves() {
        int w = 64, h = 64;
        int[] px = new int[w * h];
        Random rng = new Random();
        int[] palette = {
                0xFF2E5C31, // dark
                0xFF3A7D44, // base
                0xFF4E9F3D, // light
                0xFF61C454, // highlight
                0xFF1B3B1E  // very dark
        };
        for (int i = 0; i < px.length; i++) {
            float r = rng.nextFloat();
            if (r < 0.15f) {
                px[i] = 0x00000000; // transparent hole
            } else {
                px[i] = palette[rng.nextInt(palette.length)];
            }
        }
        return new Texture(w, h, px, true);
    }

    public int getId() { return id; }

    public float contentAspect() {
        float w = (uMax - uMin) * pixelWidth;
        float h = (vMax - vMin) * pixelHeight;
        if (h <= 0.0001f) return 1f;
        return w / h;
    }

    public float aspect() {
        return (float) pixelWidth / pixelHeight;
    }

    public void bind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }

    public void destroy() {
        GL11.glDeleteTextures(id);
    }
}
