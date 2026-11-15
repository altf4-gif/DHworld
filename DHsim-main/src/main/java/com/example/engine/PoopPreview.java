package com.example.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.Random;

public final class PoopPreview {
    private PoopPreview() { }

    public static UiTexture render(int size, boolean bright) {
        int fbo = GL30.glGenFramebuffers();
        int tex = GL11.glGenTextures();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, size, size, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        int depthRbo = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRbo);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, size, size);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, tex, 0);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_RENDERBUFFER, depthRbo);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[PoopPreview] framebuffer incomplete: 0x" + Integer.toHexString(status));
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
            GL30.glDeleteRenderbuffers(depthRbo);
            GL11.glDeleteTextures(tex);
            GL30.glDeleteFramebuffers(fbo);
            return null;
        }

        GL11.glViewport(0, 0, size, size);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        Matrix4f view = new Matrix4f().lookAt(0.0f, 1.3f, 1.6f, 0.0f, 0.45f, 0.0f, 0.0f, 1.0f, 0.0f);
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(45f), 1f, 0.1f, 20f);

        // Fix: begin() now requires camPos, fogColor, fogDensity
        Poop.begin(view, proj, new Vector3f(0.0f, 1.3f, 1.6f), new Vector3f(0, 0, 0), 0.0f);
        Poop preview = bright
                ? Poop.brightAt(0f, 0f, 0f, new Random(42))
                : Poop.normalAt(0f, 0f, 0f, new Random(42));
        preview.render();
        Poop.end();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
        GL30.glDeleteRenderbuffers(depthRbo);
        GL30.glDeleteFramebuffers(fbo);
        return new UiTexture(tex, size, size);
    }
}
