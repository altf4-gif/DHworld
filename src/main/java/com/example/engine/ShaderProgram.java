package com.example.engine;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * GLSL program wrapper: compile, link, set uniforms.
 */
public final class ShaderProgram {
    private final int programId;

    public ShaderProgram(String vertexSource, String fragmentSource) {
        int vs = compile(GL20.GL_VERTEX_SHADER, vertexSource);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource);

        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vs);
        GL20.glAttachShader(programId, fs);
        GL20.glLinkProgram(programId);

        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(programId);
            GL20.glDeleteProgram(programId);
            throw new RuntimeException("Shader link error: " + log);
        }

        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
    }

    private static int compile(int type, String source) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(id);
            GL20.glDeleteShader(id);
            throw new RuntimeException("Shader compile error: " + log);
        }
        return id;
    }

    public int getId() {
        return programId;
    }

    public void bind()   { GL20.glUseProgram(programId); }
    public void unbind() { GL20.glUseProgram(0); }

    public void setUniform(String name, Matrix4f value) {
        int loc = GL20.glGetUniformLocation(programId, name);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            value.get(buf);
            GL20.glUniformMatrix4fv(loc, false, buf);
        }
    }

    public void setUniform(String name, Matrix4f[] values) {
        for (int i = 0; i < values.length; i++) {
            int loc = GL20.glGetUniformLocation(programId, name + "[" + i + "]");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buf = stack.mallocFloat(16);
                values[i].get(buf);
                GL20.glUniformMatrix4fv(loc, false, buf);
            }
        }
    }

    public void setUniform1i(String name, int v) {
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, name), v);
    }

    public void setUniform1f(String name, float v) {
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, name), v);
    }

    public void setUniform2f(String name, float x, float y) {
        GL20.glUniform2f(GL20.glGetUniformLocation(programId, name), x, y);
    }

    public void setUniform3f(String name, float x, float y, float z) {
        GL20.glUniform3f(GL20.glGetUniformLocation(programId, name), x, y, z);
    }

    public void setUniform4f(String name, float x, float y, float z, float w) {
        GL20.glUniform4f(GL20.glGetUniformLocation(programId, name), x, y, z, w);
    }

    public void destroy() {
        GL20.glDeleteProgram(programId);
    }
}
