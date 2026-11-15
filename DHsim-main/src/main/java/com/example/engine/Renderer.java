package com.example.engine;

import org.lwjgl.opengl.GL11;

/**
 * Minimal renderer: clears the framebuffer and toggles depth testing.
 */
public final class Renderer {

    public void init() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glClearColor(0.08f, 0.09f, 0.12f, 1.0f);
    }

    public void clear() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }
}
