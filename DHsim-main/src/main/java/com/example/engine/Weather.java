package com.example.engine;

import java.util.Random;

/**
 * Manages the weather state (Clear, Cloudy, Overcast).
 * Transitions are smooth over ~15 seconds. Changes randomly every 1-3 minutes.
 */
public final class Weather {

    public enum State { CLEAR, CLOUDY, OVERCAST }

    private State current = State.CLEAR;
    private State target = State.CLEAR;

    private float currentCloudiness = 0.0f;
    private float targetCloudiness = 0.0f;

    private float timer = 60f;
    private final Random rng = new Random();

    public void update(float dt) {
        timer -= dt;
        if (timer <= 0f) {
            rollWeather();
        }

        // Smooth transition
        currentCloudiness += (targetCloudiness - currentCloudiness) * Math.min(1f, dt * 0.5f);
    }

    private void rollWeather() {
        timer = 60f + rng.nextFloat() * 120f; // 1-3 minutes
        float r = rng.nextFloat();

        if (r < 0.4f) {
            target = State.CLEAR;
            targetCloudiness = 0.0f;
        } else if (r < 0.8f) {
            target = State.CLOUDY;
            targetCloudiness = 0.5f;
        } else {
            target = State.OVERCAST;
            targetCloudiness = 1.0f;
        }
        
        if (target != current) {
            System.out.println("[WEATHER] Changing to " + target);
            current = target;
        }
    }

    public float cloudiness() {
        return currentCloudiness;
    }
    
    public State state() {
        return current;
    }

    /** Returns a grey tint that overrides the ambient light when it's cloudy. */
    public float ambientDampening() {
        return 1.0f - currentCloudiness * 0.4f;
    }

    /** Sun intensity modifier. Overcast heavily dampens direct sunlight. */
    public float sunIntensityModifier() {
        return 1.0f - currentCloudiness * 0.85f;
    }
}
