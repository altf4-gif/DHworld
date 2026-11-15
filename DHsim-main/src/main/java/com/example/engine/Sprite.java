package com.example.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Textured billboard facing the camera in XZ, Y aligned to world Up.
 * The base Y is the feet position — no more "bob" moving the character up/down
 * visually through the ground. Bobbing is now done via slight scale pulsation.
 * Shadow is a flat "blob" quad projected onto the ground.
 */
public final class Sprite {
    private static int sharedVao = -1;
    private static int shadowVao = -1;
    private static int shadowVbo = -1;

    private final Texture texture;
    private final Vector3f position;
    private final float width;
    private final float height;
    private final float bobPhase;

    private float scale = 1f;

    public Sprite(Texture texture, Vector3f position, float targetHeight) {
        this.texture = texture;
        this.position = new Vector3f(position);
        this.height = targetHeight;
        this.width = targetHeight * texture.contentAspect();
        this.bobPhase = ThreadLocalRandom.current().nextFloat() * (float) (Math.PI * 2);
        if (sharedVao == -1) createSharedQuad();
        if (shadowVao == -1) createShadowQuad();
    }

    private static void createSharedQuad() {
        float[] vertices = {
                -0.5f, -0.5f, 0f, 0f,
                 0.5f, -0.5f, 1f, 0f,
                 0.5f,  0.5f, 1f, 1f,
                 0.5f,  0.5f, 1f, 1f,
                -0.5f,  0.5f, 0f, 1f,
                -0.5f, -0.5f, 0f, 0f,
        };

        sharedVao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(sharedVao);
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(vertices.length);
        try { buf.put(vertices).flip(); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW); }
        finally { MemoryUtil.memFree(buf); }

        final int stride = 4 * Float.BYTES;
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    private static void createShadowQuad() {
        // A simple flat quad for blob shadows (XZ plane)
        float[] vertices = {
                -0.5f, 0f, -0.5f,
                 0.5f, 0f, -0.5f,
                 0.5f, 0f,  0.5f,
                 0.5f, 0f,  0.5f,
                -0.5f, 0f,  0.5f,
                -0.5f, 0f, -0.5f,
        };
        shadowVao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(shadowVao);
        shadowVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, shadowVbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(vertices.length);
        try { buf.put(vertices).flip(); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW); }
        finally { MemoryUtil.memFree(buf); }
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    public void setPosition(float x, float y, float z) { position.set(x, y, z); }
    public Vector3f position() { return position; }
    public void setScale(float s) { this.scale = s; }

    public void render(ShaderProgram shader, Matrix4f view, Matrix4f projection,
                       Vector3f cameraPos, float time) {
        // Visual bob is a scale pulse, not Y-translation
        float pulse = 1f + 0.03f * (float) Math.sin(time * 3f + bobPhase);
        Vector3f base = new Vector3f(position.x, position.y, position.z);

        float dx = cameraPos.x - base.x;
        float dz = cameraPos.z - base.z;

        Vector3f forward = new Vector3f(dx, 0f, dz);
        if (forward.lengthSquared() < 1e-6f) forward.set(0f, 0f, 1f);
        else forward.normalize();

        Vector3f up    = new Vector3f(0f, 1f, 0f);
        Vector3f right = new Vector3f(up).cross(forward).normalize();

        float w = width * scale * pulse;
        float h = height * scale * pulse;

        Matrix4f model = new Matrix4f().identity();
        model.setColumn(0, new Vector4f(right.x * w, right.y * w, right.z * w, 0f));
        model.setColumn(1, new Vector4f(up.x    * h, up.y    * h, up.z    * h, 0f));
        model.setColumn(2, new Vector4f(forward.x,   forward.y,   forward.z,   0f));
        model.setColumn(3, new Vector4f(base.x,      base.y,      base.z,      1f));

        shader.setUniform("uModel", model);
        shader.setUniform("uView", view);
        shader.setUniform("uProjection", projection);
        shader.setUniform2f("uUvOffset", texture.uMin, texture.vMin);
        shader.setUniform2f("uUvScale", texture.uMax - texture.uMin, texture.vMax - texture.vMin);

        texture.bind();
        GL30.glBindVertexArray(sharedVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    /**
     * Renders a flat "blob" shadow on the ground at the sprite's base.
     * Fades out when the sun is overhead or below the horizon.
     */
    public void renderShadow(ShaderProgram shadowShader, Matrix4f lightVP,
                             Vector3f sunDir, float time) {
        if (sunDir.y <= 0.05f) return;

        // Shadow stretches based on sun angle
        float stretch = 1f / Math.max(0.2f, sunDir.y);
        float w = width * scale * 1.2f;
        float h = height * scale * 0.5f * stretch;

        // Offset shadow away from the sun horizontally
        float dx = -sunDir.x * (height * 0.5f / Math.max(0.2f, sunDir.y));
        float dz = -sunDir.z * (height * 0.5f / Math.max(0.2f, sunDir.y));

        float y = Ground.getHeightAt(position.x + dx, position.z + dz) + 0.05f;

        Matrix4f model = new Matrix4f().translate(position.x + dx, y, position.z + dz)
                .scale(w, 1f, h);

        shadowShader.setUniform("uModel", model);
        shadowShader.setUniform("uLightVP", lightVP); // We just use it to draw

        GL30.glBindVertexArray(shadowVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
    }
}
