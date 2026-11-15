package com.example.engine;

import org.joml.Matrix4f;

/**
 * An instance of a composed model (walls + roof + door + windows).
 * Exposes x/y/z so spatial queries (overlap checks) can be done without
 * reaching into the matrix.
 */
public final class HouseInstance {
    public final float x, y, z;
    private final ObjModel walls, roof, door, windows;
    private final Matrix4f model = new Matrix4f();

    public HouseInstance(ObjModel walls, ObjModel roof, ObjModel door, ObjModel windows,
                         float x, float y, float z, float yaw, float scale) {
        this.walls = walls;
        this.roof = roof;
        this.door = door;
        this.windows = windows;
        this.x = x; this.y = y; this.z = z;
        setTransform(x, y, z, yaw, scale);
    }

    public void setTransform(float x, float y, float z, float yaw, float scale) {
        model.identity().translate(x, y, z).rotateY(yaw).scale(scale);
    }

    public void render(ShaderProgram shader, Matrix4f view, Matrix4f proj) {
        shader.setUniform("uModel", model);
        shader.setUniform("uView", view);
        shader.setUniform("uProjection", proj);
        if (walls != null) walls.render();
        if (roof != null) roof.render();
        if (door != null) door.render();
        if (windows != null) windows.render();
    }
}
