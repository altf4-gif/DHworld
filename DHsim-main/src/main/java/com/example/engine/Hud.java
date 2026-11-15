package com.example.engine;

import java.awt.Color;

/**
 * Lightweight HUD: time/weather, population, mute state, and hint bar.
 * Textures are only regenerated when their value actually changes.
 */
public final class Hud {
    private UiTexture popTex, timeTex, muteTex, hintTex;
    private int lastPop = -1;
    private int lastMinute = -1;
    private String lastWeather = "";
    private boolean lastMuted = false;

    public Hud() {
        hintTex = TextRenderer.createText(
                "M — звук   |   T — время   |   Q — спавн   |   TAB — энциклопедия",
                14, 900, new Color(220, 220, 220));
    }

    public void render(UiRenderer ui, int sw, int sh, int pop, float timeOfDay,
                       String weather, boolean muted) {
        int hour = (int) (timeOfDay * 24);
        int minute = (int) ((timeOfDay * 24 - hour) * 60);

        if (minute != lastMinute || !weather.equals(lastWeather)) {
            if (timeTex != null) timeTex.destroy();
            timeTex = TextRenderer.createText(
                    String.format("%02d:%02d  |  %s", hour, minute, weather),
                    16, 400, new Color(240, 240, 240));
            lastMinute = minute;
            lastWeather = weather;
        }
        if (pop != lastPop) {
            if (popTex != null) popTex.destroy();
            popTex = TextRenderer.createText("Население: " + pop, 16, 400, new Color(255, 220, 110));
            lastPop = pop;
        }
        if (muted != lastMuted) {
            if (muteTex != null) { muteTex.destroy(); muteTex = null; }
            if (muted) {
                muteTex = TextRenderer.createText("ЗВУК ВЫКЛ", 16, 200, new Color(255, 100, 100));
            }
            lastMuted = muted;
        }

        float x = 20, y = 20;
        if (timeTex != null) {
            float tH = 20, tW = tH * ((float) timeTex.width / timeTex.height);
            ui.drawTextured(x + tW / 2, y + tH / 2, tW, tH, timeTex.id);
            y += 26;
        }
        if (popTex != null) {
            float tH = 20, tW = tH * ((float) popTex.width / popTex.height);
            ui.drawTextured(x + tW / 2, y + tH / 2, tW, tH, popTex.id);
            y += 26;
        }
        if (muteTex != null) {
            float tH = 20, tW = tH * ((float) muteTex.width / muteTex.height);
            ui.drawTextured(x + tW / 2, y + tH / 2, tW, tH, muteTex.id);
        }

        if (hintTex != null) {
            float tH = 16, tW = tH * ((float) hintTex.width / hintTex.height);
            ui.drawTextured(sw / 2f, sh - 20, tW, tH, hintTex.id);
        }
    }

    public void destroy() {
        if (popTex != null) popTex.destroy();
        if (timeTex != null) timeTex.destroy();
        if (muteTex != null) muteTex.destroy();
        if (hintTex != null) hintTex.destroy();
    }
}
