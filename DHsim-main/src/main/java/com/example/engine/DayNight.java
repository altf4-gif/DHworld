package com.example.engine;

import org.joml.Vector3f;

/**
 * Drives the day/night cycle: sun direction, light colours, sky gradient,
 * ambient light and fog. One full cycle takes 5 real minutes at speed 1x.
 */
public final class DayNight {

    private float timeOfDay = 0.30f;   // 0 = midnight, 0.25 = 6am, 0.5 = noon, 0.75 = 6pm
    private float speed = 1f;
    private static final float FULL_CYCLE_SECONDS = 300f;  // 5 min real time

    public void update(float dt) {
        timeOfDay += (dt * speed) / FULL_CYCLE_SECONDS;
        if (timeOfDay >= 1f) timeOfDay -= 1f;
        if (timeOfDay < 0f)  timeOfDay += 1f;
    }

    public void toggleSpeed() {
        speed = (speed > 5f) ? 1f : 10f;
    }

    public float timeOfDay() { return timeOfDay; }

    /** Direction from the world toward the sun (unit vector). */
    public Vector3f sunDir() {
        float angle = (timeOfDay - 0.25f) * (float)(Math.PI * 2f);
        float x = (float)Math.cos(angle);
        float y = (float)Math.sin(angle);
        float z = (float)Math.cos(angle) * 0.5f;
        return new Vector3f(x, y, z).normalize();
    }

    public float sunElevation() { return sunDir().y; }

    public float sunIntensity() { return Math.max(0f, sunElevation()); }

    public Vector3f sunColor() {
        float y = sunElevation();
        if (y < -0.1f) return new Vector3f(0f, 0f, 0f);

        if (y < 0.25f) {
            float t = (y + 0.1f) / 0.35f;
            t = Math.max(0f, Math.min(1f, t));
            return new Vector3f(
                    1.0f,
                    0.40f + 0.55f * t,
                    0.15f + 0.70f * t);
        }
        return new Vector3f(1.0f, 0.95f, 0.85f);
    }

    public Vector3f ambient() {
        float y = sunElevation();
        float base = 0.15f + 0.35f * Math.max(0f, y + 0.15f);
        if (y < 0f) {
            return new Vector3f(base * 0.45f, base * 0.6f, base * 1.3f);
        }
        return new Vector3f(base * 1.1f, base * 1.0f, base * 0.85f);
    }

    /** Combined ambient + sun for objects that don't do lambert (sprites/poop). */
    public Vector3f billboardTint() {
        Vector3f a = ambient();
        Vector3f s = sunColor();
        return new Vector3f(
                a.x + s.x * 0.4f,
                a.y + s.y * 0.4f,
                a.z + s.z * 0.4f);
    }

    public Vector3f skyTop() {
        float y = sunElevation();
        if (y > 0.3f) {
            return new Vector3f(0.32f, 0.60f, 0.95f);
        }
        if (y > 0f) {
            float t = y / 0.3f;
            return new Vector3f(
                    0.10f + 0.22f * t,
                    0.06f + 0.54f * t,
                    0.20f + 0.75f * t);
        }
        float t = Math.max(0f, Math.min(1f, -y / 0.3f));
        return new Vector3f(
                0.10f * (1f - t) + 0.02f * t,
                0.06f * (1f - t) + 0.03f * t,
                0.20f * (1f - t) + 0.08f * t);
    }

    public Vector3f skyBottom() {
        float y = sunElevation();
        if (Math.abs(y) < 0.20f) {
            float g = 1f - Math.abs(y) / 0.20f;
            return new Vector3f(
                    0.20f + 0.60f * g,
                    0.15f + 0.35f * g,
                    0.15f + 0.10f * g);
        }
        if (y > 0.3f) return new Vector3f(0.72f, 0.86f, 0.97f);
        return new Vector3f(0.04f, 0.05f, 0.10f);
    }

    public Vector3f fogColor() { return skyBottom(); }

    public float fogDensity() { return 0.008f; }
}
