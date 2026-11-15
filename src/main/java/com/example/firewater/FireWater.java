package com.example.firewater;

import com.example.engine.Main.Label;
import com.example.engine.RiggedModel;
import com.example.engine.Sandbox;
import com.example.engine.Texture;
import com.example.engine.UiRenderer;
import com.example.engine.Window;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Режим «Огонь и Вода»: выбор уровня + кооперативная головоломка сверху. */
public final class FireWater {
    private static final int SELECT = 0, PLAY = 1, WIN = 2;
    private static final float SPEED = 3.2f;

    private Window win;
    private UiRenderer ui;
    private BoxRenderer br;
    private Texture white;
    private final List<File> files = new ArrayList<>();
    private Level level;
    private final RiggedModel[] hero = new RiggedModel[2];
    private int state = SELECT, cur = 0, unlocked = 1;
    private final int[] stars = new int[64];
    private float anim, flash;
    private boolean lmbHeld;
    private final Set<Integer> held = new HashSet<>();
    private final Matrix4f view = new Matrix4f(), proj = new Matrix4f(), lightVP = new Matrix4f();
    private final Vector3f eye = new Vector3f();
    private final Vector3f sunDir = new Vector3f(0.4f, 0.8f, 0.3f).normalize();
    private final Vector3f sunColor = new Vector3f(0.5f, 0.5f, 0.45f);
    private final Vector3f ambient = new Vector3f(0.6f, 0.6f, 0.65f);
    private final Vector3f fog = new Vector3f(0.07f, 0.08f, 0.11f);

    private Label head, hint, hudLevel, hudTime, hudGems, hudDeaths, winTitle, winStats, winHint;
    private Label[] cardNum, cardName, cardInfo;

    public void run(Window window) {
        win = window;
        win.setCursorDisabled(false);
        ui = new UiRenderer();
        br = new BoxRenderer();
        white = new Texture(1, 1, new int[]{0xFFFFFFFF}, false);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        loadProgress();
        findLevels();
        makeLabels();

        long last = System.nanoTime();
        boolean running = true;
        while (running && !win.shouldClose()) {
            float dt = Math.min(0.05f, (System.nanoTime() - last) / 1_000_000_000f);
            last = System.nanoTime();
            anim += dt;
            win.pollEvents();
            if (tap(GLFW.GLFW_KEY_F11)) win.toggleFullscreen();
            boolean lmb = win.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            boolean click = lmb && !lmbHeld; lmbHeld = lmb;

            if (state == SELECT) {
                if (tap(GLFW.GLFW_KEY_ESCAPE)) running = false;
                renderSelect(click);
            } else {
                if (tap(GLFW.GLFW_KEY_ESCAPE)) { unloadLevel(); state = SELECT; }
                else if (tap(GLFW.GLFW_KEY_Q)) { unloadLevel(); running = false; }
                else {
                    if (state == PLAY) stepPlay(dt); else stepWin();
                    renderPlay();
                }
            }
            win.swapBuffers();
        }
        unloadLevel();
        saveProgress();
        destroyLabels();
        br.destroy(); ui.destroy(); white.destroy();
        win.setCursorDisabled(false);
    }

    private void stepPlay(float dt) {
        float[] dx = new float[2], dz = new float[2];
        if (win.isKeyPressed(GLFW.GLFW_KEY_A)) dx[0] -= 1;
        if (win.isKeyPressed(GLFW.GLFW_KEY_D)) dx[0] += 1;
        if (win.isKeyPressed(GLFW.GLFW_KEY_W)) dz[0] -= 1;
        if (win.isKeyPressed(GLFW.GLFW_KEY_S)) dz[0] += 1;
        if (win.isKeyPressed(GLFW.GLFW_KEY_LEFT)) dx[1] -= 1;
        if (win.isKeyPressed(GLFW.GLFW_KEY_RIGHT)) dx[1] += 1;
        if (win.isKeyPressed(GLFW.GLFW_KEY_UP)) dz[1] -= 1;
        if (win.isKeyPressed(GLFW.GLFW_KEY_DOWN)) dz[1] += 1;

        for (int i = 0; i < 2; i++) {
            float len = (float) Math.sqrt(dx[i] * dx[i] + dz[i] * dz[i]);
            if (len > 0) { dx[i] = dx[i] / len * SPEED * dt; dz[i] = dz[i] / len * SPEED * dt; }
            float ox = level.hx[i], oz = level.hz[i];
            level.moveHero(i, dx[i], dz[i]);
            float vx = dt > 1e-6f ? (level.hx[i] - ox) / dt : 0;
            float vz = dt > 1e-6f ? (level.hz[i] - oz) / dt : 0;
            if (hero[i] != null) {
                hero[i].updateManual(vx, vz, dt);
                hero[i].position().set(level.hx[i], 0, level.hz[i]);
            }
        }
        level.update(dt);
        if (tap(GLFW.GLFW_KEY_R)) level.reset();
        if (level.kill() >= 0) { level.deaths++; level.reset(); flash = 0.5f; }
        if (flash > 0) flash -= dt;
        if (level.bothExit()) {
            int s = 1;
            if (level.gemTotal > 0 && level.gemGot >= level.gemTotal) s++;
            if (level.time <= level.par) s++;
            stars[cur] = Math.max(stars[cur], s);
            unlocked = Math.max(unlocked, Math.min(files.size(), cur + 2));
            saveProgress();
            state = WIN;
        }
    }

    private void stepWin() {
        if (tap(GLFW.GLFW_KEY_R)) startLevel(cur);
        else if (tap(GLFW.GLFW_KEY_ENTER)) {
            if (cur + 1 < files.size()) startLevel(cur + 1);
            else { unloadLevel(); state = SELECT; }
        }
    }

    private void fitCamera() {
        float aspect = win.aspectRatio(), fov = 45f;
        proj.identity().perspective((float) Math.toRadians(fov), aspect, 0.1f, 500f);
        float half = Math.max((level.w + 3f) / aspect, level.h + 5f) * 0.5f;
        float dist = half / (float) Math.tan(Math.toRadians(fov * 0.5f)) * 1.15f;
        eye.set(0, dist * 0.86f, dist * 0.50f);
        view.identity().lookAt(eye, new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
    }

    private void renderPlay() {
        fitCamera();
        int W = win.width(), H = win.height();
        GL11.glClearColor(fog.x, fog.y, fog.z, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        br.begin(view, proj);
        level.render(br, anim);
        br.box(level.hx[0], 0.04f, level.hz[0], 0.8f, 0.03f, 0.8f, 0.9f, 0.25f, 0.2f);
        br.box(level.hx[1], 0.04f, level.hz[1], 0.8f, 0.03f, 0.8f, 0.25f, 0.5f, 0.95f);
        br.end();
        for (int i = 0; i < 2; i++) if (hero[i] != null)
            hero[i].render(view, proj, lightVP, white.getId(), sunDir, sunColor, ambient, eye, fog, 0f);

        hudLevel.set("Уровень " + (cur + 1) + ": " + level.name);
        hudTime.set("Время: " + (int) level.time + "  (цель " + level.par + ")");
        hudGems.set("Алмазы: " + level.gemGot + "/" + level.gemTotal);
        hudDeaths.set("Смертей: " + level.deaths);
        ui.begin(W, H);
        hudLevel.drawLeft(ui, 20, 34, 34);
        hudTime.drawLeft(ui, 20, 70, 28);
        hudGems.drawLeft(ui, 20, 100, 28);
        hudDeaths.drawLeft(ui, 20, 130, 28);
        hint.draw(ui, W / 2f, H - 30f, 26);
        if (flash > 0) ui.drawSolid(W / 2f, H / 2f, W, H, 0.8f, 0.1f, 0.1f, Math.min(0.45f, flash));
        if (state == WIN) {
            ui.drawSolid(W / 2f, H / 2f, W, H, 0f, 0f, 0f, 0.6f);
            winTitle.draw(ui, W / 2f, H / 2f - 70f, 60);
            winStats.set("Время " + (int) level.time + "   Алмазы " + level.gemGot + "/" + level.gemTotal
                    + "   Звёзды " + stars[cur] + "/3");
            winStats.draw(ui, W / 2f, H / 2f, 40);
            winHint.draw(ui, W / 2f, H / 2f + 70f, 30);
        }
        ui.end();
    }

    private void renderSelect(boolean click) {
        int W = win.width(), H = win.height();
        GL11.glClearColor(fog.x, fog.y, fog.z, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        int n = Math.max(1, files.size());
        float cw = Math.min(260f, (W - 120f) / n - 24f), ch = 280f, gap = 24f;
        float total = n * cw + (n - 1) * gap, x0 = (W - total) / 2f, cy = H / 2f + 20f;
        double[] m = win.cursorPos();
        ui.begin(W, H);
        head.draw(ui, W / 2f, H / 2f - 190f, 56);
        for (int i = 0; i < files.size(); i++) {
            float cx = x0 + i * (cw + gap) + cw / 2;
            boolean hov = m[0] >= cx - cw / 2 && m[0] <= cx + cw / 2 && m[1] >= cy - ch / 2 && m[1] <= cy + ch / 2;
            boolean open = i < unlocked;
            ui.drawSolid(cx, cy, cw, ch, hov && open ? 0.24f : 0.15f, hov && open ? 0.42f : 0.16f, hov && open ? 0.26f : 0.19f, 1f);
            cardNum[i].draw(ui, cx, cy - 80f, 70);
            cardName[i].draw(ui, cx, cy + 10f, 30);
            cardInfo[i].set(open ? "Звёзды: " + stars[i] + "/3" : "ЗАКРЫТО");
            cardInfo[i].draw(ui, cx, cy + 80f, 28);
            if (click && hov && open) { startLevel(i); break; }
        }
        hint.draw(ui, W / 2f, H - 40f, 28);
        ui.end();
    }

    private void findLevels() {
        File d = new File("levels_fw");
        if (!d.isDirectory()) d = new File("../levels_fw");
        File[] fs = d.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".txt"));
        if (fs != null) { Arrays.sort(fs); files.addAll(Arrays.asList(fs)); }
        System.out.println("[FW] уровней: " + files.size());
    }

    private void startLevel(int i) {
        unloadLevel();
        cur = i;
        try { level = Level.load(files.get(i)); }
        catch (Exception e) { System.err.println("[FW] " + e.getMessage()); state = SELECT; return; }
        makeHeroes();
        state = PLAY;
    }

    private void unloadLevel() {
        for (int i = 0; i < 2; i++) if (hero[i] != null) { hero[i].destroy(); hero[i] = null; }
        level = null;
    }

    private void makeHeroes() {
        File dir = new File("MrH");
        if (!dir.isDirectory()) dir = new File("../MrH");
        List<File> objs = new ArrayList<>();
        scan(dir, objs);
        String woman = "berem", man = "normis", fire = "man";
        File cfg = new File("fw/heroes.txt");
        if (cfg.isFile()) {
            try {
                for (String line : Files.readAllLines(cfg.toPath(), StandardCharsets.UTF_8)) {
                    int eq = line.indexOf('=');
                    if (eq < 0 || line.trim().startsWith("#")) continue;
                    String k = line.substring(0, eq).trim(), v = line.substring(eq + 1).trim();
                    if (k.equals("woman")) woman = v;
                    else if (k.equals("man")) man = v;
                    else if (k.equals("fire")) fire = v;
                }
            } catch (Exception ignored) { }
        }
        String w0 = fire.equals("woman") ? woman : man;
        String w1 = fire.equals("woman") ? man : woman;
        File f0 = find(objs, w0), f1 = find(objs, w1);
        if (f0 == null && !objs.isEmpty()) f0 = objs.get(0);
        if (f1 == null && objs.size() > 1) f1 = objs.get(1);
        if (f1 == null) f1 = f0;
        File[] pick = {f0, f1};
        for (int i = 0; i < 2; i++) {
            if (pick[i] == null) { System.err.println("[FW] нет моделек в MrH"); continue; }
            hero[i] = new RiggedModel(pick[i].getPath(), Sandbox.textureFor(pick[i]),
                    level.hx[i], level.hz[i], 1.5f);
            hero[i].setManual(true);
            System.out.println("[FW] герой " + (i == 0 ? "ОГОНЬ" : "ВОДА") + " = " + pick[i].getName());
        }
    }

    private static File find(List<File> objs, String sub) {
        String s = sub.toLowerCase();
        for (File f : objs) if (f.getName().toLowerCase().contains(s)) return f;
        return null;
    }

    private static void scan(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        for (File f : fs) {
            if (f.isDirectory()) scan(f, out);
            else if (f.getName().toLowerCase().endsWith(".obj")) out.add(f);
        }
    }

    private void loadProgress() {
        File f = new File("fw/progress.txt");
        if (!f.isFile()) return;
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String k = line.substring(0, eq).trim(), v = line.substring(eq + 1).trim();
                if (k.equals("unlocked")) unlocked = Math.max(1, Integer.parseInt(v));
                else if (k.startsWith("stars")) {
                    int i = Integer.parseInt(k.substring(5));
                    if (i >= 0 && i < stars.length) stars[i] = Integer.parseInt(v);
                }
            }
        } catch (Exception ignored) { }
    }

    private void saveProgress() {
        try {
            File d = new File("fw");
            if (!d.exists()) d.mkdirs();
            StringBuilder sb = new StringBuilder("unlocked=" + unlocked + "\n");
            for (int i = 0; i < files.size(); i++) sb.append("stars").append(i).append("=").append(stars[i]).append("\n");
            Files.write(new File(d, "progress.txt").toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    private void makeLabels() {
        head = new Label(34, 1400, new Color(240, 240, 245));
        head.set("ОГОНЬ И ВОДА — выбор уровня");
        hint = new Label(18, 1600, new Color(180, 185, 195));
        hint.set("WASD — Огонь (красный),  Стрелки — Вода (синяя),  R — заново,  ESC — назад,  Q — в меню");
        hudLevel = new Label(22, 900, Color.WHITE);
        hudTime = new Label(18, 700, new Color(210, 215, 225));
        hudGems = new Label(18, 700, new Color(210, 215, 225));
        hudDeaths = new Label(18, 700, new Color(210, 215, 225));
        winTitle = new Label(34, 1200, new Color(120, 240, 150));
        winTitle.set("УРОВЕНЬ ПРОЙДЕН");
        winStats = new Label(22, 1200, Color.WHITE);
        winHint = new Label(18, 1400, new Color(200, 205, 215));
        winHint.set("ENTER — дальше,  R — заново,  ESC — к уровням");
        cardNum = new Label[files.size()];
        cardName = new Label[files.size()];
        cardInfo = new Label[files.size()];
        for (int i = 0; i < files.size(); i++) {
            cardNum[i] = new Label(40, 300, Color.WHITE);
            cardNum[i].set(String.valueOf(i + 1));
            cardName[i] = new Label(18, 260, new Color(220, 225, 235));
            String n = files.get(i).getName().replace(".txt", "");
            try { Level t = Level.load(files.get(i)); n = t.name; } catch (Exception ignored) { }
            cardName[i].set(n);
            cardInfo[i] = new Label(16, 260, new Color(255, 220, 120));
        }
    }

    private void destroyLabels() {
        head.destroy(); hint.destroy(); hudLevel.destroy(); hudTime.destroy(); hudGems.destroy();
        hudDeaths.destroy(); winTitle.destroy(); winStats.destroy(); winHint.destroy();
        for (int i = 0; i < cardNum.length; i++) { cardNum[i].destroy(); cardName[i].destroy(); cardInfo[i].destroy(); }
    }

    private boolean tap(int key) {
        boolean now = win.isKeyPressed(key);
        boolean fresh = now && !held.contains(key);
        if (now) held.add(key); else held.remove(key);
        return fresh;
    }
}
