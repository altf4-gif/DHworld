package com.example.firewater;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Карта из txt + логика.
 * # стена  . пол  1/2 старты  R/B/G лужи  r/b алмазы
 * A кнопка -> a ворота   L рычаг -> l ворота   X ящик   E/e выходы
 */
public final class Level {
    public static final float HALF = 0.30f, BOXH = 0.42f;

    public int w, h, par = 60;
    public String name = "";
    private char[] g;
    private boolean[] gemTaken;
    public int gemTotal, gemGot;

    public final float[] hx = new float[2], hz = new float[2];
    private final float[] sx = new float[2], sz = new float[2];
    private float[] bx = new float[0], bz = new float[0], bx0 = new float[0], bz0 = new float[0];

    public boolean buttonDown, leverOn, gateA, gateL;
    private boolean leverHeld;
    public float time;
    public int deaths;

    public static Level load(File f) throws IOException {
        Level L = new Level();
        List<String> rows = new ArrayList<>();
        for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith(";")) continue;
            if (s.startsWith("name=")) { L.name = s.substring(5).trim(); continue; }
            if (s.startsWith("par=")) { try { L.par = Integer.parseInt(s.substring(4).trim()); } catch (Exception ignored) {} continue; }
            rows.add(s);
        }
        if (rows.isEmpty()) throw new IOException("Пустая карта: " + f);
        L.h = rows.size();
        L.w = 0;
        for (String r : rows) L.w = Math.max(L.w, r.length());
        L.g = new char[L.w * L.h];
        L.gemTaken = new boolean[L.w * L.h];
        List<float[]> boxes = new ArrayList<>();
        for (int r = 0; r < L.h; r++) {
            String s = rows.get(r);
            for (int c = 0; c < L.w; c++) {
                char ch = c < s.length() ? s.charAt(c) : '#';
                if (ch == '1' || ch == '2') {
                    int k = ch - '1';
                    L.sx[k] = L.cx(c); L.sz[k] = L.cz(r);
                    ch = '.';
                } else if (ch == 'X') {
                    boxes.add(new float[]{L.cx(c), L.cz(r)});
                    ch = '.';
                } else if (ch == 'r' || ch == 'b') L.gemTotal++;
                L.g[r * L.w + c] = ch;
            }
        }
        L.bx = new float[boxes.size()]; L.bz = new float[boxes.size()];
        L.bx0 = new float[boxes.size()]; L.bz0 = new float[boxes.size()];
        for (int i = 0; i < boxes.size(); i++) {
            L.bx[i] = L.bx0[i] = boxes.get(i)[0];
            L.bz[i] = L.bz0[i] = boxes.get(i)[1];
        }
        if (L.name.isEmpty()) L.name = f.getName();
        L.reset();
        return L;
    }

    public float cx(int c) { return c - w / 2f + 0.5f; }
    public float cz(int r) { return r - h / 2f + 0.5f; }
    private int col(float x) { return (int) Math.floor(x + w / 2f); }
    private int row(float z) { return (int) Math.floor(z + h / 2f); }

    public char at(float x, float z) {
        int c = col(x), r = row(z);
        if (c < 0 || r < 0 || c >= w || r >= h) return '#';
        return g[r * w + c];
    }

    public void reset() {
        for (int i = 0; i < 2; i++) { hx[i] = sx[i]; hz[i] = sz[i]; }
        for (int i = 0; i < bx.length; i++) { bx[i] = bx0[i]; bz[i] = bz0[i]; }
        Arrays.fill(gemTaken, false);
        gemGot = 0; leverOn = false; leverHeld = false; time = 0;
        buttonDown = false; gateA = false; gateL = false;
    }

    private boolean solidAt(float x, float z, float half) {
        int c0 = col(x - half), c1 = col(x + half), r0 = row(z - half), r1 = row(z + half);
        for (int r = r0; r <= r1; r++) for (int c = c0; c <= c1; c++) {
            if (c < 0 || r < 0 || c >= w || r >= h) return true;
            char ch = g[r * w + c];
            if (ch == '#' || (ch == 'a' && !gateA) || (ch == 'l' && !gateL)) return true;
        }
        return false;
    }

    private int boxAt(float x, float z, float half) {
        for (int i = 0; i < bx.length; i++)
            if (Math.abs(x - bx[i]) < half + BOXH - 0.002f && Math.abs(z - bz[i]) < half + BOXH - 0.002f) return i;
        return -1;
    }

    private boolean boxFree(int i, float nx, float nz) {
        if (solidAt(nx, nz, BOXH)) return false;
        for (int j = 0; j < bx.length; j++) if (j != i &&
                Math.abs(nx - bx[j]) < 2 * BOXH - 0.002f && Math.abs(nz - bz[j]) < 2 * BOXH - 0.002f) return false;
        return true;
    }

    public void moveHero(int i, float dx, float dz) {
        float x = hx[i], z = hz[i];
        if (dx != 0) {
            float nx = x + dx;
            int b = boxAt(nx, z, HALF);
            if (b >= 0 && boxFree(b, bx[b] + dx, bz[b])) bx[b] += dx;
            if (!solidAt(nx, z, HALF) && boxAt(nx, z, HALF) < 0) x = nx;
        }
        if (dz != 0) {
            float nz = z + dz;
            int b = boxAt(x, nz, HALF);
            if (b >= 0 && boxFree(b, bx[b], bz[b] + dz)) bz[b] += dz;
            if (!solidAt(x, nz, HALF) && boxAt(x, nz, HALF) < 0) z = nz;
        }
        hx[i] = x; hz[i] = z;
    }

    private boolean centerIn(float x, float z, int c, int r) { return col(x) == c && row(z) == r; }
    private boolean boxIn(int c, int r) {
        for (int i = 0; i < bx.length; i++) if (col(bx[i]) == c && row(bz[i]) == r) return true;
        return false;
    }

    public void update(float dt) {
        time += dt;
        buttonDown = false;
        boolean onLever = false;
        for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) {
            int idx = r * w + c;
            char ch = g[idx];
            if (ch == 'A') {
                if (centerIn(hx[0], hz[0], c, r) || centerIn(hx[1], hz[1], c, r) || boxIn(c, r)) buttonDown = true;
            } else if (ch == 'L') {
                if (centerIn(hx[0], hz[0], c, r) || centerIn(hx[1], hz[1], c, r)) onLever = true;
            } else if (ch == 'r' && !gemTaken[idx] && centerIn(hx[0], hz[0], c, r)) { gemTaken[idx] = true; gemGot++; }
            else if (ch == 'b' && !gemTaken[idx] && centerIn(hx[1], hz[1], c, r)) { gemTaken[idx] = true; gemGot++; }
        }
        if (onLever && !leverHeld) leverOn = !leverOn;
        leverHeld = onLever;
        gateA = buttonDown;
        gateL = leverOn;
    }

    public int kill() {
        for (int i = 0; i < 2; i++) {
            char ch = at(hx[i], hz[i]);
            if (ch == 'G' || (ch == 'R' && i == 1) || (ch == 'B' && i == 0)) return i;
        }
        return -1;
    }

    public boolean bothExit() { return at(hx[0], hz[0]) == 'E' && at(hx[1], hz[1]) == 'e'; }

    public void render(BoxRenderer br, float anim) {
        br.box(0, -0.06f, 0, w, 0.12f, h, 0.15f, 0.16f, 0.19f);
        for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) {
            int idx = r * w + c;
            float x = cx(c), z = cz(r);
            switch (g[idx]) {
                case '#' -> br.box(x, 0.6f, z, 1, 1.2f, 1, 0.40f, 0.42f, 0.46f);
                case 'R' -> br.box(x, 0.02f, z, 1, 0.05f, 1, 0.72f, 0.13f, 0.10f);
                case 'B' -> br.box(x, 0.02f, z, 1, 0.05f, 1, 0.13f, 0.35f, 0.80f);
                case 'G' -> br.box(x, 0.02f, z, 1, 0.05f, 1, 0.16f, 0.62f, 0.22f);
                case 'A' -> br.box(x, 0.03f, z, 0.8f, 0.06f, 0.8f,
                        buttonDown ? 0.3f : 0.85f, buttonDown ? 0.9f : 0.75f, 0.2f);
                case 'L' -> br.box(x, 0.03f, z, 0.8f, 0.06f, 0.8f,
                        leverOn ? 0.9f : 0.5f, 0.3f, leverOn ? 0.9f : 0.6f);
                case 'a' -> { if (!gateA) br.box(x, 0.6f, z, 1, 1.2f, 1, 0.85f, 0.55f, 0.15f);
                              else br.box(x, 0.02f, z, 1, 0.04f, 1, 0.35f, 0.30f, 0.15f); }
                case 'l' -> { if (!gateL) br.box(x, 0.6f, z, 1, 1.2f, 1, 0.65f, 0.30f, 0.75f);
                              else br.box(x, 0.02f, z, 1, 0.04f, 1, 0.28f, 0.18f, 0.32f); }
                case 'E' -> br.box(x, 0.03f, z, 0.9f, 0.06f, 0.9f, 0.9f, 0.25f, 0.2f);
                case 'e' -> br.box(x, 0.03f, z, 0.9f, 0.06f, 0.9f, 0.25f, 0.5f, 0.95f);
                case 'r' -> { if (!gemTaken[idx]) br.gem(x, 0.35f, z, 0.30f, anim * 2f, 0.95f, 0.25f, 0.2f); }
                case 'b' -> { if (!gemTaken[idx]) br.gem(x, 0.35f, z, 0.30f, anim * 2f, 0.3f, 0.55f, 0.95f); }
                default -> { }
            }
        }
        for (int i = 0; i < bx.length; i++) br.box(bx[i], 0.4f, bz[i], 0.84f, 0.8f, 0.84f, 0.60f, 0.44f, 0.24f);
    }
}
