package com.example.engine;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

/**
 * Creates and owns a fullscreen GLFW window with an OpenGL 3.3 core context.
 *
 * The window goes fullscreen on the primary monitor at its native resolution
 * right after creation. width()/height() report the live framebuffer size, so
 * everything (camera projection, UI ortho, spawn-menu hit tests) adapts to the
 * actual resolution automatically.
 */
public final class Window {
    private final long handle;

    public Window(int fallbackWidth, int fallbackHeight, String title) {
        this.handle = createHandle(fallbackWidth, fallbackHeight, title);
    }

    private static long createHandle(int width, int height, String title) {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        long handle = GLFW.glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (handle == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        GLFW.glfwMakeContextCurrent(handle);
        GLFW.glfwSwapInterval(1);

        // ---- Fullscreen on the primary monitor ----
        long primary = GLFW.glfwGetPrimaryMonitor();
        if (primary != 0) {
            GLFWVidMode mode = GLFW.glfwGetVideoMode(primary);
            if (mode != null) {
                GLFW.glfwSetWindowMonitor(handle, primary, 0, 0,
                        mode.width(), mode.height(), mode.refreshRate());
            }
        }

        GLFW.glfwShowWindow(handle);
        GL.createCapabilities();
        return handle;
    }

    public long handle() {
        return handle;
    }

    /** Live framebuffer width in pixels (changes when switching to fullscreen). */
    public int width() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(handle, w, h);
            return w.get(0);
        }
    }

    /** Live framebuffer height in pixels. */
    public int height() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(handle, w, h);
            return h.get(0);
        }
    }

    public float aspectRatio() {
        int w = width();
        int h = height();
        return h == 0 ? 1f : (float) w / (float) h;
    }

    public boolean shouldClose() {
        return GLFW.glfwWindowShouldClose(handle);
    }

    public boolean isKeyPressed(int key) {
        return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
    }

    public boolean isMouseButtonPressed(int button) {
        return GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
    }

    /**
     * Cursor position scaled to framebuffer pixels, origin at the top-left.
     * This matches what {@link #width()} / {@link #height()} report, so 2D UI
     * hit tests line up even on high-DPI displays.
     */
    public double[] cursorPos() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(handle, x, y);

            IntBuffer ww = stack.mallocInt(1);
            IntBuffer wh = stack.mallocInt(1);
            GLFW.glfwGetWindowSize(handle, ww, wh);

            IntBuffer fw = stack.mallocInt(1);
            IntBuffer fh = stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(handle, fw, fh);

            double sx = ww.get(0) > 0 ? (double) fw.get(0) / ww.get(0) : 1.0;
            double sy = wh.get(0) > 0 ? (double) fh.get(0) / wh.get(0) : 1.0;

            return new double[]{ x.get(0) * sx, y.get(0) * sy };
        }
    }

    public void setCursorDisabled(boolean disabled) {
        GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR,
                disabled ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
    }

    public void pollEvents() {
        GLFW.glfwPollEvents();
    }

    public void swapBuffers() {
        GLFW.glfwSwapBuffers(handle);
    }

    public void destroy() {
        GLFW.glfwDestroyWindow(handle);
        GLFW.glfwTerminate();
        GLFWErrorCallback cb = GLFW.glfwSetErrorCallback(null);
        if (cb != null) {
            cb.free();
        }
    }
}
