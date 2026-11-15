package com.example.engine;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a multi-line string into a UiTexture using AWT (Java2D).
 */
public final class TextRenderer {

    public static UiTexture createText(String text, int fontSize, int maxWidth, Color color) {
        Font font = new Font("SansSerif", Font.BOLD, fontSize);

        // --- measure & wrap ---
        BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D m = dummy.createGraphics();
        m.setFont(font);
        FontMetrics fm = m.getFontMetrics();

        List<String> lines = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) {
                lines.add("");
                continue;
            }
            String[] words = para.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String test = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(test) > maxWidth && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(test);
                }
            }
            lines.add(line.toString());
        }
        m.dispose();

        int lineHeight = fm.getHeight();
        int textWidth = 0;
        for (String l : lines) textWidth = Math.max(textWidth, fm.stringWidth(l));
        int width = textWidth + 40;
        int height = lines.size() * lineHeight + 40;

        // --- draw ---
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(color);
        int y = fm.getAscent() + 20;
        for (String l : lines) {
            g.drawString(l, 20, y);
            y += lineHeight;
        }
        g.dispose();

        // --- upload (flip Y so v=0 is the BOTTOM row, standard OpenGL) ---
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
        for (int py = height - 1; py >= 0; py--) {
            for (int px = 0; px < width; px++) {
                int p = pixels[py * width + px];
                buf.put((byte) ((p >> 16) & 0xFF));
                buf.put((byte) ((p >> 8) & 0xFF));
                buf.put((byte) (p & 0xFF));
                buf.put((byte) ((p >> 24) & 0xFF));
            }
        }
        buf.flip();

        int texId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        return new UiTexture(texId, width, height);
    }
}
