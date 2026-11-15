package com.example.engine;

import org.lwjgl.opengl.GL11;

/**
 * Simple wrapper around an OpenGL texture created from in-memory pixels
 * (not from a file). Used by TextRenderer.
 */
public final class UiTexture {
    public final int id;
    public final int width;
    public final int height;

    public UiTexture(int id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    public void destroy() {
        GL11.glDeleteTextures(id);
    }
}
