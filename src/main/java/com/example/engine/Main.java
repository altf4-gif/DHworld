package com.example.engine;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/** Стартовое меню: Симуляция / Огонь и Вода. */
public final class Main {
    private Window win;
    private UiRenderer ui;
    private Label title, b0, b1, hint;
    private boolean lmbHeld, f11;

    public static void main(String[] args) { new Main().run(); }

    private void run() {
        win = new Window(1280, 720, "MrH");
        win.setCursorDisabled(false);
        ui = new UiRenderer();
        title = new Label(46, 1200, new Color(240, 240, 245));
        b0 = new Label(30, 700, Color.WHITE);
        b1 = new Label(30, 700, Color.WHITE);
        hint = new Label(20, 1200, new Color(180, 185, 195));
        title.set("MrH");
        b0.set("СИМУЛЯЦИЯ");
        b1.set("ОГОНЬ И ВОДА");
        hint.set("ЛКМ — выбрать,  F11 — экран,  ESC — выход");
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        long last = System.nanoTime();
        while (!win.shouldClose()) {
            float dt = Math.min(0.05f, (System.nanoTime() - last) / 1_000_000_000f);
            last = System.nanoTime();
            win.pollEvents();
            if (win.isKeyPressed(GLFW.GLFW_KEY_F11) && !f11) { win.toggleFullscreen(); f11 = true; }
            if (!win.isKeyPressed(GLFW.GLFW_KEY_F11)) f11 = false;
            if (win.isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) break;

            boolean lmb = win.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            boolean click = lmb && !lmbHeld; lmbHeld = lmb;

            int W = win.width(), H = win.height();
            float cw = Math.min(420f, W * 0.36f), ch = 120f, gap = 40f;
            float x0 = W / 2f - cw - gap / 2f, x1 = W / 2f + gap / 2f, cy = H / 2f + 20f;
            double[] m = win.cursorPos();
            boolean h0 = m[0] >= x0 && m[0] <= x0 + cw && m[1] >= cy - ch / 2 && m[1] <= cy + ch / 2;
            boolean h1 = m[0] >= x1 && m[0] <= x1 + cw && m[1] >= cy - ch / 2 && m[1] <= cy + ch / 2;

            GL11.glClearColor(0.07f, 0.08f, 0.11f, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            ui.begin(W, H);
            title.draw(ui, W / 2f, H / 2f - 130f, 70f);
            ui.drawSolid(x0 + cw / 2, cy, cw, ch, h0 ? 0.24f : 0.16f, h0 ? 0.44f : 0.17f, h0 ? 0.27f : 0.20f, 1f);
            ui.drawSolid(x1 + cw / 2, cy, cw, ch, h1 ? 0.44f : 0.16f, h1 ? 0.26f : 0.17f, h1 ? 0.20f : 0.20f, 1f);
            b0.draw(ui, x0 + cw / 2, cy, 46f);
            b1.draw(ui, x1 + cw / 2, cy, 46f);
            hint.draw(ui, W / 2f, H - 60f, 30f);
            ui.end();
            win.swapBuffers();

            if (click && h0) { win.setCursorDisabled(true); new Sandbox().run(win); win.setCursorDisabled(false); lmbHeld = true; }
            if (click && h1) { new com.example.firewater.FireWater().run(win); lmbHeld = true; }
        }
        title.destroy(); b0.destroy(); b1.destroy(); hint.destroy();
        ui.destroy();
        win.destroy();
    }

    public static final class Label {
        private final int size, maxW; private final Color color;
        private String text; private UiTexture tex;
        public Label(int size, int maxW, Color c) { this.size = size; this.maxW = maxW; this.color = c; }
        public void set(String s) {
            if (s.equals(text)) return;
            if (tex != null) tex.destroy();
            tex = TextRenderer.createText(s, size, maxW, color);
            text = s;
        }
        public void draw(UiRenderer ui, float cx, float cy, float h) {
            if (tex == null) return;
            float w = h * tex.width / (float) tex.height;
            ui.drawTextured(cx, cy, w, h, tex.id);
        }
        public void drawLeft(UiRenderer ui, float x, float cy, float h) {
            if (tex == null) return;
            float w = h * tex.width / (float) tex.height;
            ui.drawTextured(x + w / 2f, cy, w, h, tex.id);
        }
        public void destroy() { if (tex != null) { tex.destroy(); tex = null; } text = null; }
    }
}
