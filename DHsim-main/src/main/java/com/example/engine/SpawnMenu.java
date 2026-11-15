package com.example.engine;

import java.awt.Color;

/**
 * Меню спавна: вкладки + сетка клеток (иконка + подпись).
 * Индексы: 0..TAB_NAMES.length-1 = вкладки, дальше = клетки текущей вкладки.
 * actionAt() по вкладке переключает её и возвращает null — движок это игнорирует.
 */
public final class SpawnMenu {

    public enum Action {
        SPAWN_TEEN, SPAWN_FAT, SPAWN_MUSCULAR, SPAWN_FEMALE, SPAWN_PREGNANT,
        SPAWN_BABY, SPAWN_YOUNG_BOY,
        SPAWN_POOP, SPAWN_BRIGHT_POOP, SPAWN_POOP_CLUSTER,
        SPAWN_COMBO, SPAWN_TEEN_CROWD,
        SPAWN_MRH_NORMIS, SPAWN_MRH_FEMALE, SPAWN_MRH_KACHOK, SPAWN_MRH_TOLSTIY,
        SPAWN_MRH_BEREM, SPAWN_MRH_PUPS, SPAWN_MRH_POLICE, SPAWN_MRH_COM, SPAWN_MRH_SOLDIER
    }

    private static final String[] TAB_NAMES = {"3D люди", "2D люди", "Какашки", "Комбо"};

    /** kind: 0 = typeTextures, 1 = poopIcon, 2 = brightPoopIcon, 3 = peopleTex */
    private static final class Item {
        final String label; final Action action; final int kind; final int tex;
        Item(String l, Action a, int k, int t) { label = l; action = a; kind = k; tex = t; }
    }

    private static final Item[][] TABS = {
            { // --- 3D (RiggedModel) ---
                    new Item("Нормис",     Action.SPAWN_MRH_NORMIS,  3, 12),
                    new Item("Женщина",    Action.SPAWN_MRH_FEMALE,  3, 3),
                    new Item("Качок",      Action.SPAWN_MRH_KACHOK,  3, 9),
                    new Item("Толстяк",    Action.SPAWN_MRH_TOLSTIY, 3, 10),
                    new Item("Беременная", Action.SPAWN_MRH_BEREM,   3, 11),
                    new Item("Пупс",       Action.SPAWN_MRH_PUPS,    3, 1),
                    new Item("Полиция",    Action.SPAWN_MRH_POLICE,  3, 7),
                    new Item("Коммунист",  Action.SPAWN_MRH_COM,     3, 5),
                    new Item("Военный",    Action.SPAWN_MRH_SOLDIER, 3, 8),
            },
            { // --- 2D (Eater) ---
                    new Item("Подросток", Action.SPAWN_TEEN,       0, 0),
                    new Item("Толстяк",   Action.SPAWN_FAT,        0, 1),
                    new Item("Качок",     Action.SPAWN_MUSCULAR,   0, 6),
                    new Item("Женщина",   Action.SPAWN_FEMALE,     0, 2),
                    new Item("Беременная",Action.SPAWN_PREGNANT,   0, 3),
                    new Item("Малыш",     Action.SPAWN_BABY,       0, 4),
                    new Item("Мальчик",   Action.SPAWN_YOUNG_BOY,  0, 5),
            },
            { // --- какашки ---
                    new Item("Какашка",       Action.SPAWN_POOP,         1, -1),
                    new Item("Яркая какашка", Action.SPAWN_BRIGHT_POOP,  2, -1),
                    new Item("Кучка (5 шт)",  Action.SPAWN_POOP_CLUSTER, 1, -1),
            },
            { // --- комбо ---
                    new Item("Подросток + 5", Action.SPAWN_COMBO,       0, 0),
                    new Item("Толпа (5 шт)",  Action.SPAWN_TEEN_CROWD,  0, 0),
            }
    };

    private static final float PANEL_W_BASE = 880f;
    private static final float PAD_BASE     = 18f;
    private static final float GAP_BASE     = 10f;
    private static final float HEADER_BASE  = 54f;
    private static final float TABS_BASE    = 42f;
    private static final float FOOTER_BASE  = 42f;
    private static final int   MAX_COLS     = 5;

    private final Texture[] typeTextures;
    private final Texture[] peopleTextures;
    private final UiTexture poopIcon, brightPoopIcon;
    private final UiTexture titleTexture, hintTexture;
    private final UiTexture[] tabTextures;
    private final UiTexture[][] labelTextures;

    private boolean open = false;
    private int hovered = -1;
    private int tab = 0;

    /** Старый конструктор — совместимость. */
    public SpawnMenu(Texture[] typeTextures, UiTexture poopIcon, UiTexture brightPoopIcon) {
        this(typeTextures, null, poopIcon, brightPoopIcon);
    }

    public SpawnMenu(Texture[] typeTextures, Texture[] peopleTextures,
                     UiTexture poopIcon, UiTexture brightPoopIcon) {
        this.typeTextures = typeTextures;
        this.peopleTextures = peopleTextures;
        this.poopIcon = poopIcon;
        this.brightPoopIcon = brightPoopIcon;

        titleTexture = TextRenderer.createText("СПАВН", 30, 760, new Color(255, 220, 110));
        hintTexture  = TextRenderer.createText("ЛКМ — заспавнить.  Q / Esc — закрыть.",
                16, 620, new Color(150, 155, 170));

        tabTextures = new UiTexture[TAB_NAMES.length];
        for (int i = 0; i < TAB_NAMES.length; i++)
            tabTextures[i] = TextRenderer.createText(TAB_NAMES[i], 22, 400, new Color(235, 238, 245));

        labelTextures = new UiTexture[TABS.length][];
        for (int t = 0; t < TABS.length; t++) {
            labelTextures[t] = new UiTexture[TABS[t].length];
            for (int i = 0; i < TABS[t].length; i++)
                labelTextures[t][i] = TextRenderer.createText(TABS[t][i].label, 20, 420,
                        new Color(228, 231, 238));
        }
    }

    public void open()   { open = true; hovered = -1; }
    public void close()  { open = false; hovered = -1; }
    public void toggle() { if (open) close(); else open(); }
    public boolean isOpen() { return open; }
    public void setHovered(int i) { hovered = i; }

    /** Клик по вкладке переключает её и возвращает null. */
    public Action actionAt(int i) {
        if (i < 0) return null;
        if (i < TAB_NAMES.length) { tab = i; hovered = -1; return null; }
        int k = i - TAB_NAMES.length;
        Item[] items = TABS[tab];
        if (k < 0 || k >= items.length) return null;
        return items[k].action;
    }

    // ---------------- раскладка ----------------

    private static float uiScale(int sh) { return Math.min(Math.max(sh / 720f, 1f), 2f); }

    private static final class L {
        float s, panelW, panelH, left, top, pad, gap;
        float headerH, tabsH, footerH, cellW, cellH, gridTop;
        int cols, rows;
    }

    private L layout(int sw, int sh) {
        L l = new L();
        l.s = uiScale(sh);
        int n = TABS[tab].length;
        l.cols = Math.min(MAX_COLS, Math.max(1, n));
        l.rows = (n + l.cols - 1) / l.cols;

        l.pad = PAD_BASE * l.s;
        l.gap = GAP_BASE * l.s;
        l.headerH = HEADER_BASE * l.s;
        l.tabsH   = TABS_BASE * l.s;
        l.footerH = FOOTER_BASE * l.s;

        l.panelW = Math.min(PANEL_W_BASE * l.s, sw - 40f * l.s);
        l.cellW = (l.panelW - 2 * l.pad - (l.cols - 1) * l.gap) / l.cols;
        l.cellH = l.cellW * 1.16f;

        float fixed = l.headerH + l.tabsH + l.gap + l.footerH + 2 * l.pad;
        float gridH = l.rows * l.cellH + (l.rows - 1) * l.gap;
        l.panelH = fixed + gridH;

        float maxH = sh * 0.94f;
        if (l.panelH > maxH) {
            float room = maxH - fixed - (l.rows - 1) * l.gap;
            float k = Math.max(0.35f, Math.min(1f, room / (l.rows * l.cellH)));
            l.cellH *= k;
            l.cellW *= k;
            l.panelW = l.cellW * l.cols + (l.cols - 1) * l.gap + 2 * l.pad;
            gridH = l.rows * l.cellH + (l.rows - 1) * l.gap;
            l.panelH = fixed + gridH;
        }

        l.left = sw / 2f - l.panelW / 2f;
        l.top  = sh / 2f - l.panelH / 2f;
        l.gridTop = l.top + l.pad + l.headerH + l.tabsH + l.gap;
        return l;
    }

    private static float[] tabRect(L l, int i) {
        float w = (l.panelW - 2 * l.pad) / TAB_NAMES.length;
        float h = l.tabsH * 0.82f;
        float x = l.left + l.pad + i * w;
        float y = l.top + l.pad + l.headerH + (l.tabsH - h) / 2f;
        return new float[]{x, y, w - 4f, h};
    }

    private static float[] cellRect(L l, int i) {
        int c = i % l.cols, r = i / l.cols;
        float x = l.left + l.pad + c * (l.cellW + l.gap);
        float y = l.gridTop + r * (l.cellH + l.gap);
        return new float[]{x, y, l.cellW, l.cellH};
    }

    private static boolean inside(float[] r, double mx, double my) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    public int hitTest(int sw, int sh, double mx, double my) {
        L l = layout(sw, sh);
        for (int i = 0; i < TAB_NAMES.length; i++)
            if (inside(tabRect(l, i), mx, my)) return i;
        int n = TABS[tab].length;
        for (int i = 0; i < n; i++)
            if (inside(cellRect(l, i), mx, my)) return TAB_NAMES.length + i;
        return -1;
    }

    // ---------------- отрисовка ----------------

    public void render(UiRenderer ui, int sw, int sh) {
        L l = layout(sw, sh);
        float cx = sw / 2f, cy = sh / 2f;

        ui.drawSolid(cx, cy, sw, sh, 0f, 0f, 0f, 0.62f);
        ui.drawSolid(cx, cy, l.panelW, l.panelH, 0.15f, 0.18f, 0.24f, 1f);
        ui.drawSolid(cx, cy, l.panelW - 3, l.panelH - 3, 0.07f, 0.08f, 0.11f, 1f);

        // заголовок
        float titleH = 34 * l.s;
        float titleW = titleH * ((float) titleTexture.width / titleTexture.height);
        ui.drawTextured(cx, l.top + l.pad + l.headerH * 0.5f, titleW, titleH, titleTexture.id);

        // вкладки
        for (int i = 0; i < TAB_NAMES.length; i++) {
            float[] r = tabRect(l, i);
            float rcx = r[0] + r[2] / 2f, rcy = r[1] + r[3] / 2f;
            boolean active = (i == tab);
            boolean hov = (hovered == i);
            if (active)      ui.drawSolid(rcx, rcy, r[2], r[3], 0.55f, 0.42f, 0.10f, 1f);
            else if (hov)    ui.drawSolid(rcx, rcy, r[2], r[3], 0.28f, 0.32f, 0.42f, 1f);
            else             ui.drawSolid(rcx, rcy, r[2], r[3], 0.16f, 0.19f, 0.26f, 1f);
            ui.drawSolid(rcx, rcy, r[2] - 3, r[3] - 3,
                    active ? 0.20f : 0.10f, active ? 0.16f : 0.12f, active ? 0.08f : 0.16f, 1f);

            UiTexture tt = tabTextures[i];
            float th = Math.min(20 * l.s, r[3] * 0.55f);
            float tw = th * ((float) tt.width / tt.height);
            if (tw > r[2] - 10) { tw = r[2] - 10; th = tw * tt.height / (float) tt.width; }
            ui.drawTextured(rcx, rcy, tw, th, tt.id);
        }

        // клетки
        Item[] items = TABS[tab];
        for (int i = 0; i < items.length; i++) {
            float[] r = cellRect(l, i);
            float rcx = r[0] + r[2] / 2f, rcy = r[1] + r[3] / 2f;
            boolean hov = (hovered == TAB_NAMES.length + i);

            if (hov) {
                ui.drawSolid(rcx, rcy, r[2], r[3], 0.62f, 0.48f, 0.12f, 1f);
                ui.drawSolid(rcx, rcy, r[2] - 4, r[3] - 4, 0.24f, 0.28f, 0.36f, 1f);
            } else {
                ui.drawSolid(rcx, rcy, r[2], r[3], 0.16f, 0.20f, 0.27f, 1f);
                ui.drawSolid(rcx, rcy, r[2] - 3, r[3] - 3, 0.11f, 0.13f, 0.18f, 1f);
            }

            float iconBox = Math.min(r[2] - 16 * l.s, r[3] * 0.66f);
            float iconCy  = r[1] + r[3] * 0.40f;
            drawIcon(ui, items[i], rcx, iconCy, iconBox);

            UiTexture lt = labelTextures[tab][i];
            float th = Math.min(18 * l.s, r[3] * 0.16f);
            float tw = th * ((float) lt.width / lt.height);
            if (tw > r[2] - 10 * l.s) { tw = r[2] - 10 * l.s; th = tw * lt.height / (float) lt.width; }
            ui.drawTextured(rcx, r[1] + r[3] - th * 0.9f - 6 * l.s, tw, th, lt.id);
        }

        // подсказка
        float hintH = 18 * l.s;
        float hintW = hintH * ((float) hintTexture.width / hintTexture.height);
        ui.drawTextured(cx, l.top + l.panelH - l.pad - l.footerH * 0.4f, hintW, hintH, hintTexture.id);
    }

    private void drawIcon(UiRenderer ui, Item it, float cx, float cy, float box) {
        switch (it.kind) {
            case 1 -> { if (poopIcon != null) ui.drawTextured(cx, cy, box, box, poopIcon.id); }
            case 2 -> { if (brightPoopIcon != null) ui.drawTextured(cx, cy, box, box, brightPoopIcon.id); }
            case 3 -> {
                Texture t = null;
                if (peopleTextures != null && it.tex >= 0 && it.tex < peopleTextures.length)
                    t = peopleTextures[it.tex];
                if (t == null && typeTextures != null && it.tex >= 0 && it.tex < typeTextures.length)
                    t = typeTextures[it.tex];
                if (t != null) drawContentTex(ui, cx, cy, box, t);
            }
            default -> {
                if (typeTextures != null && it.tex >= 0 && it.tex < typeTextures.length) {
                    Texture t = typeTextures[it.tex];
                    if (t != null) drawContentTex(ui, cx, cy, box, t);
                }
            }
        }
    }

    private static void drawContentTex(UiRenderer ui, float cx, float cy, float box, Texture t) {
        float aspect = t.contentAspect();
        float h = box, w = h * aspect;
        if (w > box) { w = box; h = w / aspect; }
        ui.drawTexturedUV(cx, cy, w, h, t.getId(), t.uMin, t.vMin, t.uMax, t.vMax);
    }

    public void destroy() {
        titleTexture.destroy();
        hintTexture.destroy();
        for (UiTexture t : tabTextures) t.destroy();
        for (UiTexture[] row : labelTextures) for (UiTexture t : row) t.destroy();
    }
}
