package com.example.engine;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * A faction/pack of villagers. They share a wood stockpile, a central base,
 * and a unique color identity.
 */
public final class Tribe {
    public final int id;
    public final Vector3f center;
    public final Vector3f color; // RGB 0..1
    public int wood = 15; // start with a little wood so they can build immediately
    public final List<HouseInstance> houses = new ArrayList<>();

    public Tribe(int id, Vector3f center, Vector3f color) {
        this.id = id;
        this.center = center;
        this.color = color;
    }

    public boolean canBuild() {
        return wood >= 10;
    }

    public boolean needsHouse(int population) {
        // 1 house per 2 villagers
        return houses.size() < (population + 1) / 2;
    }
}
