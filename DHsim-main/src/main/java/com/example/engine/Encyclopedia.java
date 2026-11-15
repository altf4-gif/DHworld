package com.example.engine;

import java.awt.Color;
import java.util.List;

/**
 * In-game encyclopedia. Displays a list of characters (Person objects).
 * Navigate with Up / Down arrow keys while open.
 *
 * Open / close with TAB (Q is reserved for spawning).
 */
public final class Encyclopedia {
    private boolean open = false;
    private final List<Person> people;
    private int selectedIndex = 0;

    private final UiTexture titleTexture;
    private final UiTexture hintTexture;

    private UiTexture currentNameTex = null;
    private UiTexture currentDescTex = null;
    private int lastRenderedIndex = -1;

    public Encyclopedia(List<Person> people) {
        this.people = people;
        titleTexture = TextRenderer.createText(
                "ЭНЦИКЛОПЕДИЯ ОБИТАТЕЛЕЙ", 30, 760, new Color(255, 220, 110));
        hintTexture = TextRenderer.createText(
                "Стрелки Вверх/Вниз — листать. TAB — закрыть.", 16, 400, new Color(150, 155, 170));
    }

    public void toggle() {
        open = !open;
    }

    public boolean isOpen() {
        return open;
    }

    public void next() {
        if (people.isEmpty()) return;
        selectedIndex = (selectedIndex + 1) % people.size();
    }

    public void prev() {
        if (people.isEmpty()) return;
        selectedIndex = (selectedIndex - 1 + people.size()) % people.size();
    }

    public void render(UiRenderer ui, int screenWidth, int screenHeight) {
        // Background dim
        ui.drawSolid(screenWidth / 2f, screenHeight / 2f, screenWidth, screenHeight,
                0f, 0f, 0f, 0.7f);

        // Panel
        float panelW = 860;
        float panelH = 640;
        ui.drawSolid(screenWidth / 2f, screenHeight / 2f, panelW, panelH,
                0.15f, 0.18f, 0.24f, 1f);
        ui.drawSolid(screenWidth / 2f, screenHeight / 2f, panelW - 3, panelH - 3,
                0.07f, 0.08f, 0.11f, 1f);

        // Title
        float titleH = 44;
        float titleW = titleH * ((float) titleTexture.width / titleTexture.height);
        float titleY = screenHeight / 2f - panelH / 2f + 50;
        ui.drawTextured(screenWidth / 2f, titleY, titleW, titleH, titleTexture.id);

        if (!people.isEmpty()) {
            Person p = people.get(selectedIndex);

            // --- Left side: Image ---
            float maxH = 400;
            float maxW = 350;
            float aspect = p.texture.aspect();
            float imgH = maxH;
            float imgW = imgH * aspect;
            if (imgW > maxW) {
                imgW = maxW;
                imgH = imgW / aspect;
            }
            float imgX = screenWidth / 2f - panelW / 4f;
            float imgY = screenHeight / 2f + 30;
            ui.drawTextured(imgX, imgY, imgW, imgH, p.texture.getId());

            // --- Right side: Text ---
            if (lastRenderedIndex != selectedIndex) {
                if (currentNameTex != null) currentNameTex.destroy();
                if (currentDescTex != null) currentDescTex.destroy();

                currentNameTex = TextRenderer.createText(p.name, 24, 380, new Color(255, 220, 110));
                currentDescTex = TextRenderer.createText(p.description, 17, 380, new Color(215, 218, 225));
                lastRenderedIndex = selectedIndex;
            }

            float nameW = 380;
            float nameH = nameW * ((float) currentNameTex.height / currentNameTex.width);
            float nameX = screenWidth / 2f + panelW / 4f - 30;
            float nameY = screenHeight / 2f - 160;
            ui.drawTextured(nameX, nameY, nameW, nameH, currentNameTex.id);

            float descW = 380;
            float descH = descW * ((float) currentDescTex.height / currentDescTex.width);
            float descX = screenWidth / 2f + panelW / 4f - 30;
            float descY = screenHeight / 2f + 20;
            ui.drawTextured(descX, descY, descW, descH, currentDescTex.id);
        }

        // Hint
        float hintH = 22;
        float hintW = hintH * ((float) hintTexture.width / hintTexture.height);
        float hintY = screenHeight / 2f + panelH / 2f - 35;
        ui.drawTextured(screenWidth / 2f, hintY, hintW, hintH, hintTexture.id);
    }

    public void destroy() {
        if (titleTexture != null) titleTexture.destroy();
        if (hintTexture != null) hintTexture.destroy();
        if (currentNameTex != null) currentNameTex.destroy();
        if (currentDescTex != null) currentDescTex.destroy();
    }
}
