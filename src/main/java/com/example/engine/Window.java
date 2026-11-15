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
 * Окно игры. Фуллскрин БЕЗРАМОЧНЫЙ: окно размером с монитор без рамки,
 * видеорежим НЕ переключается — alt-tab и сворачивание ничего не ломают.
 * F11 — переключение фуллскрин <-> окно. API совместим со старым Window.
 */
public final class Window {
    private final long handle;
    private final int windowedW, windowedH;
    private boolean fullscreen;

    public Window(int fallbackWidth, int fallbackHeight, String title) {
        this.windowedW = fallbackWidth;
        this.windowedH = fallbackHeight;

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

        long monitor = GLFW.glfwGetPrimaryMonitor();
        GLFWVidMode mode = monitor != 0 ? GLFW.glfwGetVideoMode(monitor) : null;
        int w = mode != null ? mode.width() : fallbackWidth;
        int h = mode != null ? mode.height() : fallbackHeight;
        fullscreen = mode != null;

        GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, fullscreen ? GLFW.GLFW_FALSE : GLFW.GLFW_TRUE);
        handle = GLFW.glfwCreateWindow(w, h, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (handle == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        GLFW.glfwSetWindowPos(handle, 0, 0);
        GLFW.glfwMakeContextCurrent(handle);
        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(handle);
        GL.createCapabilities();
    }

    /** F11: безрамочный фуллскрин <-> обычное окно по центру. */
    public void toggleFullscreen() {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        GLFWVidMode mode = monitor != 0 ? GLFW.glfwGetVideoMode(monitor) : null;
        if (mode == null) return;
        fullscreen = !fullscreen;
        if (fullscreen) {
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowPos(handle, 0, 0);
            GLFW.glfwSetWindowSize(handle, mode.width(), mode.height());
        } else {
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
            GLFW.glfwSetWindowSize(handle, windowedW, windowedH);
            GLFW.glfwSetWindowPos(handle,
                    (mode.width() - windowedW) / 2, (mode.height() - windowedH) / 2);
        }
    }

    public long handle() { return handle; }

    public int width() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(handle, w, h);
            return w.get(0);
        }
    }

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

    public boolean shouldClose() { return GLFW.glfwWindowShouldClose(handle); }

    public boolean isKeyPressed(int key) {
        return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
    }

    public boolean isMouseButtonPressed(int button) {
        return GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
    }

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

    public void pollEvents() { GLFW.glfwPollEvents(); }
    public void swapBuffers() { GLFW.glfwSwapBuffers(handle); }

    public void destroy() {
        GLFW.glfwDestroyWindow(handle);
        GLFW.glfwTerminate();
        GLFWErrorCallback cb = GLFW.glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }
}
