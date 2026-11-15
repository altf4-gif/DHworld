package com.example.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * A fully animated single-piece model that wanders around.
 * Since there is no skeleton, we animate the whole model:
 * - Bobbing up and down (simulating steps)
 * - Waddling left and right (tilt)
 * - Turning toward its destination
 */
public final class WanderingModel {
    private final ObjModel model;
    private final Vector3f pos;
    private final float scale;
    private final float baseY;
    
    private final Random rng;
    private final Vector3f target = new Vector3f();
    private float currentYaw = 0f;
    private final float walkSpeed = 1.5f;

    private float bobTimer = 0f;

    public WanderingModel(ObjModel model, float x, float z, float scale) {
        this.model = model;
        this.pos = new Vector3f(x, Ground.getHeightAt(x, z), z);
        this.baseY = pos.y;
        this.scale = scale;
        this.rng = new Random();
        pickNewTarget();
    }

    private void pickNewTarget() {
        float a = rng.nextFloat() * (float) (Math.PI * 2);
        float r = 5f + rng.nextFloat() * 10f;
        target.set(pos.x + (float) Math.cos(a) * r, 0, pos.z + (float) Math.sin(a) * r);
    }

    public void update(float dt) {
        // 1. Movement towards target
        float dx = target.x - pos.x;
        float dz = target.z - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.5f) {
            pickNewTarget();
        } else {
            float step = Math.min(walkSpeed * dt, dist);
            pos.x += (dx / dist) * step;
            pos.z += (dz / dist) * step;
            
            // Rotate towards target smoothly
            float targetYaw = (float) Math.atan2(dz, dx) - (float) (Math.PI / 2);
            float yawDiff = targetYaw - currentYaw;
            while (yawDiff > Math.PI) yawDiff -= Math.PI * 2;
            while (yawDiff < -Math.PI) yawDiff += Math.PI * 2;
            currentYaw += yawDiff * Math.min(1f, dt * 5f);

            bobTimer += dt * 8f; // speed of bobbing
        }

        pos.y = Ground.getHeightAt(pos.x, pos.z);
    }

    public void render(ShaderProgram shader, Matrix4f view, Matrix4f proj) {
        // 2. Calculate animation transforms
        // Bobbing (up and down)
        float bob = (float) Math.abs(Math.sin(bobTimer)) * 0.15f;
        // Waddle (tilt left/right)
        float waddle = (float) Math.sin(bobTimer) * 0.1f;

        Matrix4f modelMat = new Matrix4f()
                .translate(pos.x, pos.y + bob, pos.z)
                .rotateY(currentYaw)
                .rotateZ(waddle)
                .scale(scale);

        shader.setUniform("uModel", modelMat);
        shader.setUniform("uView", view);
        shader.setUniform("uProjection", proj);
        
        if (model != null) model.render();
    }

    public void destroy() {
        if (model != null) model.destroy();
    }
}
