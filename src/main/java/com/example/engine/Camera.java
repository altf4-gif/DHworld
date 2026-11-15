package com.example.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

/**
 * Free-fly camera. Now starts above ground (Ground.getHeightAt + eye height).
 */
public final class Camera {
    private final Vector3f position = new Vector3f(0.7f, 3.2f, 6.5f);
    private float yaw = -90f;
    private float pitch = 0f;

    private final Matrix4f view = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private float lastAspect = 0f;

    private final float moveSpeed = 4.0f;
    private final float mouseSensitivity = 0.12f;
    private final float fov = 60f;
    private final float eyeHeight = 1.7f;

    private float lastX;
    private float lastY;
    private boolean firstMouse = true;

    public Camera(float aspectRatio) {
        snapToGround();
        setAspect(aspectRatio);
        updateView();
    }

    /** Snaps camera to `Ground.getHeightAt + eyeHeight`. Call after terrain pads are created. */
    public void snapToGround() {
        position.y = Ground.getHeightAt(position.x, position.z) + eyeHeight;
        updateView();
    }

    public void setAspect(float aspect) {
        if (aspect <= 0.0001f) aspect = 1f;
        if (Math.abs(aspect - lastAspect) < 0.0001f) return;
        lastAspect = aspect;
        projection.identity().perspective((float) Math.toRadians(fov), aspect, 0.1f, 1000f);
    }

    public void update(Window window, float dt) {
        processKeyboard(window, dt);
        processMouse(window);
        updateView();
    }

    private void processKeyboard(Window window, float dt) {
        Vector3f front = frontVector();
        Vector3f right = rightVector();
        Vector3f move = new Vector3f();

        if (window.isKeyPressed(GLFW.GLFW_KEY_W)) move.add(front);
        if (window.isKeyPressed(GLFW.GLFW_KEY_S)) move.sub(front);
        if (window.isKeyPressed(GLFW.GLFW_KEY_D)) move.add(right);
        if (window.isKeyPressed(GLFW.GLFW_KEY_A)) move.sub(right);
        if (window.isKeyPressed(GLFW.GLFW_KEY_SPACE)) move.y += 1f;
        if (window.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) move.y -= 1f;

        if (move.lengthSquared() > 0.0001f) {
            move.normalize().mul(moveSpeed * dt);
            position.add(move);
        }
    }

    private void processMouse(Window window) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer xBuf = stack.mallocDouble(1);
            DoubleBuffer yBuf = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window.handle(), xBuf, yBuf);
            float x = (float) xBuf.get(0);
            float y = (float) yBuf.get(0);

            if (firstMouse) {
                lastX = x; lastY = y;
                firstMouse = false;
                return;
            }

            float dx = x - lastX;
            float dy = y - lastY;
            lastX = x;
            lastY = y;

            yaw += dx * mouseSensitivity;
            pitch -= dy * mouseSensitivity;
            if (pitch > 89f) pitch = 89f;
            if (pitch < -89f) pitch = -89f;
        }
    }

    public void resetMouseLook() {
        firstMouse = true;
    }

    public Vector3f frontVector() {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return new Vector3f(
                (float) (Math.cos(yawRad) * Math.cos(pitchRad)),
                (float) Math.sin(pitchRad),
                (float) (Math.sin(yawRad) * Math.cos(pitchRad))
        ).normalize();
    }

    public Vector3f rightVector() {
        return new Vector3f(frontVector()).cross(new Vector3f(0f, 1f, 0f)).normalize();
    }

    public Vector3f upVector() {
        return new Vector3f(rightVector()).cross(frontVector()).normalize();
    }

    private void updateView() {
        Vector3f front = frontVector();
        Vector3f target = new Vector3f(position).add(front);
        view.identity().lookAt(position, target, new Vector3f(0f, 1f, 0f));
    }

    public Matrix4f view() { return view; }
    public Matrix4f projection() { return projection; }
    public Vector3f position() { return position; }
    public float fov() { return fov; }
}
