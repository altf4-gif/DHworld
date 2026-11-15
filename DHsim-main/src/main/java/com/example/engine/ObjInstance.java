package com.example.engine;

import org.joml.Matrix4f;

/**
 * A generic instance of a single OBJ model placed in the world.
 * Used for trees.
 */
public final class ObjInstance {
    private final ObjModel model;
    private final Matrix4f transform;

    public ObjInstance(ObjModel model, float x, float z, float yaw, float scale) {
        this.model = model;
        this.transform = new Matrix4f().translate(x, 0, z).rotateY(yaw).scale(scale);
    }

    public void render(ShaderProgram shader, Matrix4f view, Matrix4f proj) {
        if (model == null) return;
        shader.setUniform("uModel", transform);
        shader.setUniform("uView", view);
        shader.setUniform("uProjection", proj);
        model.render();
    }
}
