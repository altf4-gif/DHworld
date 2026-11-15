package com.example.engine;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Меню спавна на Q: сетка с названиями моделей из MrH.
 * ЛКМ по клетке — заспавнить (меню не закрывается, можно спавнить подряд).
 * Q / ESC — закрыть. Рисует через UiRenderer + TextRenderer из DHsim.
 */
public final class Menu {
    private final UiRenderer ui = new UiRenderer();
    private final List<UiTexture> labels = new ArrayList<>();
    private final UiTexture header;
    private final int count;

    private boolean open = false;
    private int hover = -1;

    private float x0, y0, cw, ch, gap, gridW, gridH;
    private int cols, rows;

    public Menu(List<String> names) {
        count = names.size();
        header = TextRenderer.createText("СПАВН МОДЕЛИ  |  клик — заспавнить  |  Q — закрыть",
                24, 1400, new Color(235, 235, 235));
        for (int i = 0; i < count; i++) {
            String n = names.get(i);
            if (n.length() > 26) n = n.substring(0, 25) + "…";
            labels.add(TextRenderer.createText((i + 1) + ". " + n, 20, 260, Color.WHITE));
        }
    }

    public boolean isOpen() { return open; }
    public int hover() { return hover; }

    public void toggle(Window w) {
        open = !open;
        w.setCursorDisabled(!open);
        hover = -1;
    }

    public void close(Window w) {
        if (!open) return;
        open = false;
        w.setCursorDisabled(true);
        hover = -1;
    }

    private void layout(int W, int H) {
        cols = Math.max(1, Math.min(4, (W - 100) / 250));
        rows = Math.max(1, (count + cols - 1) / cols);
        gap = 10f;
        cw = Math.min(240f, (W - 100f - (cols - 1) * gap) / cols);
        ch = Math.max(34f, Math.min(68f, (H - 180f - (rows - 1) * gap) / rows));
        gridW = cols * cw + (cols - 1) * gap;
        gridH = rows * ch + (rows - 1) * gap;
        x0 = (W - gridW) / 2f;
        y0 = (H - gridH) / 2f + 24f;
    }

    public void render(Window w) {
        if (!open) return;
        int W = w.width(), H = w.height();
        layout(W, H);

        double[] m = w.cursorPos();
        hover = -1;
        for (int i = 0; i < count; i++) {
            float cx = x0 + (i % cols) * (cw + gap);
            float cy = y0 + (i / cols) * (ch + gap);
            if (m[0] >= cx && m[0] <= cx + cw && m[1] >= cy && m[1] <= cy + ch) {
                hover = i;
                break;
            }
        }

        ui.begin(W, H);
        ui.drawSolid(W / 2f, H / 2f, W, H, 0f, 0f, 0f, 0.45f);
        ui.drawSolid(W / 2f, H / 2f - 4f, gridW + 48f, gridH + 100f,
                0.10f, 0.10f, 0.12f, 0.93f);

        float hh = 44f;
        float hw = hh * header.width / (float) header.height;
        ui.drawTextured(W / 2f, y0 - 36f, hw, hh, header.id);

        for (int i = 0; i < count; i++) {
            float cx = x0 + (i % cols) * (cw + gap);
            float cy = y0 + (i / cols) * (ch + gap);
            boolean hov = i == hover;
            ui.drawSolid(cx + cw / 2f, cy + ch / 2f, cw, ch,
                    hov ? 0.24f : 0.17f, hov ? 0.44f : 0.18f, hov ? 0.27f : 0.21f, 1f);
            UiTexture t = labels.get(i);
            float th = Math.min(ch - 8f, 56f);
            float tw = th * t.width / (float) t.height;
            if (tw > cw - 12f) { tw = cw - 12f; th = tw * t.height / (float) t.width; }
            ui.drawTextured(cx + cw / 2f, cy + ch / 2f, tw, th, t.id);
        }
        ui.end();
    }

    public void destroy() {
        for (UiTexture t : labels) t.destroy();
        header.destroy();
        ui.destroy();
    }
}
