package com.example.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Песочница: плита + спавн ходячих моделей. ESC — назад в лаунчер. */
public final class Sandbox {
    private static final float HEIGHT = 1.8f;
    private static final float EDGE = Ground.SIZE / 2f - 1f;

    private static final class Npc {
        final RiggedModel model; final int objIndex;
        Npc(RiggedModel m, int i) { model = m; objIndex = i; }
    }

    private final List<File> objs = new ArrayList<>();
    private final List<Npc> npcs = new ArrayList<>();
    private final Set<Integer> held = new HashSet<>();
    private final Random rng = new Random();
    private int selected = 0;
    private boolean lmbHeld = false;
    private Menu menu;

    public void run(Window window) {
        held.clear(); lmbHeld = false;
        File modelsDir = new File("MrH");
        if (!modelsDir.isDirectory()) modelsDir = new File("../MrH");
        scan(modelsDir);

        window.setCursorDisabled(true);
        Camera camera = new Camera(window.aspectRatio());
        camera.resetMouseLook();
        Ground ground = new Ground();
        Texture noShadow = new Texture(1, 1, new int[]{0xFFFFFFFF}, false);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        List<String> names = new ArrayList<>();
        for (File f : objs) { String n = f.getName(); names.add(n.substring(0, n.length() - 4)); }
        menu = new Menu(names);

        Matrix4f lightVP = new Matrix4f();
        Vector3f sunDir = new Vector3f(0.4f, 0.8f, 0.3f).normalize();
        Vector3f sunColor = new Vector3f(0.55f, 0.55f, 0.50f);
        Vector3f ambient = new Vector3f(0.55f, 0.55f, 0.60f);
        Vector3f sky = new Vector3f(0.55f, 0.75f, 0.95f);

        title(window);
        long last = System.nanoTime();
        boolean running = true;
        while (running && !window.shouldClose()) {
            float dt = Math.min(0.05f, (System.nanoTime() - last) / 1_000_000_000f);
            last = System.nanoTime();
            window.pollEvents();
            if (tap(window, GLFW.GLFW_KEY_ESCAPE)) {
                if (menu.isOpen()) { menu.close(window); camera.resetMouseLook(); }
                else running = false;
            }
            camera.setAspect(window.aspectRatio());
            if (!menu.isOpen()) camera.update(window, dt);
            input(window, camera);

            for (int i = npcs.size() - 1; i >= 0; i--) {
                Npc n = npcs.get(i);
                n.model.update(dt);
                Vector3f p = n.model.position();
                if (Math.abs(p.x) > EDGE || Math.abs(p.z) > EDGE) {
                    n.model.destroy();
                    npcs.set(i, new Npc(create(n.objIndex,
                            (rng.nextFloat() * 2f - 1f) * 6f, (rng.nextFloat() * 2f - 1f) * 6f), n.objIndex));
                }
            }
            GL11.glClearColor(sky.x, sky.y, sky.z, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            ground.render(camera.view(), camera.projection());
            for (Npc n : npcs)
                n.model.render(camera.view(), camera.projection(), lightVP, noShadow.getId(),
                        sunDir, sunColor, ambient, camera.position(), sky, 0f);
            menu.render(window);
            window.swapBuffers();
        }
        for (Npc n : npcs) n.model.destroy();
        npcs.clear();
        menu.destroy();
        ground.destroy();
        noShadow.destroy();
    }

    private void input(Window w, Camera cam) {
        if (tap(w, GLFW.GLFW_KEY_F11)) w.toggleFullscreen();
        if (tap(w, GLFW.GLFW_KEY_Q)) { menu.toggle(w); if (!menu.isOpen()) cam.resetMouseLook(); }
        boolean lmb = w.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        boolean click = lmb && !lmbHeld; lmbHeld = lmb;
        if (menu.isOpen()) {
            if (click && menu.hover() >= 0) { selected = menu.hover(); spawn(cam); title(w); }
            if (tap(w, GLFW.GLFW_KEY_R)) { clearAll(); title(w); }
            return;
        }
        for (int i = 0; i < Math.min(10, objs.size()); i++) {
            int key = (i == 9) ? GLFW.GLFW_KEY_0 : GLFW.GLFW_KEY_1 + i;
            if (tap(w, key)) { selected = i; title(w); }
        }
        if (!objs.isEmpty() && tap(w, GLFW.GLFW_KEY_TAB)) { selected = (selected + 1) % objs.size(); title(w); }
        if (tap(w, GLFW.GLFW_KEY_E) || click) { spawn(cam); title(w); }
        if (tap(w, GLFW.GLFW_KEY_R)) { clearAll(); title(w); }
    }

    private void clearAll() { for (Npc n : npcs) n.model.destroy(); npcs.clear(); }

    private RiggedModel create(int idx, float x, float z) {
        File obj = objs.get(idx);
        return new RiggedModel(obj.getPath(), textureFor(obj), x, z, HEIGHT);
    }

    private void spawn(Camera cam) {
        if (objs.isEmpty()) return;
        Vector3f p = new Vector3f(cam.position()).add(new Vector3f(cam.frontVector()).mul(3f));
        npcs.add(new Npc(create(selected,
                Math.max(-EDGE, Math.min(EDGE, p.x)), Math.max(-EDGE, Math.min(EDGE, p.z))), selected));
    }

    private boolean tap(Window w, int key) {
        boolean now = w.isKeyPressed(key);
        boolean fresh = now && !held.contains(key);
        if (now) held.add(key); else held.remove(key);
        return fresh;
    }

    private void title(Window w) {
        String cur = objs.isEmpty() ? "нет .obj в MrH"
                : (selected + 1) + "/" + objs.size() + " " + objs.get(selected).getName();
        GLFW.glfwSetWindowTitle(w.handle(), "MrH | " + cur + " | людей: " + npcs.size()
                + " | Q меню, E/ЛКМ спавн, R очистить, ESC назад");
    }

    private void scan(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        for (File f : fs) {
            if (f.isDirectory()) scan(f);
            else if (f.getName().toLowerCase().endsWith(".obj")) objs.add(f);
        }
    }

    public static String textureFor(File obj) {
        File dir = obj.getParentFile();
        String base = obj.getName().substring(0, obj.getName().length() - 4);
        File mtl = new File(dir, base + ".mtl");
        if (mtl.isFile()) {
            try (BufferedReader br = new BufferedReader(new FileReader(mtl))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("map_Kd")) {
                        String name = new File(line.substring(6).trim().replace('\\', '/')).getName();
                        File tex = new File(dir, name);
                        if (tex.isFile()) return tex.getPath();
                    }
                }
            } catch (Exception ignored) { }
        }
        File[] pngs = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
        return (pngs != null && pngs.length > 0) ? pngs[0].getPath() : null;
    }
}
